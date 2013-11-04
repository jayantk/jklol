package com.jayantkrish.jklol.ccg.supertag;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cli.TrainCcg;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.pos.WordPrefixSuffixFeatureGenerator;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerators;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.ConvertingLocalContext;
import com.jayantkrish.jklol.sequence.FactorGraphSequenceTagger;
import com.jayantkrish.jklol.sequence.ListTaggedSequence;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.TaggedSequence;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.PairCountAccumulator;

/**
 * Trains a CCG supertagger. The supertagger takes as input a
 * POS-tagged sentence, and predicts a sequence of CCG syntactic
 * categories.
 * 
 * @author jayantk
 */
public class TrainSupertagger extends AbstractCli {

  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> modelOutput;
  private OptionSpec<String> syntaxMap;

  // Model construction options.
  private OptionSpec<Void> noTransitions;
  private OptionSpec<Void> locallyNormalized;
  private OptionSpec<Void> maxMargin;
  private OptionSpec<Integer> commonWordCountThreshold;
  private OptionSpec<Integer> labelRestrictionCountThreshold;
  private OptionSpec<Integer> prefixSuffixFeatureCountThreshold; 

  private static final String UNK_PREFIX = "UNK-";

  public TrainSupertagger() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.LBFGS, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();

    syntaxMap = parser.accepts("syntaxMap").withRequiredArg().ofType(String.class);
    noTransitions = parser.accepts("noTransitions");
    locallyNormalized = parser.accepts("locallyNormalized");
    
