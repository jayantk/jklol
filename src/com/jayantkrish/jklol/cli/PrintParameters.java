package com.jayantkrish.jklol.cli;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Prints the parameters of a model. The model is read from
 * disk as a {@code TrainedModelSet}.
 *
 * @author jayantk
 */
public class PrintParameters extends AbstractCli {

  private OptionSpec<String> model;
  private OptionSpec<Integer> numFeatures;

  public PrintParameters() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    numFeatures = parser.accepts("numFeatures").withRequiredArg().ofType(Integer.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the serialized model and print its parameters
    TrainedModelSet trainedModel = IoUtils.readSerializedObject(options.valueOf(model),
        TrainedModelSet.class);
    ParametricFactorGraph modelFamily = trainedModel.getModelFamily();
    SufficientStatistics parameters = trainedModel.getParameters();

    int num = -1;
    if (options.has(numFeatures)) {
      num = options.valueOf(numFeatures);
    }

    System.out.println("Model Parameters:");
    System.out.println(modelFamily.getParameterDescription(parameters, num));
  }

  public static void main(String[] args) {
    new PrintParameters().run(args);
  }
}
