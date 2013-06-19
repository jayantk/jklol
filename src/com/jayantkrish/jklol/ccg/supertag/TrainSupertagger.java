package com.jayantkrish.jklol.ccg.supertag;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.FactorGraphSequenceTagger;
import com.jayantkrish.jklol.sequence.ListTaggedSequence;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.TaggedSequence;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainSupertagger extends AbstractCli {
  
  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> modelOutput;

  // Model construction options.
  private OptionSpec<Void> noTransitions;
  private OptionSpec<Void> noUnknownWordFeatures;
  private OptionSpec<Void> maxMargin;
  private OptionSpec<Integer> commonWordCountThreshold;
  
  public TrainSupertagger() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.LBFGS, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
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
    List<CcgExample> ccgExamples = CcgExample.readExamplesFromFile(
        options.valueOf(trainingFilename), true, true);
    List<TaggedSequence<WordAndPos, SyntacticCategory>> trainingData = 
        reformatTrainingExamples(ccgExamples);

    FeatureVectorGenerator<LocalContext<WordAndPos>> featureGen = 
        buildFeatureVectorGenerator(trainingData, options.valueOf(commonWordCountThreshold),
            options.has(noUnknownWordFeatures));

    Set<SyntacticCategory> validCategories = Sets.newHashSet();
    for (TaggedSequence<WordAndPos, SyntacticCategory> trainingDatum : trainingData) {
      validCategories.addAll(trainingDatum.getLabels());
    }

    System.out.println(validCategories.size() + " CCG categories");
    System.out.println(featureGen.getNumberOfFeatures() + " word/CCG category features");

    // Build the factor graph.
    ParametricFactorGraph sequenceModelFamily = TaggerUtils.buildFeaturizedSequenceModel(
        validCategories, featureGen.getFeatureDictionary(), options.has(noTransitions));
    GradientOptimizer trainer = createGradientOptimizer(trainingData.size());
    FactorGraphSequenceTagger<WordAndPos, SyntacticCategory> tagger = TaggerUtils.trainSequenceModel(
        sequenceModelFamily, trainingData, SyntacticCategory.class, featureGen, trainer,
        options.has(maxMargin));

    // Save model to disk.
    System.out.println("Serializing trained model...");
    FactorGraphSupertagger supertagger = new FactorGraphSupertagger(tagger.getModelFamily(), 
        tagger.getParameters(), tagger.getInstantiatedModel(), tagger.getFeatureGenerator());
    IoUtils.serializeObjectToFile(supertagger, options.valueOf(modelOutput));
  }

  private List<TaggedSequence<WordAndPos, SyntacticCategory>> reformatTrainingExamples(
      Collection<CcgExample> ccgExamples) {
    List<TaggedSequence<WordAndPos, SyntacticCategory>> examples = Lists.newArrayList();
    for (CcgExample example : ccgExamples) {
      Preconditions.checkArgument(example.hasSyntacticParse());
      List<WordAndPos> taggedWords = WordAndPos.createExample(example.getWords(), example.getPosTags());
      List<SyntacticCategory> syntacticCategories = example.getSyntacticParse().getAllSpannedLexiconEntries();
      examples.add(new ListTaggedSequence<WordAndPos, SyntacticCategory>(taggedWords, syntacticCategories));
    }
    return examples;
  }

  public static void main(String[] args) {
    new TrainSupertagger().run(args);
  }
}
