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
import com.jayantkrish.jklol.models.TableFactorBuilder;
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
    DiscreteVariable terminalVar = (DiscreteVariable) pam.getNonterminalVar().getOnlyVariable();
    DiscreteVariable wordVar = (DiscreteVariable) pam.getTerminalVar().getOnlyVariable();
    
    // The penalties for including each lexicon entry in the lexicon. 
    double terminalProbability = 0.01;
    Tensor lexiconPenalties = DenseTensor.random(new int[] {2},
        new int[] {terminalVar.numValues()}, Math.log(terminalProbability), 1.0);
    Tensor lexiconWordPenalties = DenseTensor.random(new int[] {1, 2},
        new int[] {wordVar.numValues(), terminalVar.numValues()}, Math.log(terminalProbability), 1.0);
    
    // A number of examples by number of nonterminals matrix holding the
    // lagrange multiplier for downweighting terminal symbol probabilities
    // in each training example.
    int[] lagrangeDims = new int[] {0, 2};
    int[] lagrangeSizes = new int[] {trainingData.size(), terminalVar.numValues()};
    DenseTensorBuilder lagrangeMultipliers = new DenseTensorBuilder(lagrangeDims, lagrangeSizes);
    
    int[] lagrangeWordDims = new int[] {0, 1, 2};
    int[] lagrangeWordSizes = new int[] {trainingData.size(), wordVar.numValues(), terminalVar.numValues()};
    DenseTensorBuilder lagrangeWordMultipliers = new DenseTensorBuilder(lagrangeWordDims, lagrangeWordSizes);

    SufficientStatistics parameters = initialParameters;

    // Variables for easy logging.
    DiscreteVariable exampleVar = DiscreteVariable.sequence("examples", trainingData.size());
    VariableNumMap exampleVarNumMap = VariableNumMap.singleton(0, "examples", exampleVar);
    VariableNumMap wordVarNumMap = VariableNumMap.singleton(1, "words", wordVar);
    VariableNumMap terminalVarNumMap = VariableNumMap.singleton(2, "terminals", terminalVar);
    VariableNumMap wordAndTerminalVarNumMap = wordVarNumMap.union(terminalVarNumMap);

    Tensor oldTerminalIndicator = SparseTensor.empty(new int[] {2}, new int[] {terminalVar.numValues()});
    Tensor oldTerminalWordIndicator = SparseTensor.empty(new int[] {1, 2}, new int[] {wordVar.numValues(), terminalVar.numValues()});
    for (int i = 0; i < numIterations; i++) {
      double stepSize = 1 / Math.sqrt(i + 1);

      if ((i + 1) % 20 == 0) {
        parameters = runEm(pam, smoothing, parameters, trainingData,
            lagrangeMultipliers.build(), 2);
      }

      // Maximize the prior distribution over terminal symbols.
      Tensor terminalLagrangeSum = lagrangeMultipliers.build().sumOutDimensions(0);
      terminalLagrangeSum = terminalLagrangeSum.elementwiseAddition(lexiconPenalties);
      Tensor terminalIndicator = terminalLagrangeSum.findKeysLargerThan(0);
      
      Tensor terminalWordLagrangeSum = lagrangeWordMultipliers.build().sumOutDimensions(0);
      terminalWordLagrangeSum = terminalWordLagrangeSum.elementwiseAddition(lexiconWordPenalties);
      Tensor terminalWordIndicator = terminalWordLagrangeSum.findKeysLargerThan(0);

      /*
      TableFactor terminalIndicatorFactor = new TableFactor(terminalVarNumMap, lagrangeMultipliers.build().sumOutDimensions(0));
      System.out.println(terminalIndicatorFactor.getParameterDescription());
      */
      // Log the delta in lexicon entries
      System.out.println("Current lexicon entries:");
      TableFactor indicatorFactor = new TableFactor(wordAndTerminalVarNumMap, terminalWordIndicator);
      System.out.println(indicatorFactor.getParameterDescription());

      System.out.println("Added lexicon entries:");
      Tensor addedIndicator = terminalWordIndicator.elementwiseAddition(oldTerminalWordIndicator.elementwiseProduct(-1.0))
          .findKeysLargerThan(0.0);
      TableFactor addedIndicatorFactor = new TableFactor(wordAndTerminalVarNumMap, addedIndicator);
      System.out.println(addedIndicatorFactor.getParameterDescription());

      System.out.println("Removed lexicon entries:");
      Tensor removedIndicator = oldTerminalWordIndicator.elementwiseAddition(terminalWordIndicator.elementwiseProduct(-1.0))
          .findKeysLargerThan(0.0);
      TableFactor removedIndicatorFactor = new TableFactor(wordAndTerminalVarNumMap, removedIndicator);
      System.out.println(removedIndicatorFactor.getParameterDescription());
      oldTerminalIndicator = terminalIndicator;
      oldTerminalWordIndicator = terminalWordIndicator;

      // Decode best trees to find mu variables
      CfgAlignmentModel model = pam.getModelFromParameters(parameters);
      Set<CfgParseTree> usedTerminals = Sets.newHashSet();
      TableFactorBuilder muVars = new TableFactorBuilder(terminalVarNumMap, DenseTensorBuilder.getFactory());
      TableFactorBuilder gammaVars = new TableFactorBuilder(wordVarNumMap.union(terminalVarNumMap), DenseTensorBuilder.getFactory());
      Tensor lagrangeMultipliersTensor = lagrangeMultipliers.build();
      Tensor lagrangeWordMultipliersTensor = lagrangeWordMultipliers.build();
      for (int j = 0; j < trainingData.size(); j++) {
        AlignmentExample example = trainingData.get(j);
        Tensor exampleMultipliers = lagrangeMultipliersTensor.slice(new int[] {0}, new int[] {j});
        Tensor exampleWordMultipliers = lagrangeWordMultipliersTensor.slice(new int[] {0}, new int[] {j});
        
        TableFactor exampleWeights = new TableFactor(pam.getNonterminalVar().union(pam.getTerminalVar()),
            exampleWordMultipliers.elementwiseAddition(exampleMultipliers).elementwiseProduct(-1.0).elementwiseExp());

        CfgParser parser = model.getCfgParser(example, exampleWeights);
        Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
        CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, false);

        CfgParseTree tree = chart.getBestParseTree();
        usedTerminals.clear();
        getTerminals(tree, usedTerminals);

        // Mu variables are 1 for every terminal symbol in the parse tree.
        // Gamma vars are 1 for every used terminal/nonterminal pair.
        muVars.multiply(0.0);
        gammaVars.multiply(0.0);
        for (CfgParseTree usedTerminal : usedTerminals) {
          muVars.setWeight(1.0, usedTerminal.getRoot());
          gammaVars.setWeight(1.0, usedTerminal.getTerminalProductions(), usedTerminal.getRoot());
        }
        
        // Gradient of the lagrange multipliers for this particular example.
        Tensor exampleLexiconIndicator = muVars.build().getWeights();
        Tensor exampleLagrangeGradient = terminalIndicator.elementwiseAddition(exampleLexiconIndicator.elementwiseProduct(-1.0));
        
        Tensor exampleWordLexiconIndicator = gammaVars.build().getWeights();
        Tensor exampleWordLagrangeGradient = terminalWordIndicator.elementwiseAddition(exampleWordLexiconIndicator.elementwiseProduct(-1.0));

        Tensor exampleIndicator = SparseTensor.singleElement(new int[] {0},
            new int[] {trainingData.size()}, new int[] {j}, 1.0);
        
        // Update the lagrange multipliers for this example.
        lagrangeMultipliers.incrementOuterProductWithMultiplier(exampleIndicator,
            exampleLagrangeGradient, -1.0 * stepSize);
        lagrangeWordMultipliers.incrementOuterProductWithMultiplier(exampleIndicator,
            exampleWordLagrangeGradient, -1.0 * stepSize);
        
        double exampleLexiconL2Norm = exampleLexiconIndicator.innerProduct(exampleLexiconIndicator).getByDimKey();
        double innerProduct = exampleLexiconIndicator.innerProduct(terminalIndicator).getByDimKey();
        double exampleWordLexiconL2Norm = exampleWordLexiconIndicator.innerProduct(exampleWordLexiconIndicator).getByDimKey();
        double wordInnerProduct = exampleWordLexiconIndicator.innerProduct(terminalWordIndicator).getByDimKey();
        System.out.println("inner prod: " + innerProduct + " exampleL2: " + exampleLexiconL2Norm);
        System.out.println("word inner prod: " + wordInnerProduct + " word exampleL2: " + exampleWordLexiconL2Norm);
        if (innerProduct == exampleLexiconL2Norm && exampleWordLexiconL2Norm == wordInnerProduct) {
          System.out.println("satisfied constraint: " + example.getWords());
        } else {
          TableFactor exampleLexicon = new TableFactor(wordVarNumMap.union(terminalVarNumMap), exampleWordLexiconIndicator);
          System.out.println(exampleLexicon.getParameterDescription());
        }
      }

      lagrangeMultipliers.maximum(SparseTensor.empty(lagrangeDims, lagrangeSizes));
      lagrangeWordMultipliers.maximum(SparseTensor.empty(lagrangeWordDims, lagrangeWordSizes));
      
      Factor lagrangeFactor = new TableFactor(exampleVarNumMap.union(terminalVarNumMap), lagrangeMultipliers.build());
      // System.out.println(lagrangeFactor.getParameterDescription());
    }

    Tensor lagrangeMultipliersTensor = lagrangeMultipliers.build();
    Tensor lagrangeWordMultipliersTensor = lagrangeWordMultipliers.build();
    
    return new ParametersAndLagrangeMultipliers(parameters,
        lagrangeWordMultipliersTensor.elementwiseAddition(lagrangeMultipliersTensor));
  }
  
  private void getTerminals(CfgParseTree tree, Set<CfgParseTree> accumulator) {
    if (tree.isTerminal()) {
      accumulator.add(tree);
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

      // Soft EM
      /*
      log.startTimer("e_step/marginals");
      Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
      CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, true);
      log.stopTimer("e_step/marginals");

      log.startTimer("e_step/compute_expectations");
      pam.incrementSufficientStatistics(accumulator, currentParameters, chart, 1.0);
      log.stopTimer("e_step/compute_expectations");
      */

      // Hard EM
      log.startTimer("e_step/marginals");
      Factor rootFactor = model.getRootFactor(example.getTree(), parser.getParentVariable());
      CfgParseChart chart = parser.parseMarginal(example.getWords(), rootFactor, false);
      CfgParseTree parse = chart.getBestParseTree();
      log.stopTimer("e_step/marginals");
      
      log.startTimer("e_step/compute_expectations");
      pam.incrementSufficientStatistics(accumulator, currentParameters, parse, 1.0);
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
