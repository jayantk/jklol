package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.cfg.CfgParser;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.AbstractEmOracle;
import com.jayantkrish.jklol.training.ExpectationMaximization;
import com.jayantkrish.jklol.training.LogFunction;

public class LagrangianAlignmentTrainer {
  
  private final int numIterations;
  // TODO: This oracle needs to run hard EM.
  private final ExpectationMaximization em;

  public LagrangianAlignmentTrainer(int numIterations, ExpectationMaximization em) {
    this.numIterations = numIterations;
    this.em = em;
  }

  public ParametersAndLagrangeMultipliers train(ParametricCfgAlignmentModel pam,
      SufficientStatistics initialParameters, SufficientStatistics smoothing, List<AlignmentExample> trainingData) {
    double terminalProbability = 0.01;

    DiscreteVariable terminalVar = (DiscreteVariable) pam.getNonterminalVar().getOnlyVariable();
    // A number of examples by number of nonterminals matrix holding the
    // lagrange multiplier for downweighting terminal symbol probabilities
    // in each training example.
    int[] lagrangeDims = new int[] {0, 1};
    int[] lagrangeSizes = new int[] {trainingData.size(), terminalVar.numValues()};
    DenseTensorBuilder lagrangeMultipliers = new DenseTensorBuilder(lagrangeDims, lagrangeSizes);
    SufficientStatistics parameters = initialParameters;

    // Variables for easy logging.
    DiscreteVariable exampleVar = DiscreteVariable.sequence("examples", trainingData.size());
    VariableNumMap exampleVarNumMap = VariableNumMap.singleton(0, "examples", exampleVar);
    VariableNumMap terminalVarNumMap = VariableNumMap.singleton(1, "terminals", terminalVar);

    Tensor oldTerminalIndicator = SparseTensor.empty(new int[] {1}, new int[] {terminalVar.numValues()});
    for (int i = 0; i < numIterations; i++) {
      double stepSize = 1 / Math.sqrt(i + 1);
      parameters = runEm(pam, smoothing, parameters, trainingData,
          lagrangeMultipliers.build(), 2);

      // Maximize the prior distribution over terminal symbols.
      Tensor terminalLagrangeSum = lagrangeMultipliers.build().sumOutDimensions(0);
      terminalLagrangeSum = terminalLagrangeSum.elementwiseAddition(Math.log(terminalProbability));
      Tensor terminalIndicator = terminalLagrangeSum.findKeysLargerThan(0);

      /*
      TableFactor terminalIndicatorFactor = new TableFactor(terminalVarNumMap, lagrangeMultipliers.build().sumOutDimensions(0));
      System.out.println(terminalIndicatorFactor.getParameterDescription());
      */
      // Log the delta in lexicon entries
      System.out.println("Current lexicon entries:");
      TableFactor indicatorFactor = new TableFactor(terminalVarNumMap, terminalIndicator);
      System.out.println(indicatorFactor.getParameterDescription());

      System.out.println("Added lexicon entries:");
      Tensor addedIndicator = terminalIndicator.elementwiseAddition(oldTerminalIndicator.elementwiseProduct(-1.0))
          .findKeysLargerThan(0.0);
      TableFactor addedIndicatorFactor = new TableFactor(terminalVarNumMap, addedIndicator);
      System.out.println(addedIndicatorFactor.getParameterDescription());

      System.out.println("Removed lexicon entries:");
      Tensor removedIndicator = oldTerminalIndicator.elementwiseAddition(terminalIndicator.elementwiseProduct(-1.0))
          .findKeysLargerThan(0.0);
      TableFactor removedIndicatorFactor = new TableFactor(terminalVarNumMap, removedIndicator);
      System.out.println(removedIndicatorFactor.getParameterDescription());
      oldTerminalIndicator = terminalIndicator;

      // Decode best trees to find mu variables
      CfgAlignmentModel model = pam.getModelFromParameters(parameters);
      Set<Object> usedTerminals = Sets.newHashSet();
      DenseTensorBuilder muVars = new DenseTensorBuilder(new int[] {1}, new int[] {terminalVar.numValues()});
      Tensor lagrangeMultipliersTensor = lagrangeMultipliers.build();
      for (int j = 0; j < trainingData.size(); j++) {
        AlignmentExample example = trainingData.get(j);
        Tensor exampleMultipliers = lagrangeMultipliersTensor.slice(new int[] {0}, new int[] {j});
        TableFactor exampleWeights = new TableFactor(model.getParentVar(),
            exampleMultipliers.elementwiseProduct(-1.0).elementwiseExp());

        CfgParser parser = model.getCfgParser(example, exampleWeights);
        Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
        CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, false);

        CfgParseTree tree = chart.getBestParseTree();
        usedTerminals.clear();
        getTerminals(tree, usedTerminals);

        // Mu variables are 1 for every terminal symbol in the parse tree. 
        muVars.multiply(0.0);
        for (Object usedTerminal : usedTerminals) {
          int index = terminalVar.getValueIndex(usedTerminal);
          muVars.put(new int[] {index}, 1.0);
        }
        
        // Gradient of the lagrange multipliers for this particular example.
        Tensor exampleLagrangeGradient = terminalIndicator.elementwiseAddition(
            muVars.build().elementwiseProduct(-1.0));
        Tensor exampleIndicator = SparseTensor.singleElement(new int[] {0},
            new int[] {trainingData.size()}, new int[] {j}, 1.0);
        
        // Update the lagrange multipliers for this example.
        lagrangeMultipliers.incrementOuterProductWithMultiplier(exampleIndicator,
            exampleLagrangeGradient, -1.0 * stepSize);
      }

      lagrangeMultipliers.maximum(SparseTensor.empty(lagrangeDims, lagrangeSizes));
      
      Factor lagrangeFactor = new TableFactor(exampleVarNumMap.union(terminalVarNumMap), lagrangeMultipliers.build());
      // System.out.println(lagrangeFactor.getParameterDescription());
    }

