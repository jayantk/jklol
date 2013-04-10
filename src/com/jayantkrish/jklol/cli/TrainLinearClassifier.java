package com.jayantkrish.jklol.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.evaluation.FactorGraphPredictor;
import com.jayantkrish.jklol.evaluation.FactorGraphPredictor.SimpleFactorGraphPredictor;
import com.jayantkrish.jklol.evaluation.LossFunctions;
import com.jayantkrish.jklol.evaluation.LossFunctions.PrecisionRecall;
import com.jayantkrish.jklol.evaluation.Predictor.Prediction;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.MaxMarginOracle;
import com.jayantkrish.jklol.training.MaxMarginOracle.HammingCost;
import com.jayantkrish.jklol.training.OracleAdapter;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Command line program for training a linear classifier.
 * 
 * @author jayantk
 */
public class TrainLinearClassifier extends AbstractCli {
  
  private OptionSpec<String> featureVectorFile;
  private OptionSpec<String> labelFile;
  private OptionSpec<String> modelOutput;
  
  private OptionSpec<String> delimiterOption;
  private OptionSpec<Void> printTrainingError;
  
  public static final String INPUT_VAR_NAME = "x";
  public static final String OUTPUT_VAR_NAME = "y";
  
  public TrainLinearClassifier() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    featureVectorFile = parser.accepts("features").withRequiredArg().ofType(String.class).required();
    labelFile = parser.accepts("labels").withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();

    // Optional options
    delimiterOption = parser.accepts("delimiter").withRequiredArg().ofType(String.class)
        .defaultsTo(",");

