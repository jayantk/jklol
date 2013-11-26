package com.jayantkrish.jklol.ccg.supertag;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
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
  private OptionSpec<Integer> posContextFeatureCountThreshold;
  private OptionSpec<Integer> prefixSuffixFeatureCountThreshold;
  
  private OptionSpec<String> wordEmbeddingFeatures;
  private OptionSpec<Void> usePosWithEmbedding;

  private static final String UNK_PREFIX = "UNK-";
  private static final String EMBEDDING_UNKNOWN_WORD = "*UNKNOWN*";

  public TrainSupertagger() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.LBFGS, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    syntaxMap = parser.accepts("syntaxMap").withRequiredArg().ofType(String.class).required();
    
    noTransitions = parser.accepts("noTransitions");
    locallyNormalized = parser.accepts("locallyNormalized");
    
    maxMargin = parser.accepts("maxMargin");
    commonWordCountThreshold = parser.accepts("commonWordThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(0); // old value: 5 
    labelRestrictionCountThreshold = parser.accepts("labelRestrictionThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(Integer.MAX_VALUE); // old value: 20
    posContextFeatureCountThreshold = parser.accepts("posContextFeatureCountThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(0); // old value: 30
    prefixSuffixFeatureCountThreshold = parser.accepts("prefixSuffixThreshold").withRequiredArg()
      .ofType(Integer.class).defaultsTo(0); // old values: 10, 35

    wordEmbeddingFeatures = parser.accepts("wordEmbeddingFeatures").withRequiredArg().ofType(String.class);
    usePosWithEmbedding = parser.accepts("usePosWithEmbedding");
  }

  @Override
  public void run(OptionSet options) {
    // Read in the training data as sentences, to use for
    // feature generation.
    System.out.println("Reading training data...");
    List<CcgExample<SupertaggedSentence>> ccgExamples = TrainCcg.readTrainingData(
        options.valueOf(trainingFilename), true, true, options.valueOf(syntaxMap));
    System.out.println("Reformatting training data...");
    List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> trainingData =
        reformatTrainingExamples(ccgExamples, true);
    
    Map<String, Tensor> wordEmbeddings = null;
    if (options.has(wordEmbeddingFeatures)) {
      wordEmbeddings = readWordVectors(options.valueOf(wordEmbeddingFeatures));
    }

    System.out.println("Generating features...");
    FeatureVectorGenerator<LocalContext<WordAndPos>> featureGen =
        buildFeatureVectorGenerator(TaggerUtils.extractContextsFromData(trainingData), wordEmbeddings,
            options.valueOf(commonWordCountThreshold), options.valueOf(posContextFeatureCountThreshold),
                                    options.valueOf(prefixSuffixFeatureCountThreshold), options.has(usePosWithEmbedding));
    System.out.println(featureGen.getNumberOfFeatures() + " features per CCG category.");

    System.out.println("Generating label restrictions...");
    WordAndPos startInput = null;
    HeadedSyntacticCategory startLabel = null;
    if (options.has(locallyNormalized)) {
      startInput = new WordAndPos("<START>", "<START>");
      startLabel = HeadedSyntacticCategory.parseFrom("START{0}");
    }
    TableFactor labelRestrictions = getLabelRestrictions(trainingData, options.valueOf(labelRestrictionCountThreshold),
                                                         startInput, startLabel);
    
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
        tagger.getInputGenerator(), tagger.getMaxMarginalCalculator(), tagger.getMarginalCalculator(),
        tagger.getStartInput(), tagger.getStartLabel());
    IoUtils.serializeObjectToFile(supertagger, options.valueOf(modelOutput));
  }

  private static Map<String, Tensor> readWordVectors(String filename) {
    Map<String, Tensor> tensorMap = Maps.newHashMap();
    int[] dims = new int[] {0};
    int[] sizes = null;
    for (String line : IoUtils.readLines(filename)) {
      String[] parts = line.split("\\s");
      String word = parts[0];
      double[] values = new double[parts.length - 1];
      if (sizes == null) {
        sizes = new int[] {parts.length - 1};
      }

      for (int i = 1; i < parts.length; i++) {
        values[i - 1] = Double.parseDouble(parts[i]);
      }
      
      Tensor tensor = new DenseTensor(dims, sizes, values);
      tensorMap.put(word, tensor);
    }
    return tensorMap;
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
      Collection<CcgExample<SupertaggedSentence>> ccgExamples, boolean ignoreInvalid) {
    List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> examples = Lists.newArrayList();
    for (CcgExample<SupertaggedSentence> example : ccgExamples) {
      Preconditions.checkArgument(example.hasSyntacticParse());
      List<WordAndPos> taggedWords = WordAndPos.createExample(example.getSentence().getWords(),
          example.getSentence().getPosTags());
      List<HeadedSyntacticCategory> syntacticCategories = example.getSyntacticParse()
          .getAllSpannedHeadedSyntacticCategories();

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
      List<LocalContext<WordAndPos>> contexts, Map<String, Tensor> wordEmbeddings, 
      int commonWordCountThreshold, int posContextCountThreshold, int prefixSuffixCountThreshold,
      boolean usePosWithEmbedding) {
    CountAccumulator<String> wordCounts = CountAccumulator.create();
    for (LocalContext<WordAndPos> context : contexts) {
      wordCounts.increment(context.getItem().getWord(), 1.0);
    }
    Set<String> commonWords = Sets.newHashSet(wordCounts.getKeysAboveCountThreshold(
        commonWordCountThreshold));

    // Build a dictionary of words and POS tags which occur frequently
    // enough in the data set.
    FeatureGenerator<LocalContext<WordAndPos>, String> wordGen = new
        WordAndPosFeatureGenerator(new int[] { -2, -1, 0, 1, 2 }, commonWords);
    CountAccumulator<String> wordPosFeatureCounts = FeatureGenerators.getFeatureCounts(wordGen, contexts);
    
    FeatureGenerator<LocalContext<WordAndPos>, String> posContextGen = new 
        PosContextFeatureGenerator(new int[][] {{-2}, {-1}, {0}, {1}, {2}, {-1, 0}, {0, 1}, 
            {-2, -1, 0}, {-1, 0, 1}, {0, 1, 2}, {-2, 0}, {-1, 1}, {0, 2}});
    CountAccumulator<String> posContextFeatureCounts = FeatureGenerators.getFeatureCounts(posContextGen, contexts);

    // Generate prefix/suffix features for common prefixes and suffixes.
    FeatureGenerator<LocalContext<WordAndPos>, String> prefixGen = 
      FeatureGenerators.convertingFeatureGenerator(new WordPrefixSuffixFeatureGenerator(1, 1, 2, 5, commonWords),
                                                   new WordAndPosContextToWordContext());

    // Count feature occurrences and discard infrequent features.
    CountAccumulator<String> prefixFeatureCounts = FeatureGenerators.getFeatureCounts(prefixGen, contexts);
    IndexedList<String> featureDictionary = IndexedList.create();
    List<FeatureGenerator<LocalContext<WordAndPos>, ? extends String>> featureGenerators = Lists.newArrayList();
    featureGenerators.add(wordGen);
    featureGenerators.add(posContextGen);
    featureGenerators.add(prefixGen);
    
    Set<String> frequentWordFeatures = wordPosFeatureCounts.getKeysAboveCountThreshold(commonWordCountThreshold - 1);
    Set<String> frequentContextFeatures = posContextFeatureCounts.getKeysAboveCountThreshold(posContextCountThreshold - 1);
    Set<String> frequentPrefixFeatures = prefixFeatureCounts.getKeysAboveCountThreshold(prefixSuffixCountThreshold - 1);
    featureDictionary.addAll(frequentWordFeatures);
    featureDictionary.addAll(frequentContextFeatures);
    featureDictionary.addAll(frequentPrefixFeatures);

    System.out.println(frequentWordFeatures.size() + " word and POS features");
    System.out.println(frequentContextFeatures.size() + " POS context features");
    System.out.println(frequentPrefixFeatures.size() + " prefix/suffix features");
    
    if (wordEmbeddings != null) {
      EmbeddingFeatureGenerator embeddingFeatureGenerator = new EmbeddingFeatureGenerator(
        wordEmbeddings, EMBEDDING_UNKNOWN_WORD, usePosWithEmbedding);
      featureGenerators.add(embeddingFeatureGenerator);
      CountAccumulator<String> embeddingFeatureCounts = FeatureGenerators.getFeatureCounts(embeddingFeatureGenerator, contexts);

      Set<String> embeddingFeatures = embeddingFeatureCounts.keySet();
      System.out.println(embeddingFeatures.size() + " word embedding features");
      featureDictionary.addAll(embeddingFeatures);
    }

    FeatureGenerator<LocalContext<WordAndPos>, String> featureGen = FeatureGenerators
      .<LocalContext<WordAndPos>, String>combinedFeatureGenerator(featureGenerators);

    return new DictionaryFeatureVectorGenerator<LocalContext<WordAndPos>, String>(
        featureDictionary, featureGen, true);
  }
  
  private static TableFactor getLabelRestrictions(List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> trainingData,
                                                  int minWordCount, WordAndPos startInput, HeadedSyntacticCategory startLabel) {
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
    if (startLabel != null) {
      validCategories.add(startLabel);
    }
    System.out.println(validCategories.size() + " CCG categories");
    
    Set<String> inputSet = Sets.newHashSet();
    for (String word : wordCategoryCounts.keySet()) {
      if (wordCategoryCounts.getTotalCount(word) >= minWordCount) {
        inputSet.add(word);
      }
    }
    if (startInput != null) {
      inputSet.add(startInput.getWord());
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

    if (startInput != null) {
      builder.setWeight(1.0, startInput.getWord(), startLabel);
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
