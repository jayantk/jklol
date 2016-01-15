package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.data.CcgExampleFormat;
import com.jayantkrish.jklol.ccg.data.CcgSyntaxTreeFormat;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lexicon.SpanFeatureAnnotation;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

public class FeaturizedLexiconScorerTest extends TestCase {
  
  private static final String[] lexicon = {
      "block,N{0},(lambda (x) (pred:block x)),0 pred:block",
      "block,NP{0},(lambda (x) (pred:object x)),0 pred:object",
      "foo,ABC{0},(lambda (x) (pred:object x)),0 pred:object",
      "bar,ABCD{0},(lambda (x) (pred:object x)),0 pred:object",
  };
  
  private static final String[] unknownLexicon = {};
  private static final String[] ruleArray = {"ABC{0} ABCD{0}", "ABC{0} ABC{0} ABCD{0}"};

  private static final String[] trainingData = {
      "red block#########(lambda (x) (pred:block x))",
      "block red#########(lambda (x) (pred:object x))",
      "block block red#########(lambda (x) (pred:object x))",
      "block block red#########(lambda (x) (pred:object x))",
  };
  
  private ParametricCcgParser family;
  private List<CcgExample> examples;

  public void setUp() {
    CcgExampleFormat exampleReader = new CcgExampleFormat(new CcgSyntaxTreeFormat(), false);
    
    examples = Lists.newArrayList();
    for (int i = 0; i < trainingData.length; i++) {
      CcgExample example = exampleReader.parseFrom(trainingData[i]);
      examples.add(new CcgExample(example.getSentence(), null, null, example.getLogicalForm()));
    }
    List<StringContext> contexts = StringContext.getContextsFromExamples(examples);
    
    Set<String> posTags = Sets.newHashSet(ParametricCcgParser.DEFAULT_POS_TAG);
    FeatureGenerator<StringContext, String> featureGen = new FeatureGenerator<StringContext, String>() {
      private static final long serialVersionUID = 1L;

      @Override
      public Map<String, Double> generateFeatures(StringContext item) {
        String featureName = Integer.toString(item.getSpanStart());
        Map<String, Double> features = Maps.newHashMap();
        features.put(featureName, 1.0);
        features.put(item.getPos().get(item.getSpanStart()), 1.0);
        return features;
      }
    };
    FeatureVectorGenerator<StringContext> featureVectorGen = DictionaryFeatureVectorGenerator
        .createFromData(contexts, featureGen, true);
    System.out.println(featureVectorGen.getFeatureDictionary().getValues());
    
    List<CcgExample> featurizedExamples = Lists.newArrayList();
    for (CcgExample example : examples) {
      SpanFeatureAnnotation annotation = SpanFeatureAnnotation.annotate(example.getSentence(), featureVectorGen);
      AnnotatedSentence newSentence = example.getSentence().addAnnotation("features", annotation);
      featurizedExamples.add(new CcgExample(newSentence, example.getDependencies(),
          example.getSyntacticParse(), example.getLogicalForm()));
    }
    examples = featurizedExamples;
    
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon),
        Arrays.asList(unknownLexicon), Arrays.asList(ruleArray),
        new DefaultCcgFeatureFactory("features", featureVectorGen.getFeatureDictionary(), true, true, null),
        posTags, true, null, false);
  }

  public void testTraining() {
    ExpressionComparator comparator = new SimplificationComparator(ExpressionSimplifier.lambdaCalculus());
    CcgInference inferenceAlg = CcgCkyInference.getDefault(100);

    CcgPerceptronOracle oracle = new CcgPerceptronOracle(family, comparator, inferenceAlg, 0.0);
    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithL2Regularization(10,
        1, 1, false, true, 0.0, new DefaultLogFunction());

    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    CcgParser parser = family.getModelFromParameters(parameters);
    
    for (CcgExample example : examples) {
      CcgParse parse = inferenceAlg.getBestParse(parser, example.getSentence(), null, null);
      System.out.println(example.getSentence());
      System.out.println(parse);
    }

    System.out.println(family.getParameterDescription(parameters));
  }
}
