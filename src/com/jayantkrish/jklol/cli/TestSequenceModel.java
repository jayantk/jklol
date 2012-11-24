package com.jayantkrish.jklol.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Tests a sequence model, which is serialized to disk as a 
 * {@code TrainedModelSet}. 
 *
 * @author jayantk
 */
public class TestSequenceModel extends AbstractCli {

  private OptionSpec<String> model;

  public TestSequenceModel() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    // Read in the serialized model.
    TrainedModelSet trainedModel = IoUtils.readSerializedObject(options.valueOf(model),
        TrainedModelSet.class);
    DynamicFactorGraph sequenceModel = trainedModel.getInstantiatedModel();

    // Read in the words to tag.
    List<String> wordsToTag = options.nonOptionArguments();
    List<String> labels = ModelUtils.testSequenceModel(wordsToTag, sequenceModel);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < wordsToTag.size(); i++) {
      sb.append(wordsToTag.get(i) + "/" + labels.get(i) + " ");
    }

    // Print the predicted labels.
    System.out.println(sb);
  }

  public static void main(String[] args) {
    new TestSequenceModel().run(args);
  }
}
