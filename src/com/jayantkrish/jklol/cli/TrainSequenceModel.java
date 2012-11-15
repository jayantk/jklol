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
public class TrainSequenceModel extends AbstractCli {

  public static final String PLATE_NAME = "plate";
  public static final String INPUT_NAME = "x";
  public static final String OUTPUT_NAME = "y";

  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> emissionFeatures;
  private OptionSpec<String> modelOutput;
  private static final String MAX_MARGIN = "maxMargin";

  public TrainSequenceModel() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    // The training data. Expects a filename of space-separated
    // elements,
    // where each element has the format word/label pairs.
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
    // Feature functions of word/label pairs
    emissionFeatures = parser.accepts("emissionFeatures").withRequiredArg()
        .ofType(String.class).required();
    // Where to serialize the trained factor graph
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    // Optional arguments.
    parser.accepts(MAX_MARGIN); // Trains with a max-margin method.
  }

  @Override
  public void run(OptionSet options) {
    // Construct the sequence model
    ParametricFactorGraph sequenceModel = ModelUtils.buildSequenceModel(
        IoUtils.readLines(options.valueOf(emissionFeatures)));

    // Read in the training data, formatted as assignments.
    List<Example<DynamicAssignment, DynamicAssignment>> trainingData = readTrainingData(
        sequenceModel, options.valueOf(trainingFilename));

    SufficientStatistics parameters = run(sequenceModel, trainingData, options.has(MAX_MARGIN));

    DynamicFactorGraph factorGraph = sequenceModel.getModelFromParameters(parameters);

    System.out.println("Serializing trained model...");
    IoUtils.serializeObjectToFile(factorGraph, options.valueOf(modelOutput));

    System.out.println("Learned parameters: ");
    System.out.println(sequenceModel.getParameterDescription(parameters));
  }

  public SufficientStatistics run(ParametricFactorGraph sequenceModel, 
      List<Example<DynamicAssignment, DynamicAssignment>> trainingData,
      boolean useMaxMargin) {
    System.out.println(trainingData.size() + " training examples.");

    // Estimate parameters
    GradientOracle<DynamicFactorGraph, Example<DynamicAssignment, DynamicAssignment>> oracle;
    if (useMaxMargin) {
      oracle = new MaxMarginOracle(sequenceModel, new MaxMarginOracle.HammingCost(), new JunctionTree());
    } else {
      oracle = new LoglikelihoodOracle(sequenceModel, new JunctionTree());
    }

    System.out.println("Training...");
    StochasticGradientTrainer trainer = createStochasticGradientTrainer(trainingData.size());
    SufficientStatistics parameters = trainer.train(
        oracle, sequenceModel.getNewSufficientStatistics(), trainingData);

    return parameters;
  }


  public static void main(String[] args) {
    new TrainSequenceModel().run(args);
  }


  private static List<Example<DynamicAssignment, DynamicAssignment>> readTrainingData(
      ParametricFactorGraph model, String trainingFilename) {
    DynamicVariableSet plate = model.getVariables().getPlate(PLATE_NAME);
    VariableNumMap x = plate.getFixedVariables().getVariablesByName(INPUT_NAME);
    VariableNumMap y = plate.getFixedVariables().getVariablesByName(OUTPUT_NAME);

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
      DynamicAssignment input = DynamicAssignment.createPlateAssignment(PLATE_NAME, inputs);
      DynamicAssignment output = DynamicAssignment.createPlateAssignment(PLATE_NAME, outputs);
      examples.add(Example.create(input, output));
    }

    return examples;
  }
}