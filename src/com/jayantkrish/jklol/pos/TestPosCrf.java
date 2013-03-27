package com.jayantkrish.jklol.pos;

import java.util.Collections;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

public class TestPosCrf extends AbstractCli {

  private OptionSpec<String> model;
  private OptionSpec<String> testData;

  public TestPosCrf() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testData = parser.accepts("testData").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the serialized model and print its parameters
    TrainedPosTagger trainedModel = IoUtils.readSerializedObject(options.valueOf(model),
        TrainedPosTagger.class);

    if (options.has(testData)) {

    } else {
      List<String> wordsToTag = options.nonOptionArguments();
      List<String> posTags = Collections.<String>nCopies(wordsToTag.size(), "");
      PosTaggedSentence sent = new PosTaggedSentence(wordsToTag, posTags);

      DynamicAssignment input = TrainPosCrf.reformatTrainingData(sent, 
          trainedModel.getFeatureGenerator(), trainedModel.getModelFamily()).getInput();

      DynamicFactorGraph dfg = trainedModel.getInstantiatedModel();
      FactorGraph fg = dfg.conditional(input);

      JunctionTree jt = new JunctionTree();
      Assignment output = jt.computeMaxMarginals(fg).getNthBestAssignment(0);

      DynamicAssignment prediction = dfg.getVariables()
          .toDynamicAssignment(output, fg.getAllVariables());
      List<String> labels = Lists.newArrayList();
      for (Assignment plateAssignment : prediction.getPlateFixedAssignments(TrainPosCrf.PLATE_NAME)) {
        List<Object> values = plateAssignment.getValues();
        labels.add((String) values.get(1));
      }
    
      System.out.println(input);
      System.out.println(labels);
    }
  }

  public static void main(String[] args) {
    new TestPosCrf().run(args);
  }
}