package com.jayantkrish.jklol.pos;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;
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

      MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();
      PosError error = executor.mapReduce(testData, new PosEvaluationMapper(trainedModel),
          new PosEvaluationReducer());

      System.out.println("TAG ACCURACY: " + error.getTagAccuracy() + " (" + error.getNumTagsCorrect() + " / " + error.getNumTags() + ")");
      System.out.println("SENTENCE ACCURACY: " + error.getSentenceAccuracy() + " (" + error.getNumSentencesCorrect() + " / " + error.getNumSentences() + ")");
    } else {
      PosTaggedSentence tags = trainedModel.tagWords(options.nonOptionArguments());
      System.out.println(tags.getWords());
      System.out.println(tags.getPos());
    }
  }

  public static void main(String[] args) {
    new TestPosCrf().run(args);
  }
  
  private static class PosError {
    private int numTagsCorrect;
    private int numTags;
    
    private int numSentencesCorrect;
    private int numSentences;

    public PosError(int numTagsCorrect, int numTags, int numSentencesCorrect, int numSentences) {
      this.numTagsCorrect = numTagsCorrect;
      this.numTags = numTags;
      this.numSentencesCorrect = numSentencesCorrect;
      this.numSentences = numSentences;
    }
    
    public static PosError zero() {
      return new PosError(0, 0, 0, 0);
    }
    
    public int getNumTagsCorrect() {
      return numTagsCorrect;
    }

    public int getNumTags() {
      return numTags;
    }
    
    public double getTagAccuracy() {
      return ((double) numTagsCorrect) / numTags;
    }

    public int getNumSentencesCorrect() {
      return numSentencesCorrect;
    }

    public int getNumSentences() {
      return numSentences;
    }
    
    public double getSentenceAccuracy() {
      return ((double) numSentencesCorrect) / numSentences;
    }

    public void increment(PosError other) {
      this.numTagsCorrect += other.numTagsCorrect;
      this.numTags += other.numTags;
      this.numSentencesCorrect += other.numSentencesCorrect;
      this.numSentences += other.numSentences;
    }
  }
  
  private static class PosEvaluationMapper extends Mapper<PosTaggedSentence, PosError> {
    
    private final PosTagger tagger;
    
    public PosEvaluationMapper(PosTagger tagger) {
      this.tagger = Preconditions.checkNotNull(tagger);
    }

    @Override
    public PosError map(PosTaggedSentence item) {
      List<String> prediction = tagger.tagWords(item.getWords()).getPos();
      List<String> actual = item.getPos();
      Preconditions.checkState(prediction.size() == actual.size());
      int numTagsCorrect = 0;
      for (int i = 0; i < prediction.size(); i++) {
        if (prediction.get(i).equals(actual.get(i))) {
          numTagsCorrect++;
        }
      }

      System.out.println(item.getWords() + "\n" + prediction + "\n" + actual);
      int numSentencesCorrect = (numTagsCorrect == actual.size()) ? 1 : 0;
      return new PosError(numTagsCorrect, actual.size(), numSentencesCorrect, 1);
    }
  }
  
  private static class PosEvaluationReducer extends SimpleReducer<PosError> {

    @Override
    public PosError getInitialValue() {
      return PosError.zero();
    }

    @Override
    public PosError reduce(PosError item, PosError accumulated) {
      accumulated.increment(item);
      return accumulated;
    }
  }
}