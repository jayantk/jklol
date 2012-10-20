package com.jayantkrish.jklol.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainLinearClassifier {
  
  public static ParametricFactorGraph buildModel(List<String> features, 
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

    // Define the names of the features used in the classifier. Our classifier
    // will expect input vectors of dimension features.size(). This DiscreteVariable 
    // maps each feature name to an index in the feature vector; it can also 
    // be used to construct the input vectors.
    DiscreteVariable featureVar = new DiscreteVariable("features", features);

    // A ConditionalLogLinearFactor represents a trainable linear classifier
    // (yes, the name is terrible). Just copy this definition, replacing x, y
    // and featureVar with whatever you called those things.
    builder.addUnreplicatedFactor("classifier", new ConditionalLogLinearFactor(x, y,
        VariableNumMap.emptyMap(), featureVar));
    // Builds the actual trainable model.
    return builder.build();
  }

  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments.
    OptionSpec<String> inputData = parser.accepts("input").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> labelData = parser.accepts("labels").withRequiredArg().ofType(String.class).required();
    OptionSpec<String> modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    // Optional options
    OptionSpec<String> delimiterOption = parser.accepts("delimiter").withRequiredArg().ofType(String.class)
        .required().defaultsTo(",");
    OptionUtils.addStochasticGradientOptions(parser);
    OptionSet options = parser.parse(args);

    String delimiter = options.valueOf(delimiterOption);
    List<String> featureNames = IoUtils.readUniqueColumnValuesFromDelimitedFile(
        options.valueOf(inputData), 1, delimiter);
    List<String> labelNames = IoUtils.readUniqueColumnValuesFromDelimitedFile(
        options.valueOf(labelData), 1, delimiter);
    
  }
}
