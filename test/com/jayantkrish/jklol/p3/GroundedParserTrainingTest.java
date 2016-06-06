package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval.SimplifierCpsTransform;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.p3.P3Parse;
import com.jayantkrish.jklol.p3.P3Model;
import com.jayantkrish.jklol.p3.P3Inference;
import com.jayantkrish.jklol.p3.P3LoglikelihoodOracle;
import com.jayantkrish.jklol.p3.P3NormalizedLoglikelihoodOracle;
import com.jayantkrish.jklol.p3.P3BeamInference;
import com.jayantkrish.jklol.p3.ParametricP3Model;
import com.jayantkrish.jklol.p3.ValueGroundedParseExample;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.Lbfgs;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IndexedList;

public class GroundedParserTrainingTest extends TestCase {

    private static final String[] lexicon = {
    "1,NP{0},1,0 num",
    "2,NP{0},2,0 num",
    "3,NP{0},3,0 num",
    "4,NP{0},4,0 num",
    "plus,((NP{1}\\NP{1}){0}/NP{2}){0},(lambda (x y) (+-k x y)),0 plus",
    "plus,((NP{1}\\NP{1}){0}/NP{2}){0},(lambda (x y) (*-k x y)),0 plus",
    "times,((NP{1}\\NP{1}){0}/NP{2}){0},(lambda (x y) (+-k x y)),0 times",
    "times,((NP{1}\\NP{1}){0}/NP{2}){0},(lambda (x y) (*-k x y)),0 times",
    "even,N{0},(rpred-k ~\"even~\"),0 even",
    "odd,N{0},(rpred-k ~\"odd~\"),0 odd",
    "a,NP{0}/N{0},(lambda (values) (amb-k values)),0 a",
    "equals,((S{0}\\NP{1}){0}/NP{2}){0},(lambda (x y) (=-k x y)),0 equals",
    "x,N{0},(resolve-k ~\"x~\")",
    "foo,N{0},(rpred-k ~\"even~\"),0 foo",
    "foo,N{0},3,0 foo",
    "randbool,NP{0},(randbool-k)",
    "and,((NP{1}\\NP{1}){0}/NP{2}){0},(lambda (x y) (and-k x y)),0 and",
    "or,((NP{1}\\NP{1}){0}/NP{2}){0},(lambda (x y) (or-k x y)),0 or",
//    "1_or_2,N{0},1,0 num",
//    "1_or_2,N{0},2,0 num",
  };
  
  private static final String[] predicateDefs = {
    "(define list-k (k . args) (k args))",
    "(define +-k (k x y) (k (+ x y)))",
    "(define *-k (k x y) (k (* x y)))",
    "(define =-k (k x y) (k (= x y)))",
    "(define and-k (k x y) (k (and* x y)))",
    "(define or-k (k x y) (k (or* x y)))",
    "(define quote-k (k x) (k (quote x)))",
    "(define map (f l) (if (nil? l) l (cons (f (car l)) (map f (cdr l)))))",
    "(define select (l1 l2) (if (nil? l1) (list) (let ((rest (select (cdr l1) (cdr l2)))) (if (car l2) (cons (car l1) rest) rest))))",
    "(define reverse (x result) (if (nil? x) result (reverse (cdr x) (cons (car x) result))))",
    "(define alist-put (name value l) (if (nil? l) (list (list name value)) (if (= name (car (car l))) (cons (list name value) (cdr l)) (cons (car l) (alist-put name value (cdr l))))))",
    "(define alist-get (name l) (if (nil? l) l (if (= name (car (car l))) (car (cdr (car l))) (alist-get name (cdr l)))))",
    "(define alist-cput (name value l) (let ((old (alist-get name l))) (if (nil? old) (alist-put name value l) l)))",

    "(define get-k (k name) (lambda (world) ((k (alist-get name world)) world)))",
    "(define put-k (k name value) (lambda (world) ((k value) (alist-put name value world))))",
    "(define cput-k (k name value) (lambda (world) (let ((next-world (alist-cput name value world))) ((k (alist-get name next-world)) next-world))))",
    "(define map-k (k f elts) (map-helper-k (lambda (v) (k (reverse v (list)))) f elts (list)))",
    "(define map-helper-k (k f elts result) (lambda (world) (if (nil? elts) ((k result) world) ((f (lambda (v) (map-helper-k k f (cdr elts) (cons v result))) (car elts)) world))))",
    "(define entities (list 1 2 3 4 5))",
    "(define possible-values (list #t #f))",
  };