    maxMargin = parser.accepts("maxMargin");
    commonWordCountThreshold = parser.accepts("commonWordThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(5);
    labelRestrictionCountThreshold = parser.accepts("labelRestrictionThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(20);
    prefixSuffixFeatureCountThreshold = parser.accepts("prefixSuffixThreshold").withRequiredArg()
      .ofType(Integer.class).defaultsTo(10); // 35
  }

  @Override
  public void run(OptionSet options) {
    // Read in the training data as sentences, to use for
    // feature generation.
    System.out.println("Reading training data...");
    List<CcgExample> ccgExamples = TrainCcg.readTrainingData(options.valueOf(trainingFilename),
        true, true, options.valueOf(syntaxMap));
    System.out.println("Reformatting training data...");
    List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> trainingData =
        reformatTrainingExamples(ccgExamples, true);

    System.out.println("Generating features...");
    FeatureVectorGenerator<LocalContext<WordAndPos>> featureGen =
        buildFeatureVectorGenerator(TaggerUtils.extractContextsFromData(trainingData),
            options.valueOf(commonWordCountThreshold), 30,
            options.valueOf(prefixSuffixFeatureCountThreshold));
    System.out.println(featureGen.getNumberOfFeatures() + " word/CCG category features");

    System.out.println("Generating label restrictions...");
    TableFactor labelRestrictions = getLabelRestrictions(trainingData, options.valueOf(labelRestrictionCountThreshold));
    
    DiscreteVariable inputVariable = (DiscreteVariable) labelRestrictions.getVars().getVariable(0);
    DiscreteVariable labelVariable = (DiscreteVariable) labelRestrictions.getVars().getVariable(1);
    ParametricFactorGraph sequenceModelFamily = TaggerUtils.buildFeaturizedSequenceModel(
      inputVariable, labelVariable, featureGen.getFeatureDictionary(), labelRestrictions.getWeights(),
      options.has(noTransitions), options.has(locallyNormalized));
    GradientOptimizer trainer = createGradientOptimizer(trainingData.size());
    Function<LocalContext<WordAndPos>, String> inputGen = new WordAndPosToInput(inputVariable);

    // Reformat the training examples to be suitable for training
    // a factor graph.
    System.out.println("Reformatting training data...");
    WordAndPos startInput = null;
    HeadedSyntacticCategory startLabel = null;
    if (options.has(locallyNormalized)) {
      startInput = new WordAndPos("<START>", "<START>");
      startLabel = HeadedSyntacticCategory.parseFrom("START{0}");
    }

    List<Example<DynamicAssignment, DynamicAssignment>> examples = null;
    if (options.has(locallyNormalized)) {
      examples = TaggerUtils.reformatTrainingDataPerItem(trainingData, featureGen, inputGen,
          sequenceModelFamily.getVariables(), startInput, startLabel);
    } else {
      examples = TaggerUtils.reformatTrainingData(trainingData, featureGen, inputGen,
          sequenceModelFamily.getVariables(), startInput, startLabel);
    }
    FactorGraphSequenceTagger<WordAndPos, HeadedSyntacticCategory> tagger = TaggerUtils.trainSequenceModel(
        sequenceModelFamily, examples, HeadedSyntacticCategory.class, featureGen, inputGen, startInput,
        startLabel, trainer, options.has(maxMargin));

    // Save model to disk.
    System.out.println("Serializing trained model...");
    FactorGraphSupertagger supertagger = new FactorGraphSupertagger(tagger.getModelFamily(),
        tagger.getParameters(), tagger.getInstantiatedModel(), tagger.getFeatureGenerator(),
        tagger.getInputGenerator(), tagger.getStartInput(), tagger.getStartLabel());
    IoUtils.serializeObjectToFile(supertagger, options.valueOf(modelOutput));
  }

  /**
   * Converts {@code ccgExamples} into word sequences tagged with
   * syntactic categories.
   * 
   * @param ccgExamples
   * @param ignoreInvalid
   * @param addStartSymbol
   * @return
   */
  public static List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> reformatTrainingExamples(
      Collection<CcgExample> ccgExamples, boolean ignoreInvalid) {
    List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> examples = Lists.newArrayList();
    for (CcgExample example : ccgExamples) {
      Preconditions.checkArgument(example.hasSyntacticParse());
      List<WordAndPos> taggedWords = WordAndPos.createExample(example.getWords(), example.getPosTags());
      List<HeadedSyntacticCategory> syntacticCategories = example.getSyntacticParse().getAllSpannedHeadedSyntacticCategories();

      if (!ignoreInvalid || !syntacticCategories.contains(null)) {
        examples.add(new ListTaggedSequence<WordAndPos, HeadedSyntacticCategory>(taggedWords, syntacticCategories));
      } else {
        List<SyntacticCategory> unheadedCategories = example.getSyntacticParse().getAllSpannedLexiconEntries();
        System.out.println("Discarding sentence: " + taggedWords);
        for (int i = 0; i < taggedWords.size(); i++) {
          if (syntacticCategories.get(i) == null) {
            System.out.println("No headed syntactic category for: " + taggedWords.get(i) + " " + unheadedCategories.get(i));
          }
        }
      }
    }
    return examples;
  }

  public static FeatureVectorGenerator<LocalContext<WordAndPos>> buildFeatureVectorGenerator(
      List<LocalContext<WordAndPos>> contexts, int commonWordCountThreshold, int wordPosCountThreshold,
      int prefixSuffixCountThreshold) {
    CountAccumulator<String> wordCounts = CountAccumulator.create();
    for (LocalContext<WordAndPos> context : contexts) {
      wordCounts.increment(context.getItem().getWord(), 1.0);
    }
    Set<String> commonWords = Sets.newHashSet(wordCounts.getKeysAboveCountThreshold(
        commonWordCountThreshold));

    // Build a dictionary of words and POS tags which occur frequently
    // enough in the data set.
    FeatureGenerator<LocalContext<WordAndPos>, String> wordGen = new
        WordAndPosContextFeatureGenerator(new int[] { -2, -1, 0, 1, 2 }, commonWords);
    CountAccumulator<String> wordPosFeatureCounts = FeatureGenerators.getFeatureCounts(wordGen, contexts);

    // Generate prefix/suffix features for common prefixes and suffixes.
    FeatureGenerator<LocalContext<WordAndPos>, String> prefixGen = 
      FeatureGenerators.convertingFeatureGenerator(new WordPrefixSuffixFeatureGenerator(1, 1, 2, 5, commonWords),
                                                   new WordAndPosContextToWordContext());

    // Count feature occurrences and discard infrequent features.
    CountAccumulator<String> prefixFeatureCounts = FeatureGenerators.getFeatureCounts(prefixGen, contexts);
    IndexedList<String> featureDictionary = IndexedList.create();
    Set<String> frequentWordFeatures = wordPosFeatureCounts.getKeysAboveCountThreshold(wordPosCountThreshold);
    Set<String> frequentPrefixFeatures = prefixFeatureCounts.getKeysAboveCountThreshold(prefixSuffixCountThreshold);
    featureDictionary.addAll(frequentWordFeatures);
    featureDictionary.addAll(frequentPrefixFeatures);

    System.out.println(frequentWordFeatures.size() + " word and POS features");
    System.out.println(frequentPrefixFeatures.size() + " prefix/suffix features");

    @SuppressWarnings("unchecked")
    FeatureGenerator<LocalContext<WordAndPos>, String> featureGen = FeatureGenerators
          .combinedFeatureGenerator(wordGen, prefixGen);

    return new DictionaryFeatureVectorGenerator<LocalContext<WordAndPos>, String>(
        featureDictionary, featureGen, true);
  }
  
  private static TableFactor getLabelRestrictions(List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> trainingData,
      int minWordCount) {
    PairCountAccumulator<String, HeadedSyntacticCategory> wordCategoryCounts = PairCountAccumulator.create();
    PairCountAccumulator<String, HeadedSyntacticCategory> posCategoryCounts = PairCountAccumulator.create();
    Set<HeadedSyntacticCategory> validCategories = Sets.newHashSet();

    // Count cooccurrences between words/POS-tags and their labels.
    for (TaggedSequence<WordAndPos, HeadedSyntacticCategory> seq : trainingData) {
      List<WordAndPos> items = seq.getItems();
      List<HeadedSyntacticCategory> labels = seq.getLabels();
      for (int i = 0; i < items.size(); i++) {
        wordCategoryCounts.incrementOutcome(items.get(i).getWord(), labels.get(i), 1.0);
        posCategoryCounts.incrementOutcome(items.get(i).getPos(), labels.get(i), 1.0);
      }
      
      validCategories.addAll(labels);
    }
    System.out.println(validCategories.size() + " CCG categories");
    
    Set<String> inputSet = Sets.newHashSet();
    for (String word : wordCategoryCounts.keySet()) {
      if (wordCategoryCounts.getTotalCount(word) >= minWordCount) {
        inputSet.add(word);
      }
    }
    System.out.println(inputSet.size() + " words with count >= " + minWordCount);
    for (String pos : posCategoryCounts.keySet()) {
      inputSet.add(UNK_PREFIX + pos);
    }
    
    DiscreteVariable inputVariable = new DiscreteVariable("input", inputSet);
    DiscreteVariable labelVariable = new DiscreteVariable("labels", validCategories);
    VariableNumMap inputLabelVars = new VariableNumMap(Ints.asList(0, 1),
        Lists.newArrayList("input", "label"), Lists.newArrayList(inputVariable, labelVariable));
    TableFactorBuilder builder = new TableFactorBuilder(inputLabelVars, SparseTensorBuilder.getFactory());
    for (String word : wordCategoryCounts.keySet()) {
      if (wordCategoryCounts.getTotalCount(word) >= minWordCount) {
        for (HeadedSyntacticCategory cat : wordCategoryCounts.getValues(word)) {
          builder.setWeight(1.0, word, cat);
        }
      }
    }

    for (String pos : posCategoryCounts.keySet()) {
      for (HeadedSyntacticCategory cat : posCategoryCounts.getValues(pos)) {
        builder.setWeight(1.0, UNK_PREFIX + pos, cat);
      }
    }
    return builder.build();
  }

  public static void main(String[] args) {
    new TrainSupertagger().run(args);
  }

  private static class WordAndPosToWord implements Function<WordAndPos, String>, Serializable {
    private static final long serialVersionUID = 1L;
    
    @Override
    public String apply(WordAndPos wordAndPos) {
      return wordAndPos.getWord();
    }
  }
  
  private static class WordAndPosToInput implements Function<LocalContext<WordAndPos>, String>, Serializable {
    private static final long serialVersionUID = 1L;
    private final DiscreteVariable inputVar;
    
    public WordAndPosToInput(DiscreteVariable inputVar) {
      this.inputVar = Preconditions.checkNotNull(inputVar);
    }

    @Override
    public String apply(LocalContext<WordAndPos> wordAndPos) {
      if (inputVar.canTakeValue(wordAndPos.getItem().getWord())) {
        return wordAndPos.getItem().getWord();
      } else {
        return UNK_PREFIX + wordAndPos.getItem().getPos();
      }
    }
  }

  private static class WordAndPosContextToWordContext implements Function<LocalContext<WordAndPos>, LocalContext<String>>, Serializable {
    private static final long serialVersionUID = 1L;
    private final Function<WordAndPos, String> wordPosConverter;

    public WordAndPosContextToWordContext() {
      this.wordPosConverter = new WordAndPosToWord();
    }

    @Override
    public LocalContext<String> apply(LocalContext<WordAndPos> context) {
      return new ConvertingLocalContext<WordAndPos, String>(context, wordPosConverter);
    }
  }
}
