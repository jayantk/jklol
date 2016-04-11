package com.jayantkrish.jklol.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.LinearClassifierFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

public class TestLinearClassifier extends AbstractCli {
  
  private OptionSpec<String> model;
  private OptionSpec<String> featureFile;
  private OptionSpec<String> labelFile;
  
  private OptionSpec<String> delimiterOption;

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    featureFile = parser.accepts("features").withRequiredArg().ofType(String.class).required();
    labelFile = parser.accepts("labels").withRequiredArg().ofType(String.class).required();
    
    // Optional options
    delimiterOption = parser.accepts("delimiter").withRequiredArg().ofType(String.class)
        .defaultsTo(",");
  }

  @Override
  public void run(OptionSet options) {
    FactorGraph classifier = IoUtils.readSerializedObject(options.valueOf(model), 
        FactorGraph.class);
    
    // The first column of both files is the set of example IDs.
    String delimiter = options.valueOf(delimiterOption);
    List<String> exampleIds = IoUtils.readUniqueColumnValuesFromDelimitedFile(
        options.valueOf(featureFile), 0, delimiter);
    DiscreteVariable exampleIdType = new DiscreteVariable("exampleIds", exampleIds);
    
    // We expect classifier to only contain a LinearClassifierFactor, which
    // we must read in to get the feature dictionary.
    LinearClassifierFactor factor = (LinearClassifierFactor) classifier.getFactors().get(0);
    DiscreteVariable featureVarType = factor.getFeatureVariableType();
    
    VariableNumMap exampleVar = VariableNumMap.singleton(0, "exampleIds", exampleIdType);
    VariableNumMap featureVar = VariableNumMap.singleton(1, "features", featureVarType);
    VariableNumMap featureVectorVars = exampleVar.union(featureVar);
    TableFactor featureVectors = TableFactor.fromDelimitedFile(featureVectorVars, 
        IoUtils.readLines(options.valueOf(featureFile)), delimiter, true,
        SparseTensorBuilder.getFactory());
    
    VariableNumMap inputVar = classifier.getVariables().getVariablesByName(
        TrainLinearClassifier.INPUT_VAR_NAME);
    VariableNumMap outputVar = classifier.getVariables().getVariablesByName(
        TrainLinearClassifier.OUTPUT_VAR_NAME);
    List<Example<Assignment, Assignment>> testData = TrainLinearClassifier
        .constructClassificationData(IoUtils.readLines(options.valueOf(labelFile)),
            exampleVar, featureVectors, inputVar, outputVar);
    
    TrainLinearClassifier.logError(testData, classifier);
  }

  public static void main(String[] args) {
    new TestLinearClassifier().run(args);
  }
}