  private static final String[] evalDefs = {
    "(define amb-k (k l) (lambda (world) ((queue-k k l) (map (lambda (x) world) l)) ))",
    "(define score-k (k v tag) (lambda (world) ((queue-k k (list v) (list tag)) (list world)) ))",
    "(define resolve-k (k name) (lambda (world) (let ((v (alist-get name world))) (if (not (nil? v)) ((k v) world) ((amb-k (lambda (v) (cput-k k name v)) possible-values) world)))))",
    "(define resolve-predicate-k (k name) (map-k k (lambda (k2 v) (resolve-k k2 (list name v))) entities))",
    "(define rpred-k (k name) (resolve-predicate-k (lambda (v) (k (select entities v))) name))",
    "(define randbool-k (k) (amb-k k (list #t #f)))",
  };
  
  private static final String[] sentences = {
    "1 plus 1 equals 2",
    "a even",
    "a even",
    "foo",
    "randbool and randbool",
  };

  private static final String[] labels = {
    "#t",
    "2",
    "4",
    "3",
    "#t",
  };

  private static final String[] ruleArray = {"DUMMY{0} BLAH{0}"};
  
  private static final Object initialDiagram = ConsValue.listToConsList(Collections.emptyList());

  private ParametricP3Model family;
  
  private ExpressionParser<SExpression> sexpParser;
  private Environment env;
  private AmbEval ambEval;
  
  private static final double TOLERANCE = 1e-3;
  
