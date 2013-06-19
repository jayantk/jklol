package com.jayantkrish.jklol.ccg.supertag; 

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.sequence.TaggedSequence;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.sequence.TaggerUtils.SequenceTaggerError;
import com.jayantkrish.jklol.util.IoUtils;

public class TestSupertagger extends AbstractCli {
  
  private OptionSpec<String> model;
  private OptionSpec<String> testFilename;
  
  private OptionSpec<Double> multitagThreshold;
  
  public TestSupertagger() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testFilename = parser.accepts("testFilename").withRequiredArg().ofType(String.class);

    multitagThreshold = parser.accepts("multitagThreshold").withRequiredArg().ofType(Double.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the serialized model and print its parameters
    Supertagger trainedModel = IoUtils.readSerializedObject(options.valueOf(model), Supertagger.class);

    if (options.has(testFilename)) {
      List<CcgExample> ccgExamples = CcgExample.readExamplesFromFile(
          options.valueOf(testFilename), true, true);
      List<TaggedSequence<WordAndPos, SyntacticCategory>> testData = 
          TrainSupertagger.reformatTrainingExamples(ccgExamples);
      
      SequenceTaggerError error = null;
      if (!options.has(multitagThreshold)) {
        error = TaggerUtils.evaluateTagger(trainedModel, testData);
      } else {
        error = TaggerUtils.evaluateMultitagger(trainedModel, testData, options.valueOf(multitagThreshold));
      }

      System.out.println("TAG ACCURACY: " + error.getTagAccuracy() + " (" + error.getNumTagsCorrect() + " / " + error.getNumTags() + ")");
      System.out.println("SENTENCE ACCURACY: " + error.getSentenceAccuracy() + " (" + error.getNumSentencesCorrect() + " / " + error.getNumSentences() + ")");
      System.out.println("TAGS PER TOKEN: " + error.getTagsPerItem());
    } else {
      // TODO.
      
    }
  }

  public static void main(String[] args) {
    new TestSupertagger().run(args);
  }
}
