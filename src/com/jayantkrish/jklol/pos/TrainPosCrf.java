package com.jayantkrish.jklol.pos;

import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.FactorGraphSequenceTagger;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Trains a sequence model for part-of-speech tagging. The model can
 * either be trained as a conditional random field (CRF) or a
 * max-margin markov network (M^3N).
 * 
 * @author jayant
 */
public class TrainPosCrf extends AbstractCli {
  
  private OptionSpec<String> trainingFilename;
  // private OptionSpec<String> allowedTransitions;
  private OptionSpec<String> modelOutput;
  
  // Model construction options.
  private OptionSpec<Void> noTransitions;
  private OptionSpec<Void> noUnknownWordFeatures;
  private OptionSpec<Void> maxMargin;
  private OptionSpec<Integer> commonWordCountThreshold;
  
  public TrainPosCrf() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.LBFGS, CommonOptions.MAP_REDUCE);
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();

    /*
    allowedTransitions = parser.accepts("transitions").withRequiredArg()
        .ofType(String.class);
        */
    
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    
    noTransitions = parser.accepts("noTransitions");
    noUnknownWordFeatures = parser.accepts("noUnknownWordFeatures");
    maxMargin = parser.accepts("maxMargin");
    commonWordCountThreshold = parser.accepts("commonWordThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(5);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the training data as sentences, to use for
    // feature generation.
    List<PosTaggedSentence> trainingData = PosTaggerUtils.readTrainingData(
        options.valueOf(trainingFilename));
    FeatureVectorGenerator<LocalContext<String>> featureGen = PosTaggerUtils
        .buildFeatureVectorGenerator(trainingData, options.valueOf(commonWordCountThreshold),
            options.has(noUnknownWordFeatures));

    Set<String> posTags = Sets.newHashSet();
    for (PosTaggedSentence datum : trainingData) {
      posTags.addAll(datum.getPos());
    }

    System.out.println(posTags.size() + " POS tags");
    System.out.println(featureGen.getNumberOfFeatures() + " word/POS features");
    
    // Build the factor graph.
    ParametricFactorGraph sequenceModelFamily = TaggerUtils.buildFeaturizedSequenceModel(posTags,
        featureGen.getFeatureDictionary(), options.has(noTransitions), false);
    GradientOptimizer trainer = createGradientOptimizer(trainingData.size());
    FactorGraphSequenceTagger<String, String> tagger = TaggerUtils.trainSequenceModel(
        sequenceModelFamily, trainingData, String.class, featureGen, null, null, trainer,
        options.has(maxMargin));

    // Save model to disk.
    System.out.println("Serializing trained model...");
    TrainedPosTagger posTagger = new TrainedPosTagger(tagger.getModelFamily(), 
        tagger.getParameters(), tagger.getInstantiatedModel(), tagger.getFeatureGenerator(), tagger.getInputGenerator());
    IoUtils.serializeObjectToFile(posTagger, options.valueOf(modelOutput));
  }

  public static void main(String[] args) {
    new TrainPosCrf().run(args);
  }
}