    printTrainingError = parser.accepts("printTrainingError");
  }

  @Override
  public void run(OptionSet options) {
    String delimiter = options.valueOf(delimiterOption);
    // The first column of both files is the set of example IDs.
    List<String> exampleIds = IoUtils.readUniqueColumnValuesFromDelimitedFile(
        options.valueOf(featureVectorFile), 0, delimiter);
    List<String> featureNames = IoUtils.readUniqueColumnValuesFromDelimitedFile(
        options.valueOf(featureVectorFile), 1, delimiter);
    List<String> labelNames = IoUtils.readUniqueColumnValuesFromDelimitedFile(
        options.valueOf(labelFile), 1, delimiter);

    DiscreteVariable exampleIdType = new DiscreteVariable("exampleIds", exampleIds);
    DiscreteVariable featureVarType = new DiscreteVariable("features", featureNames);
    ParametricFactorGraph family = buildModel(featureVarType, labelNames);
    VariableNumMap inputVar = family.getVariables().getFixedVariables().getVariablesByName(INPUT_VAR_NAME);
    VariableNumMap outputVar = family.getVariables().getFixedVariables().getVariablesByName(OUTPUT_VAR_NAME);

    // Read in the training data
    VariableNumMap exampleVar = VariableNumMap.singleton(0, "exampleIds", exampleIdType);
    VariableNumMap featureVar = VariableNumMap.singleton(1, "features", featureVarType);
    VariableNumMap featureVectorVars = exampleVar.union(featureVar);
    TableFactor featureVectors = TableFactor.fromDelimitedFile(featureVectorVars, 
        IoUtils.readLines(options.valueOf(featureVectorFile)), delimiter, false);    
    List<Example<Assignment, Assignment>> trainingData = constructClassificationData(
        IoUtils.readLines(options.valueOf(labelFile)), exampleVar, featureVectors, inputVar, outputVar);
    
    // Train the model.
    MaxMarginOracle oracle = new MaxMarginOracle(family, new HammingCost(), new JunctionTree());
    SufficientStatistics parameters = family.getNewSufficientStatistics();
    
    StochasticGradientTrainer trainer = createStochasticGradientTrainer(trainingData.size());
    parameters = trainer.train(OracleAdapter.createAssignmentAdapter(oracle), parameters, trainingData);

    // Serialize the trained model to disk.
    FactorGraph factorGraph = family.getModelFromParameters(parameters).getFactorGraph(DynamicAssignment.EMPTY);
    IoUtils.serializeObjectToFile(factorGraph, options.valueOf(modelOutput));

    System.out.println(family.getParameterDescription(parameters));
    
    if (options.has(printTrainingError)) {
      logError(trainingData, factorGraph);
    }
  }
  
  public static List<Example<Assignment, Assignment>> constructClassificationData(Iterable<String> labelLines,
      VariableNumMap exampleVar, TableFactor featureVectors, VariableNumMap inputVar, VariableNumMap outputVar) {
    List<Example<Assignment, Assignment>> trainingData = Lists.newArrayList();
    for (String line : labelLines) {
      String[] parts = line.split(",");
      Preconditions.checkArgument(parts.length == 2, "Invalid label line: " + line);

      Assignment exampleIdAssignment = exampleVar.outcomeArrayToAssignment(parts[0]);
      Tensor featureVector = featureVectors.conditional(exampleIdAssignment).getWeights();

      Assignment inputAssignment = inputVar.outcomeArrayToAssignment(featureVector);
      Assignment outputAssignment = outputVar.outcomeArrayToAssignment(parts[1]);
      trainingData.add(Example.create(inputAssignment, outputAssignment));
    }
    return trainingData;
  }
  
  public static void logError(List<Example<Assignment, Assignment>> data, FactorGraph classifier) {
    SimpleFactorGraphPredictor predictor = new FactorGraphPredictor.SimpleFactorGraphPredictor(
        classifier, classifier.getVariables().getVariablesByName(OUTPUT_VAR_NAME), new JunctionTree());
    
    PrecisionRecall<Object> loss = LossFunctions.newPrecisionRecall();
    double numCorrect = 0;
    for (Example<Assignment, Assignment> example : data) {
      Assignment input = example.getInput();
      Assignment output = example.getOutput();
      Prediction<Assignment, Assignment> prediction = predictor.getBestPrediction(input);

      // System.out.println("INPUT: " + input);
      System.out.println("PREDICTION: " + prediction.getBestPrediction());
      System.out.println("OUTPUT: " + output);
      
      boolean predictionBoolean = prediction.getBestPrediction().getOnlyValue().equals("T");
      boolean outputBoolean = output.getOnlyValue().equals("T");
      loss.accumulatePrediction(predictionBoolean, outputBoolean, prediction.getBestPredictionScore());
      
      numCorrect += (prediction.getBestPrediction().equals(output)) ? 1 : 0;
    }
    
    // System.out.println(loss);
    System.out.println("ACCURACY: " + (numCorrect / data.size()));
  }
  
  public static void main(String[] args) {
    new TrainLinearClassifier().run(args);
  }
  
  private ParametricFactorGraph buildModel(DiscreteVariable featureVar, 
      List<String> outputLabels) {
    // A linear classifier is represented as a parametric factor graph with
    // two variables: an input variable (x) whose values are feature vectors
    // and an output variable (y) whose values are the possible labels.
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    DiscreteVariable outputVar = new DiscreteVariable("tf", outputLabels);
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);

    // Add the variables to the factor graph being built, and
    // get references to the variables just created.
    builder.addVariable("x", tensorVar);
    builder.addVariable("y", outputVar);
    VariableNumMap x = builder.getVariables().getVariablesByName("x");
    VariableNumMap y = builder.getVariables().getVariablesByName("y");

    // A ConditionalLogLinearFactor represents a trainable linear classifier
    // (yes, the name is terrible). Just copy this definition, replacing x, y
    // and featureVar with whatever you called those things.
    builder.addUnreplicatedFactor("classifier", new ConditionalLogLinearFactor(x, y,
        VariableNumMap.emptyMap(), featureVar));
    // Builds the actual trainable model.
    return builder.build();
  }
}
