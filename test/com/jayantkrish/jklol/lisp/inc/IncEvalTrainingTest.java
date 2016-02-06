package com.jayantkrish.jklol.lisp.inc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.HashingFeatureVectorGenerator;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IndexedList;

public class IncEvalTrainingTest extends TestCase {
  
  private ExpressionParser<SExpression> sexpParser;
  private ExpressionParser<Expression2> exp2Parser;
  private Environment env;
  private AmbEval ambEval;
  
  private ParametricContinuationIncEval family;
  private ContinuationIncEval eval;

  private static final double TOLERANCE = 0.01;

  private static final String[] predicateDefs = {
    "(define list-k (k . args) (k args))",
    "(define +-k (k x y) (k (+ x y)))",
    "(define map (f l) (if (nil? l) l (cons (f (car l)) (map f (cdr l)))))",
    "(define alist-put (name value l) (if (nil? l) (list (list name value)) (if (= name (car (car l))) (cons (list name value) (cdr l)) (cons (car l) (alist-put name value (cdr l))))))",
    "(define alist-get (name l) (if (nil? l) l (if (= name (car (car l))) (car (cdr (car l))) (alist-get name (cdr l)))))",
    "(define alist-cput (name value l) (let ((old (alist-get name l))) (if (nil? old) (alist-put name value l) l)))",

    "(define get-k (k name) (lambda (world) ((k (alist-get name world)) world)))",
    "(define put-k (k name value) (lambda (world) ((k value) (alist-put name value world))))",
    "(define cput-k (k name value) (lambda (world) (let ((next-world (alist-cput name value world))) ((k (alist-get name next-world)) next-world))))",
    "(define possible-values (list (list \"x\" (list 1 2)) (list \"y\" (list 3 4))))",
  };

  private static final String[] evalDefs = {
    "(define amb-k (k l) (lambda (world) ((queue-k k l) (map (lambda (x) world) l)) ))",
    "(define resolve-k (k name) (lambda (world) (let ((v (alist-get name world))) (if (not (nil? v)) ((k v) world) ((amb-k (lambda (v) (cput-k k name v)) (alist-get name possible-values)) world)))))",
  };
  
  private static final String[] expressions = {
    "(resolve-k \"x\")",
    "(resolve-k \"x\")",
    "(resolve-k \"x\")",
    "(resolve-k \"x\")",
    "(+-k (amb-k (list-k 0 1)) (resolve-k \"x\"))", 
  };

  private static final Object[] labels = {
    2,
    2,
    2,
    1,
    2,
  };
  
  private static final Object initialDiagram = ConsValue.listToConsList(Collections.emptyList());

  public void setUp() {
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    ambEval = new AmbEval(symbolTable);
    env = AmbEval.getDefaultEnvironment(symbolTable);
    sexpParser = ExpressionParser.sExpression(symbolTable);
    exp2Parser = ExpressionParser.expression2();
    SExpression predicateProgram = sexpParser.parse("(begin " + Joiner.on("\n").join(predicateDefs) + ")");
    ambEval.eval(predicateProgram, env, null);
    
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();

    String evalDefString = "(begin " + Joiner.on(" ").join(evalDefs) + ")";
    SExpression defs = sexpParser.parse(evalDefString);
    
    eval = new ContinuationIncEval(ambEval, env, simplifier, defs);
    
    FeatureVectorGenerator<StateFeatures> featureVectorGen =
        new HashingFeatureVectorGenerator<StateFeatures>(100, new StateFeatureGen());
    family = ParametricContinuationIncEval.fromFeatureGenerator(featureVectorGen, eval);
  }
  
  public void testTraining() {
    List<ValueIncEvalExample> examples = parseExamples(expressions, labels);
    
    IncEvalLoglikelihoodOracle oracle = new IncEvalLoglikelihoodOracle(family, 100);
    GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(1000,
        1, 1, true, true, 0.0, new DefaultLogFunction());
    
    // GradientOptimizer trainer = new Lbfgs(100, 10, 0.0, new DefaultLogFunction());

    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    IncEval trainedEval = family.getModelFromParameters(parameters);
    
    for (ValueIncEvalExample example : examples) {
      System.out.println(example.getLogicalForm());
      List<IncEvalState> states = trainedEval.evaluateBeam(example.getLogicalForm(), example.getDiagram(), 100);
      for (IncEvalState state : states) {
        System.out.println("   " + state);
        System.out.println("   " + state.getFeatures());
      }
    }

    assertDistributionEquals(trainedEval, "(resolve-k \"x\")", initialDiagram,
        new Object[] {1, 2}, new double[] {0.25, 0.75});
  }

  private List<ValueIncEvalExample> parseExamples(String[] expressions, Object[] labels) {
    Preconditions.checkArgument(expressions.length == labels.length);
    List<ValueIncEvalExample> examples = Lists.newArrayList();
    for (int i = 0; i < expressions.length; i++) {
      Expression2 lf = exp2Parser.parse(expressions[i]);
      examples.add(new ValueIncEvalExample(lf, initialDiagram, labels[i]));
    }
    return examples;
  }
  
  private void assertDistributionEquals(IncEval eval, String expression,
      Object diagram, Object[] values, double[] probs) {
    Preconditions.checkArgument(values.length == probs.length);

    Expression2 lf = exp2Parser.parse(expression);
    List<IncEvalState> states = eval.evaluateBeam(lf, diagram, 100);
    
    CountAccumulator<Object> valueProbs = CountAccumulator.create();
    for (IncEvalState state : states) {
      valueProbs.increment(state.getDenotation(), state.getProb());
    }
    
    for (int i = 0; i < values.length; i++) {
      assertEquals(probs[i], valueProbs.getProbability(values[i]), TOLERANCE);
    }
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
