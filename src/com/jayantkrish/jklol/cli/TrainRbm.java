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
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.OracleAdapter;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainRbm {
  
  private static DiscreteVariable trueFalse = new DiscreteVariable("trueFalse", Arrays.asList("F", "T"));
  
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

  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments.
    OptionSpec<String> inputData = parser.accepts("input").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    OptionSpec<Integer> hiddenUnits = parser.accepts("hiddenUnits").withRequiredArg().ofType(Integer.class).required();
    // Optional options
    OptionSpec<Integer> iterations = parser.accepts("iterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    OptionSpec<Integer> batchSize = parser.accepts("batchSize").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    OptionSpec<Double> initialStepSize = parser.accepts("initialStepSize").withRequiredArg().ofType(Double.class).defaultsTo(1.0);
    OptionSpec<Double> l2Regularization = parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
    // boolean options.
    parser.accepts("brief"); // Hides training output.
    OptionSet options = parser.parse(args);
    
    // Build the RBM from the input data.
    String inputFilename = options.valueOf(inputData);
    List<String> observedNames = Lists.newArrayList(Sets.newHashSet(
        IoUtils.readColumnFromDelimitedFile(inputFilename, 1, ",")));
    ParametricFactorGraph rbmFamily = buildRbm(observedNames, options.valueOf(hiddenUnits));
    
    // Read in the training examples.
    List<Example<Assignment, Assignment>> trainingExamples = readTrainingData(inputFilename, 
        ",", rbmFamily);

    // The iterations option is interpreted as the number of passes over the training data to perform.
    int numIterations = (int) Math.ceil(options.valueOf(iterations) * trainingExamples.size() 
        / ((double) options.valueOf(batchSize)));
    LogFunction log = (options.has("brief")) ? new NullLogFunction() : new DefaultLogFunction();
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(
        numIterations, options.valueOf(batchSize), options.valueOf(initialStepSize), true, 
        options.valueOf(l2Regularization), log);
    LoglikelihoodOracle oracle = new LoglikelihoodOracle(rbmFamily, new GibbsSampler(0, 10, 0));
    SufficientStatistics parameters = rbmFamily.getNewSufficientStatistics();
    parameters.perturb(0.01);
    parameters = trainer.train(OracleAdapter.createAssignmentAdapter(oracle),
        parameters, trainingExamples);

    // Print training error.
    FactorGraph factorGraph = rbmFamily.getFactorGraphFromParameters(parameters)
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
}