  public void setUp() {
    ParametricCcgParser ccgFamily = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon),
        Collections.emptyList(), Arrays.asList(ruleArray),
        new DefaultCcgFeatureFactory(false, false), null, true, null, false);
    
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    ambEval = new AmbEval(symbolTable);
    env = AmbEval.getDefaultEnvironment(symbolTable);
    sexpParser = ExpressionParser.sExpression(symbolTable);
    SExpression predicateProgram = sexpParser.parse("(begin " + Joiner.on("\n").join(predicateDefs) + ")");
    ambEval.eval(predicateProgram, env, null);
    
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();
    
    String evalDefString = "(begin " + Joiner.on(" ").join(evalDefs) + ")";
    SExpression defs = sexpParser.parse(evalDefString);
    SimplifierCpsTransform transform = new SimplifierCpsTransform(simplifier, null);
    ContinuationIncEval eval = new ContinuationIncEval(ambEval, env, transform, defs);
    
    FeatureVectorGenerator<StateFeatures> featureVectorGen =
        new DictionaryFeatureVectorGenerator<StateFeatures, String>(StateFeatureGen.getFeatureNames(
            Arrays.asList("even", "odd"), Arrays.asList("1", "2", "3", "4", "5"), Arrays.asList("TRUE", "FALSE")),
            new StateFeatureGen(), false);
    ParametricIncEval evalFamily = ParametricContinuationIncEval.fromFeatureGenerator(
        featureVectorGen, eval);

    family = new ParametricP3Model(ccgFamily, evalFamily);
  }

  public void testTraining() {
    List<ValueGroundedParseExample> examples = parseExamples(sentences, labels);

    // GroundedParserInference inf = new GroundedParserInterleavedInference(20, 3);
    P3Inference inf = new P3BeamInference(
        CcgCkyInference.getDefault(100), ExpressionSimplifier.lambdaCalculus(), 10, 100, false);
    P3LoglikelihoodOracle oracle = new P3LoglikelihoodOracle(family, inf);
    // GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(1000,
    // 1, 1, true, true, Double.MAX_VALUE, 0.0, new DefaultLogFunction());
    GradientOptimizer trainer = new Lbfgs(100, 10, 0.0, new DefaultLogFunction());

    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    P3Model parser = family.getModelFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));
    
    assertDistributionEquals(parser, inf, "foo", new Object[] {3}, new double[] {1.0});
    assertDistributionEquals(parser, inf, "a even", new Object[] {2, 4}, new double[] {0.5, 0.5});
    assertDistributionEquals(parser, inf, "a even plus 1", new Object[] {3, 5}, new double[] {0.5, 0.5});
    assertDistributionEquals(parser, inf, "randbool and randbool",
        new Object[] {ConstantValue.TRUE, ConstantValue.FALSE}, new double[] {0.25, 0.75});
  }
  
  public void testNormalizedTraining() {
    List<ValueGroundedParseExample> examples = parseExamples(sentences, labels);

    // GroundedParserInference inf = new GroundedParserInterleavedInference(20, 3);
    P3Inference inf = new P3BeamInference(
        CcgCkyInference.getDefault(100), ExpressionSimplifier.lambdaCalculus(), 10, 100, true);
    P3NormalizedLoglikelihoodOracle oracle = new P3NormalizedLoglikelihoodOracle(
        family, ExpressionSimplifier.lambdaCalculus(), 100, 100);
    /*
    GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(1000,
        1, 1, true, true, Double.MAX_VALUE, 0.0, new DefaultLogFunction());
        */
    GradientOptimizer trainer = new Lbfgs(100, 10, 0.0, new DefaultLogFunction());

    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    P3Model parser = family.getModelFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));

    assertDistributionEquals(parser, inf, "foo", new Object[] {3}, new double[] {1.0});
    assertDistributionEquals(parser, inf, "a even", new Object[] {2, 4}, new double[] {0.5, 0.5});
    assertDistributionEquals(parser, inf, "a even plus 1", new Object[] {3, 5}, new double[] {0.5, 0.5});
  }
  
  private void assertDistributionEquals(P3Model parser, P3Inference inf,
      String sentence, Object[] values, double[] probs) {
    Preconditions.checkArgument(values.length == probs.length);
    
    AnnotatedSentence annotatedSentence = toSentence(sentence);
    List<P3Parse> parses = inf.beamSearch(parser, annotatedSentence, initialDiagram);
    CountAccumulator<Object> denotationProbs = CountAccumulator.create();
    System.out.println(sentence);
    int numToPrint = 10;
    int numPrinted = 0;
    for (P3Parse parse : parses) {
      if (numPrinted < numToPrint) {
        System.out.println("   " + parse.getDenotation() + " " + parse.getSubtreeProbability()
            + " " + parse.getLogicalForm());
        numPrinted++;
      } 
      denotationProbs.increment(parse.getDenotation(), parse.getSubtreeProbability());
    }

    for (int i = 0; i < values.length; i++) {
      double actual = denotationProbs.getProbability(values[i]);
      assertEquals(probs[i], actual, TOLERANCE);
    }
  }
  
  private List<ValueGroundedParseExample> parseExamples(String[] sentences, String[] labels) {
    Preconditions.checkArgument(sentences.length == labels.length);
    List<ValueGroundedParseExample> examples = Lists.newArrayList();
    for (int i = 0; i < sentences.length; i++) {
      SExpression valueProgram = sexpParser.parse(labels[i]);
      Object denotation = ambEval.eval(valueProgram, env, null).getValue();
      examples.add(new ValueGroundedParseExample(toSentence(sentences[i]), initialDiagram, denotation));
    }
    return examples;
  }
  
  private AnnotatedSentence toSentence(String sentence) {
    List<String> tokens = Arrays.asList(sentence.split(" "));
    List<String> pos = Collections.nCopies(tokens.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    return new AnnotatedSentence(tokens, pos);
  }

  private static class StateFeatureGen implements FeatureGenerator<StateFeatures, String> {
    private static final long serialVersionUID = 1L;
    
    public static IndexedList<String> getFeatureNames(List<String> predicates,
        List<String> entities, List<String> values) {
      IndexedList<String> names = IndexedList.create();
      for (String predicate : predicates) {
        for (String entity : entities) {
          for (String value : values) {
            names.add("(list " + predicate + " " + entity + ")=" + value);
          }
        }
      }
      return names;
    }

    @Override
    public Map<String, Double> generateFeatures(StateFeatures item) {
      Map<String, Double> features = Maps.newHashMap();
      Map<Object, Object> oldBindings = getBindings(item.getPrev().getDiagram());
      Map<Object, Object> newBindings = getBindings(item.getDiagram());

      for (Object newKey : newBindings.keySet()) {
        if (!oldBindings.containsKey(newKey)) {
          features.put(newKey + "=" + newBindings.get(newKey), 1.0);
        }
      }

      /*
      if (item.getOtherArg() != null) {
        // Denotation features.
        features.put("denotation_" + item.getOtherArg() + "=" + item.getDenotation(), 1.0);
      }
      */

      return features;
    }

    private Map<Object, Object> getBindings(Object diagram) {
      Map<Object, Object> bindings = Maps.newHashMap();
      List<Object> bindingList = ConsValue.consListToList(diagram);
      for (Object o : bindingList) {
        List<Object> elt = ConsValue.consListToList(o);
        bindings.put(elt.get(0), elt.get(1));
      }
      return bindings;
    }
  }
}
