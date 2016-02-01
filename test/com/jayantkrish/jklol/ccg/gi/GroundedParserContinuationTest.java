package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.IndexedList;

public class GroundedParserContinuationTest extends TestCase {

  private static final String[] lexicon = {
    "1,N{0},1,0 num",
    "2,N{0},2,0 num",
    "3,N{0},3,0 num",
    "4,N{0},4,0 num",
    "+,((N{1}\\N{1}){0}/N{2}){0},(lambda (x y) (+-k x y)),0 +",
    "1_or_2,N{0},(amb-k (list-k 1 2)),0 num",
    "x,N{0},(resolve-k ~\"x~\")",
//    "1_or_2,N{0},1,0 num",
//    "1_or_2,N{0},2,0 num",
  };
  
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

  private static final String[] ruleArray = {"DUMMY{0} BLAH{0}"};

  private GroundedParser parser;
  private ExpressionParser<SExpression> sexpParser;
  private Environment env;
  private AmbEval ambEval;
  
  private static final double TOLERANCE = 1e-6;
  
  public void setUp() {
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon),
        Collections.emptyList(), Arrays.asList(ruleArray),
        new DefaultCcgFeatureFactory(false, false), null, true, null, false);
    CcgParser ccgParser = family.getModelFromParameters(family.getNewSufficientStatistics());
    
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    ambEval = new AmbEval(symbolTable);
    env = AmbEval.getDefaultEnvironment(symbolTable);
    sexpParser = ExpressionParser.sExpression(symbolTable);
    SExpression predicateProgram = sexpParser.parse("(begin " + Joiner.on("\n").join(predicateDefs) + ")");
    ambEval.eval(predicateProgram, env, null);
    
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();
    
    String evalDefString = "(begin " + Joiner.on(" ").join(evalDefs) + ")";
    SExpression defs = sexpParser.parse(evalDefString);
    ContinuationIncrementalEval eval = new ContinuationIncrementalEval(ambEval, env, simplifier, defs);
    parser = new GroundedParser(ccgParser, eval);
  }

  public void testParse() {
    List<GroundedCcgParse> parses = beamSearch(parser,
        Arrays.asList("x", "+", "x"), "(list )");

    for (GroundedCcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse.getLogicalForm()
          + " " + parse.getDenotation() + " " + parse.getDiagram());
    }
    
    assertEquals(2, parses.size());
    assertEquals(1.0, parses.get(0).getSubtreeProbability(), TOLERANCE);
    assertEquals(1.0, parses.get(1).getSubtreeProbability(), TOLERANCE);

    Set<Object> actual = Sets.newHashSet(parses.get(0).getDenotation(), parses.get(1).getDenotation());
    Set<Object> expected = Sets.newHashSet(4, 2);
    assertEquals(expected, actual);
  }

  public List<GroundedCcgParse> beamSearch(GroundedParser parser, List<String> words,
      String initialDiagramExpression) {
    AnnotatedSentence sentence = new AnnotatedSentence(words,
        Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG));

    Object initialDiagram = ambEval.eval(sexpParser.parse(initialDiagramExpression), env, null).getValue();
    return parser.beamSearch(sentence, initialDiagram, 10);
  }
  
  public GroundedCcgParse parse(GroundedParser parser, List<String> words, String initialDiagramExpression) {
    List<GroundedCcgParse> parses = beamSearch(parser, words, initialDiagramExpression);

    if (parses.size() > 0) {
      return parses.get(0);
    } else {
      return null;
    }
  }

  
}
