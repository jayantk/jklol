package com.jayantkrish.jklol.pos;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.util.IoUtils;

public class TestPosCrf extends AbstractCli {

  private OptionSpec<String> model;
  private OptionSpec<String> testFilename;

  public TestPosCrf() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testFilename = parser.accepts("testFilename").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the serialized model and print its parameters
    PosTagger trainedModel = IoUtils.readSerializedObject(options.valueOf(model), PosTagger.class);

    if (options.has(testFilename)) {
      List<PosTaggedSentence> testData = PosTaggerUtils.readTrainingData(
          options.valueOf(testFilename));

      int numCorrect = 0;
      int total = 0;
      for (PosTaggedSentence testDatum : testData) {
        List<String> prediction = trainedModel.tagWords(testDatum.getWords()).getPos();
        List<String> actual = testDatum.getPos();
        Preconditions.checkState(prediction.size() == actual.size());
        for (int i = 0; i < prediction.size(); i++) {
          total++;

          if (prediction.get(i).equals(actual.get(i))) {
            numCorrect++;
          }
        }

        System.out.println(testDatum.getWords());
        System.out.println(prediction);
        System.out.println(actual);
      }
      
      double accuracy = ((double) numCorrect) / total;
      System.out.println("PER-WORD ACCURACY: " + accuracy + " (" + numCorrect + " / " + total + ")");
    } else {
      PosTaggedSentence tags = trainedModel.tagWords(options.nonOptionArguments());
      System.out.println(tags.getWords());
      System.out.println(tags.getPos());
    }
  }

  public static void main(String[] args) {
    new TestPosCrf().run(args);
  }
}