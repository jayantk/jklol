package com.jayantkrish.jklol.ccg.supertag; 

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.cli.TrainCcg;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.inference.BeamPruningStrategy;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.sequence.TaggedSequence;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.sequence.TaggerUtils.SequenceTaggerError;
import com.jayantkrish.jklol.util.IoUtils;

public class TestSupertagger extends AbstractCli {
  
  private OptionSpec<String> model;
  private OptionSpec<String> testFilename;
  
  private OptionSpec<String> syntaxMap;
  private OptionSpec<Double> multitagThreshold;
  private OptionSpec<Double> beamPruningThreshold;
  
  public TestSupertagger() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testFilename = parser.accepts("testFilename").withRequiredArg().ofType(String.class);
    
    syntaxMap = parser.accepts("syntaxMap").withRequiredArg().ofType(String.class);
    multitagThreshold = parser.accepts("multitagThreshold").withRequiredArg().ofType(Double.class);
    beamPruningThreshold = parser.accepts("beamPruningThreshold").withRequiredArg().ofType(Double.class);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the serialized model and print its parameters
    Supertagger trainedModel = IoUtils.readSerializedObject(options.valueOf(model), Supertagger.class);

    if (options.has(beamPruningThreshold)) {
      FactorGraphSupertagger fgTagger = (FactorGraphSupertagger) trainedModel;
      trainedModel = new FactorGraphSupertagger(fgTagger.getModelFamily(),
          fgTagger.getParameters(), fgTagger.getInstantiatedModel(), fgTagger.getFeatureGenerator(),
          fgTagger.getInputGenerator(), fgTagger.getMaxMarginalCalculator(),
          new JunctionTree(true, new BeamPruningStrategy(options.valueOf(beamPruningThreshold))),
          fgTagger.getStartInput(), fgTagger.getStartLabel());
    }

    if (options.has(testFilename)) {
      List<CcgExample> ccgExamples = TrainCcg.readTrainingData(
          options.valueOf(testFilename), true, true, options.valueOf(syntaxMap));

      List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> testData = 
          TrainSupertagger.reformatTrainingExamples(ccgExamples, false);

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
