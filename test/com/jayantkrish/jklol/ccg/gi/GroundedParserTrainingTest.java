package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.HashingFeatureVectorGenerator;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
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
//    "1_or_2,N{0},1,0 num",
//    "1_or_2,N{0},2,0 num",
  };
  
  private static final String[] predicateDefs = {
    "(define list-k (k . args) (k args))",
    "(define +-k (k x y) (k (+ x y)))",
    "(define *-k (k x y) (k (* x y)))",
    "(define =-k (k x y) (k (= x y)))",
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
  };
  
  private static final String[] sentences = {
    "a even",
    "a even",
    "1 times 1 equals 1",
    "2 plus 1 equals a odd",
    "a even plus 1 equals a odd",
  };

  private static final String[] labels = {
    "2",
    "4",
    "#t",
    "#t",
    "#t",
  };

  private static final String[] ruleArray = {"DUMMY{0} BLAH{0}"};
  
  private static final Object initialDiagram = ConsValue.listToConsList(Collections.emptyList());

  private ParametricGroundedParser family;
  
  private ExpressionParser<SExpression> sexpParser;
  private Environment env;
  private AmbEval ambEval;
  
  private static final double TOLERANCE = 1e-6;
  
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
    ContinuationIncEval eval = new ContinuationIncEval(ambEval, env, simplifier, defs);
    
    FeatureVectorGenerator<StateFeatures> featureVectorGen =
        new HashingFeatureVectorGenerator<StateFeatures>(100, new StateFeatureGen());
    ParametricIncEval evalFamily = ParametricContinuationIncEval.fromFeatureGenerator(
        featureVectorGen, eval);

    family = new ParametricGroundedParser(ccgFamily, evalFamily);
  }

  public void testTraining() {
    List<ValueGroundedParseExample> examples = parseExamples(sentences, labels);

    GroundedParserLoglikelihoodOracle oracle = new GroundedParserLoglikelihoodOracle(family, 100);
    GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(100,
        1, 1, true, true, 0.0, new DefaultLogFunction());
    // GradientOptimizer trainer = new Lbfgs(100, 10, 0.0, new DefaultLogFunction());

    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    GroundedParser parser = family.getModelFromParameters(parameters);
    System.out.println(family.getParameterDescription(parameters));

    List<GroundedCcgParse> parses = parser.beamSearch(toSentence("odd"), initialDiagram, 100);
    for (GroundedCcgParse parse : parses) {
      System.out.println(parse.getDenotation() + " " + parse.getDiagram() + " " + parse.getLogicalForm());
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

      if (item.getOtherArg() != null) {
        // Denotation features.
        features.put("denotation_" + item.getOtherArg() + "=" + item.getDenotation(), 1.0);
      }

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
