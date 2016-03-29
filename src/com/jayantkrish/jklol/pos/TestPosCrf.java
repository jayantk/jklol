package com.jayantkrish.jklol.pos;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.sequence.TaggerUtils.SequenceTaggerError;
import com.jayantkrish.jklol.util.IoUtils;

public class TestPosCrf extends AbstractCli {

  private OptionSpec<String> model;
  private OptionSpec<String> testFilename;
  
  private OptionSpec<String> cliInput;

  public TestPosCrf() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testFilename = parser.accepts("testFilename").withRequiredArg().ofType(String.class);
    
    cliInput = parser.nonOptions().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the serialized model and print its parameters
    PosTagger trainedModel = IoUtils.readSerializedObject(options.valueOf(model), PosTagger.class);

    if (options.has(testFilename)) {
      List<PosTaggedSentence> testData = PosTaggerUtils.readTrainingData(
          options.valueOf(testFilename));

      SequenceTaggerError error = TaggerUtils.evaluateTagger(trainedModel, testData);

      System.out.println("TAG ACCURACY: " + error.getTagAccuracy() + " (" + error.getNumTagsCorrect() + " / " + error.getNumTags() + ")");
      System.out.println("SENTENCE ACCURACY: " + error.getSentenceAccuracy() + " (" + error.getNumSentencesCorrect() + " / " + error.getNumSentences() + ")");
    } else {
      PosTaggedSentence tags = trainedModel.tag(options.valuesOf(cliInput));
      System.out.println(tags.getWords());
      System.out.println(tags.getPos());
    }
  }

  public static void main(String[] args) {
    new TestPosCrf().run(args);
  }

}