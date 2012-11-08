package com.jayantkrish.jklol.cli;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.GibbsSampler;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.OracleAdapter;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainRbm extends AbstractCli {
  
  private static final DiscreteVariable trueFalse = new DiscreteVariable("trueFalse", Arrays.asList("F", "T"));
  
  private OptionSpec<String> inputData;
  private OptionSpec<String> modelOutput;
  private OptionSpec<Integer> hiddenUnits;

  public TrainRbm() {
    super(CommonOptions.STOCHASTIC_GRADIENT);
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    inputData = parser.accepts("input").withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    hiddenUnits = parser.accepts("hiddenUnits").withRequiredArg().ofType(Integer.class).required();
  }

  @Override
  public void run(OptionSet options) {
    // Build the RBM from the input data.
    String inputFilename = options.valueOf(inputData);
    List<String> observedNames = Lists.newArrayList(Sets.newHashSet(
        IoUtils.readColumnFromDelimitedFile(inputFilename, 1, ",")));
    ParametricFactorGraph rbmFamily = buildRbm(observedNames, options.valueOf(hiddenUnits));
    
    // Read in the training examples.
    List<Example<Assignment, Assignment>> trainingExamples = readTrainingData(inputFilename, 
        ",", rbmFamily);

    // The iterations option is interpreted as the number of passes over the training data to perform.
    StochasticGradientTrainer trainer = createStochasticGradientTrainer(trainingExamples.size()); 
    LoglikelihoodOracle oracle = new LoglikelihoodOracle(rbmFamily, new GibbsSampler(0, 10, 0));
    SufficientStatistics parameters = rbmFamily.getNewSufficientStatistics();
    parameters.perturb(0.01);
    parameters = trainer.train(OracleAdapter.createAssignmentAdapter(oracle),
        parameters, trainingExamples);

    // Print training error.
    FactorGraph factorGraph = rbmFamily.getModelFromParameters(parameters)
        .conditional(DynamicAssignment.EMPTY);
    
    System.out.println(rbmFamily.getParameterDescription(parameters));
    System.out.println(factorGraph.getParameterDescription());
    
    JunctionTree jt = new JunctionTree();
    for (Example<Assignment, Assignment> example : trainingExamples) {
      Assignment a = example.getOutput();
      MaxMarginalSet maxMarginals = jt.computeMaxMarginals(factorGraph.conditional(a));
      System.out.println(maxMarginals.getNthBestAssignment(0));
    }
  }
    
  public static void main(String[] args) {
    new TrainRbm().run(args);
  }
  
  private static List<Example<Assignment, Assignment>> readTrainingData(
      String filename, String delimiter, ParametricFactorGraph rbmFamily) {
    DiscreteVariable rowNames = DiscreteVariable.fromCsvColumn("rows", filename, delimiter, 0);
    DiscreteVariable featureNames = DiscreteVariable.fromCsvColumn("features", 
        filename, delimiter, 1);
    
    for (String line : IoUtils.readLines(filename)) {
      line.split(",");
    }
    
    // Read in the matrix of variables.
    VariableNumMap rowVar = VariableNumMap.singleton(0, "row", rowNames);
    VariableNumMap featureVar = VariableNumMap.singleton(1, "features", featureNames);
    VariableNumMap trueFalseVar = VariableNumMap.singleton(2, "trueFalse", trueFalse);
    TableFactor factor = TableFactor.fromDelimitedFile(Arrays.asList(rowVar, featureVar, trueFalseVar), 
        IoUtils.readLines(filename), delimiter, false);
    
    // Convert the matrix to assignments.
    List<Example<Assignment, Assignment>> examples = Lists.newArrayList();
    VariableNumMap allVars = rbmFamily.getVariables().getFixedVariables();
    for (Object rowName : rowNames.getValues()) {
      Assignment a = rowVar.outcomeArrayToAssignment(rowName);
      DiscreteFactor row = factor.conditional(a);
      Iterator<Outcome> iter = row.outcomeIterator();
      Assignment exampleAssignment = Assignment.EMPTY;
      while (iter.hasNext()) {
        Outcome outcome = iter.next();
        
        List<Object> values = outcome.getAssignment().getValues();
        String varName = (String) values.get(0);
        String value = (String) values.get(1);
        
        exampleAssignment = exampleAssignment.union(allVars.getVariablesByName(varName)
            .outcomeArrayToAssignment(value));
      }
      
      System.out.println(exampleAssignment);
      examples.add(Example.create(Assignment.EMPTY, exampleAssignment));
    }
    
    return examples;
  }
  
  /**
   * Constructs a restricted Boltzmann Machine (RBM) whose observed variables
   * are named {@code observedNames}. The RBM additionally contains 
   * {@code numHidden} hidden variables, named "hidden-0", "hidden-1", etc.
   * The factors in the model connect pairs of observed and hidden variables.  
   *  
   * @param observedNames
   * @param numHidden
   * @return
   */
  private static ParametricFactorGraph buildRbm(List<String> observedNames,
      int numHidden) {    
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    // Create variables.
    for (String observedName : observedNames) {
      builder.addVariable(observedName, trueFalse);
    }
    List<String> hiddenNames = Lists.newArrayList();
    for (int i = 0; i < numHidden; i++) {
      String hiddenName = "hidden-" + i;
      hiddenNames.add(hiddenName);
      builder.addVariable(hiddenName, trueFalse);
    }
    
    // Add factors.
    DiscreteVariable featureType = new DiscreteVariable("featureVar", Arrays.asList("TT"));
    VariableNumMap featureVar = VariableNumMap.singleton(
        observedNames.size() + hiddenNames.size() + 1, "featureVar", featureType);
    VariableNumMap vars = builder.getVariables();
    for (String observedName : observedNames) {
      VariableNumMap observed = vars.getVariablesByName(observedName); 
      for (String hiddenName : hiddenNames) {
        VariableNumMap hidden = vars.getVariablesByName(hiddenName);
        // Each factor encodes a single indicator for when both variables are 1.
        VariableNumMap factorVars = VariableNumMap.unionAll(featureVar, observed, hidden);
        Assignment trueAssignment = factorVars.outcomeArrayToAssignment("T", "T", "TT");
        DiscreteLogLinearFactor factor = new DiscreteLogLinearFactor(observed.union(hidden), 
            featureVar, TableFactor.pointDistribution(factorVars, trueAssignment));
        builder.addUnreplicatedFactor(observedName + "#" + hiddenName, factor);
      }
    }
    return builder.build();
  }
}
