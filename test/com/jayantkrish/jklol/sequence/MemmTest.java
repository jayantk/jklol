package com.jayantkrish.jklol.sequence;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.NormalizingFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.Lbfgs;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

public class MemmTest extends TestCase {
  
  private final String[][] trainingInputs = {{"the", "man"}, {"the", "big", "man"}};
  private final String[][] trainingLabels = {{"DT", "NN"}, {"DT", "JJ", "NN"}};

  private List<TaggedSequence<String, String>> trainingSequences;
  private List<Example<DynamicAssignment, DynamicAssignment>> memmData, classifierData;

  private ParametricFactorGraph memmFamily;
  private ParametricFactorGraph normalizedClassifierFamily;
  private ParametricFactorGraph classifierFamily;

  private FeatureVectorGenerator<LocalContext<String>> nullFeatureGen, featureGen;

  private GradientOptimizer optimizer;
  
  private static final String START_POS = "START_POS";

  public void setUp() {
    trainingSequences = parseData(trainingInputs, trainingLabels);
    featureGen = DictionaryFeatureVectorGenerator.createFromData(
        TaggerUtils.extractContextsFromData(trainingSequences), new WordFeatureGenerator(), true);
    IndexedList<String> dummyFeatures = IndexedList.create();
    dummyFeatures.add("DUMMY_FEATURE");
    nullFeatureGen = new DictionaryFeatureVectorGenerator<LocalContext<String>, String>(
        dummyFeatures, new NullFeatureGenerator(), true);
    Set<String> labelSet = Sets.newHashSet(START_POS);
    for (int i = 0; i < trainingLabels.length; i++) {
      labelSet.addAll(Arrays.asList(trainingLabels[i]));
    }
    
    memmFamily = TaggerUtils.buildFeaturizedSequenceModel(labelSet, 
        nullFeatureGen.getFeatureDictionary(), false, true);
    normalizedClassifierFamily = TaggerUtils.buildFeaturizedSequenceModel(labelSet, 
        featureGen.getFeatureDictionary(), true, true);
    classifierFamily = TaggerUtils.buildFeaturizedSequenceModel(labelSet, 
        featureGen.getFeatureDictionary(), true, false);

    memmData = TaggerUtils.reformatTrainingDataPerItem(trainingSequences, nullFeatureGen,
        TaggerUtils.getDefaultInputGenerator(), memmFamily.getVariables(),
        TaggerUtils.DEFAULT_INPUT_VALUE, START_POS);
    classifierData = TaggerUtils.reformatTrainingDataPerItem(trainingSequences, featureGen,
        TaggerUtils.getDefaultInputGenerator(), memmFamily.getVariables(),
        TaggerUtils.DEFAULT_INPUT_VALUE, START_POS);
    
    optimizer = new Lbfgs(10, 20, 0.00001, new DefaultLogFunction(1, false));
  }

  public void testTrainMemm() {
    FactorGraphSequenceTagger<String, String> tagger = TaggerUtils.trainSequenceModel(memmFamily,
        memmData, String.class, nullFeatureGen, TaggerUtils.getDefaultInputGenerator(),
        TaggerUtils.DEFAULT_INPUT_VALUE, START_POS, optimizer, false);

    System.out.println(tagger.getModelFamily().getParameterDescription(
        tagger.getParameters()));
    
    // Only parameters should be conditional probabilities of each label
    // given the previous label.
    NormalizingFactor normalizingFactor = (NormalizingFactor) tagger.getInstantiatedModel()
        .getFactorByName(TaggerUtils.NORMALIZED_FACTOR).getFactor();
    VariableNumMap featureVectorVar = normalizingFactor.getVars().getVariablesByName(TaggerUtils.INPUT_FEATURES_PATTERN);
    VariableNumMap inputVar = normalizingFactor.getVars().getVariablesByName(TaggerUtils.INPUT_PATTERN);
    Assignment a = featureVectorVar.outcomeArrayToAssignment(SparseTensor.empty(new int[] {0}, new int[] {1}))
        .union(inputVar.outcomeArrayToAssignment(TaggerUtils.DEFAULT_INPUT_VALUE));
    Factor transitionFactor = normalizingFactor.conditional(a);
    System.out.println(transitionFactor.getParameterDescription());
    assertEquals(1.0, transitionFactor.getUnnormalizedProbability(START_POS, "DT"), 0.01);
    assertEquals(0.5, transitionFactor.getUnnormalizedProbability("DT", "JJ"), 0.01);
    assertEquals(0.5, transitionFactor.getUnnormalizedProbability("DT", "NN"), 0.01);
    assertEquals(0.25, transitionFactor.getUnnormalizedProbability("NN", "DT"), 0.01);
    assertEquals(0.25, transitionFactor.getUnnormalizedProbability("NN", "JJ"), 0.01);
  }

  public void testTrainClassifier() {
    FactorGraphSequenceTagger<String, String> normalizedTagger = TaggerUtils.trainSequenceModel(normalizedClassifierFamily,
        classifierData, String.class, featureGen, TaggerUtils.getDefaultInputGenerator(),
        TaggerUtils.DEFAULT_INPUT_VALUE, START_POS, optimizer, false);

    FactorGraphSequenceTagger<String, String> tagger = TaggerUtils.trainSequenceModel(classifierFamily,
        classifierData, String.class, featureGen, TaggerUtils.getDefaultInputGenerator(),
        TaggerUtils.DEFAULT_INPUT_VALUE, START_POS, optimizer, false);

    System.out.println(tagger.getModelFamily().getParameterDescription(tagger.getParameters()));
    System.out.println(normalizedTagger.getModelFamily().getParameterDescription(normalizedTagger.getParameters()));
    
    // The normalized and unnormalized classifiers should produce 
    // the exact same parameters. However, the parameters are stored in
    // different data structures.
    SufficientStatistics localParams = tagger.getParameters().coerceToList()
        .getStatisticByName(TaggerUtils.WORD_LABEL_FACTOR);
    SufficientStatistics normalizedParams = normalizedTagger.getParameters()
        .coerceToList().getStatisticByName(TaggerUtils.NORMALIZED_FACTOR)
        .coerceToList().getStatisticByName("1");

    SufficientStatistics deltas = localParams.duplicate();
    deltas.increment(normalizedParams, -1.0);
    assertTrue(deltas.getL2Norm() <= 0.001);
  }

  private static final List<TaggedSequence<String, String>> parseData(String[][] inputs,
      String[][] labels) {
    Preconditions.checkArgument(inputs.length == labels.length);
    
    List<TaggedSequence<String, String>> data = Lists.newArrayList();
    for (int i = 0; i < inputs.length; i++) {
      data.add(new ListTaggedSequence<String, String>(Arrays.asList(inputs[i]),
          Arrays.asList(labels[i])));
    }
    return data;
  }

  private static class WordFeatureGenerator implements FeatureGenerator<LocalContext<String>, String> {
    private static final long serialVersionUID = 1L;

    @Override
    public Map<String, Double> generateFeatures(LocalContext<String> item) {
      Map<String, Double> map = Maps.newHashMap();
      map.put(item.getItem(), 1.0);
      return map;
    }
  }

  private static class NullFeatureGenerator implements FeatureGenerator<LocalContext<String>, String> {
    private static final long serialVersionUID = 1L;
    @Override
    public Map<String, Double> generateFeatures(LocalContext<String> item) {
      Map<String, Double> map = Maps.newHashMap();
      return map;
    }
  }
}