    return new ParametersAndLagrangeMultipliers(parameters, lagrangeMultipliers.build());
  }
  
  private void getTerminals(CfgParseTree tree, Set<Object> accumulator) {
    if (tree.isTerminal()) {
      accumulator.add(tree.getRoot());
    } else {
      getTerminals(tree.getLeft(), accumulator);
      getTerminals(tree.getRight(), accumulator);
    }
  }

  private SufficientStatistics runEm(ParametricCfgAlignmentModel pam, SufficientStatistics smoothing,
      SufficientStatistics currentParameters, List<AlignmentExample> trainingData,
      DenseTensor lagrangeMultipliers, int numEmIterations) {

    LagrangeEmOracle oracle = new LagrangeEmOracle(pam, smoothing, trainingData,
        lagrangeMultipliers);
    
    List<Integer> exampleIndexes = Lists.newArrayList();
    for (int i = 0; i < trainingData.size(); i++) {
      exampleIndexes.add(i);
    }

    return em.train(oracle, currentParameters, exampleIndexes);
  }
  
  private static class LagrangeEmOracle extends AbstractEmOracle<CfgAlignmentModel, Integer> {
    
    private final ParametricCfgAlignmentModel pam;
    private final SufficientStatistics smoothing;
    private final List<AlignmentExample> trainingData;
    private final Tensor lagrangeMultipliers;
    
    public LagrangeEmOracle(ParametricCfgAlignmentModel pam, SufficientStatistics smoothing,
        List<AlignmentExample> trainingData, Tensor lagrangeMultipliers) {
      this.pam = Preconditions.checkNotNull(pam);
      this.smoothing = Preconditions.checkNotNull(smoothing);

      Preconditions.checkArgument(trainingData.size() == lagrangeMultipliers.getDimensionSizes()[0]);
      this.trainingData = trainingData;
      this.lagrangeMultipliers = lagrangeMultipliers;
    }

    @Override
    public CfgAlignmentModel instantiateModel(SufficientStatistics parameters) {
      return pam.getModelFromParameters(parameters);
    }

    @Override
    public SufficientStatistics getInitialExpectationAccumulator() {
      return pam.getNewSufficientStatistics();
    }

    @Override
    public SufficientStatistics computeExpectations(CfgAlignmentModel model,
        SufficientStatistics currentParameters, Integer index, SufficientStatistics accumulator,
        LogFunction log) {
      AlignmentExample example = trainingData.get(index);
      Tensor exampleMultipliers = lagrangeMultipliers.slice(new int[] {0}, new int[] {index});
      TableFactor exampleWeights = new TableFactor(model.getParentVar(),
          exampleMultipliers.elementwiseProduct(-1.0).elementwiseExp());

      log.startTimer("e_step/getCfg");
      CfgParser parser = model.getCfgParser(example, exampleWeights);
      log.stopTimer("e_step/getCfg");

      log.startTimer("e_step/marginals");
      Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
      CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, true);
      log.stopTimer("e_step/marginals");

      log.startTimer("e_step/compute_expectations");
      pam.incrementSufficientStatistics(accumulator, currentParameters, chart, 1.0);
      log.stopTimer("e_step/compute_expectations");

      return accumulator;
    }

    @Override
    public SufficientStatistics maximizeParameters(SufficientStatistics expectations,
        SufficientStatistics currentParameters, LogFunction log) {
      SufficientStatistics aggregate = pam.getNewSufficientStatistics();
      aggregate.increment(smoothing, 1.0);
      aggregate.increment(expectations, 1.0);

      return aggregate;
    }
  }
  
  public static class ParametersAndLagrangeMultipliers {
    private final SufficientStatistics parameters;
    private final Tensor lagrangeMultipliers;
    
    public ParametersAndLagrangeMultipliers(SufficientStatistics parameters,
        Tensor lagrangeMultipliers) {
      this.parameters = Preconditions.checkNotNull(parameters);
      this.lagrangeMultipliers = Preconditions.checkNotNull(lagrangeMultipliers);
    }

    public SufficientStatistics getParameters() {
      return parameters;
    }

    public Tensor getLagrangeMultipliers() {
      return lagrangeMultipliers;
    }
  }
}
