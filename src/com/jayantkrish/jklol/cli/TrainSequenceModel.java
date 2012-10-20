package com.jayantkrish.jklol.cli;

import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.VariableNamePattern;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.LoglikelihoodOracle;
import com.jayantkrish.jklol.training.MaxMarginOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Trains a sequence model from labeled data. At the moment, this only
 * allows CRFs, but in the future it may be extended to support HMMs.
 *
 * @author jayantk
 */
public class TrainSequenceModel {

  public static String PLATE_NAME="plate";
  public static String INPUT_NAME="x";
  public static String OUTPUT_NAME="y";

  public static ParametricFactorGraph buildModel(String emissionFeatureFilename) {
    // Read in the possible values of each variable.
    List<String> words = IoUtils.readColumnFromDelimitedFile(emissionFeatureFilename, 0, ",");
    List<String> labels = IoUtils.readColumnFromDelimitedFile(emissionFeatureFilename, 1, ",");
    List<String> emissionFeatures = IoUtils.readColumnFromDelimitedFile(emissionFeatureFilename, 2, ",");
    // Create dictionaries for each variable's values.
    DiscreteVariable wordType = new DiscreteVariable("word", words);
    DiscreteVariable labelType = new DiscreteVariable("label", labels);
    DiscreteVariable emissionFeatureType = new DiscreteVariable("emissionFeature", emissionFeatures);
    
    // Create a dynamic factor graph with a single plate replicating the input/output variables.
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    builder.addPlate("plate", new VariableNumMap(Ints.asList(1, 2),
            Arrays.asList("x", "y"), Arrays.asList(wordType, labelType)), 10000);
    VariableNumMap plateVars = new VariableNumMap(Ints.asList(1, 2), 
        Arrays.asList("plate/?(0)/x", "plate/?(0)/y"), Arrays.asList(wordType, labelType));

    // Read in the emission features (for the word/label weights).
    VariableNumMap x = plateVars.getVariablesByName("plate/?(0)/x");
    VariableNumMap y = plateVars.getVariablesByName("plate/?(0)/y");
    VariableNumMap emissionFeatureVar = VariableNumMap.singleton(0, "emissionFeature", emissionFeatureType);
    TableFactor emissionFeatureFactor = TableFactor.fromDelimitedFile(
        Arrays.asList(x, y, emissionFeatureVar), IoUtils.readLines(emissionFeatureFilename),
        ",", false);
    
    // Add a parametric factor for the word/label weights
    DiscreteLogLinearFactor emissionFactor = new DiscreteLogLinearFactor(x.union(y), emissionFeatureVar,
        emissionFeatureFactor);
    builder.addFactor("wordLabelFactor", emissionFactor, 
        VariableNamePattern.fromTemplateVariables(plateVars, VariableNumMap.emptyMap()));

    // Create a factor connecting adjacent labels 
    VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("plate/?(0)/y", "plate/?(1)/y"), Arrays.asList(labelType, labelType));
    builder.addFactor("transition", DiscreteLogLinearFactor.createIndicatorFactor(adjacentVars),
        VariableNamePattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap()));

    return builder.build();
  }

  public static List<Example<DynamicAssignment, DynamicAssignment>> readTrainingData(
      ParametricFactorGraph model, String trainingFilename) {
    DynamicVariableSet plate = model.getVariables().getPlate("plate");
    VariableNumMap x = plate.getFixedVariables().getVariablesByName("x");
    VariableNumMap y = plate.getFixedVariables().getVariablesByName("y");
    
    List<Example<DynamicAssignment, DynamicAssignment>> examples = Lists.newArrayList();
    for (String line : IoUtils.readLines(trainingFilename)) {
      String[] chunks = line.split(" ");

      List<Assignment> inputs = Lists.newArrayList();
      List<Assignment> outputs = Lists.newArrayList();
      for (int i = 0; i < chunks.length; i++) {
        String[] parts = chunks[i].split("/");

        Preconditions.checkArgument(parts.length == 2, "Invalid input line: " + line);

        inputs.add(x.outcomeArrayToAssignment(parts[0]));
        outputs.add(y.outcomeArrayToAssignment(parts[1]));
      }
      DynamicAssignment input = DynamicAssignment.createPlateAssignment("plate", inputs);
      DynamicAssignment output = DynamicAssignment.createPlateAssignment("plate", outputs);
      examples.add(Example.create(input, output));
    }

    return examples;
  }

  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments
    // The training data. Expects a filename of space-separated elements, 
    // where each element has the format word/label pairs.
    OptionSpec<String> trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
    // Feature functions of word/label pairs
    OptionSpec<String> emissionFeatures = parser.accepts("emissionFeatures").withRequiredArg()
        .ofType(String.class).required();
    // Where to serialize the trained factor graph
    OptionSpec<String> modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    // Optional options
    OptionUtils.addStochasticGradientOptions(parser);
    parser.accepts("maxMargin"); // Trains with a max-margin method.
    OptionSet options = parser.parse(args); 
    
    // Construct the sequence model
    ParametricFactorGraph sequenceModel = buildModel(options.valueOf(emissionFeatures));

    // Read in the training data, formatted as assignments.
    List<Example<DynamicAssignment, DynamicAssignment>> trainingData = readTrainingData(
        sequenceModel, options.valueOf(trainingFilename));

    System.out.println(trainingData.size() + " training examples.");

    // Estimate parameters
    GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle;
    if (options.has("maxMargin")) {
      oracle = new MaxMarginOracle(sequenceModel, new MaxMarginOracle.HammingCost(), new JunctionTree());
    } else {
      oracle = new LoglikelihoodOracle(sequenceModel, new JunctionTree());
    }

    System.out.println("Training...");
    StochasticGradientTrainer trainer = OptionUtils.createStochasticGradientTrainer(
        options, trainingData.size());
    SufficientStatistics parameters = trainer.train(
        oracle, sequenceModel.getNewSufficientStatistics(), trainingData);
    DynamicFactorGraph factorGraph = sequenceModel.getFactorGraphFromParameters(parameters);

    System.out.println("Serializing trained model...");
    IoUtils.serializeObjectToFile(factorGraph, options.valueOf(modelOutput));
 
    System.out.println("Learned parameters: ");
    System.out.println(sequenceModel.getParameterDescription(parameters));

    System.exit(0);
  }
}