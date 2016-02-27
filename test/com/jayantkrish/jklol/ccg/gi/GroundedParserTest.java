package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.inc.AmbIncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.IndexedList;

public abstract class GroundedParserTest extends TestCase {

  private static final String[] lexicon = {
    "1,N{0},1,0 num",
    "2,N{0},2,0 num",
    "3,N{0},3,0 num",
    "4,N{0},4,0 num",
    "+,((N{1}\\N{1}){0}/N{2}){0},(lambda (x y) (+ x y)),0 +",
    "1_or_2,N{0},(amb (list 1 2) (list 2.0 3.0)),0 num",
//    "1_or_2,N{0},1,0 num",
//    "1_or_2,N{0},2,0 num",
  };

  private static final String[] ruleArray = {"DUMMY{0} BLAH{0}"};

  private GroundedParser parser;
  private GroundedParserInference inf;
  
  private static final double TOLERANCE = 1e-6;
  
  public GroundedParserTest(GroundedParserInference inf) {
    this.inf = inf;
  }
  
  public void setUp() {
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon),
        Collections.emptyList(), Arrays.asList(ruleArray),
        new DefaultCcgFeatureFactory(false, false), null, true, null, false);
    CcgParser ccgParser = family.getModelFromParameters(family.getNewSufficientStatistics());
    
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    AmbEval ambEval = new AmbEval(symbolTable);
    Environment env = AmbEval.getDefaultEnvironment(symbolTable);
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();
    
    IncEval eval = new AmbIncEval(ambEval, env, simplifier);
    parser = new GroundedParser(ccgParser, eval);
  }

  public void testParse() {
    List<GroundedCcgParse> parses = beamSearch(parser, Arrays.asList("1_or_2", "+", "2"));

    for (GroundedCcgParse parse : parses) {
      System.out.println(parse.getSubtreeProbability() + " " + parse.getLogicalForm() + " " + parse.getDenotation());
    }
    
    assertEquals(2, parses.size());
    assertEquals(3.0, parses.get(0).getSubtreeProbability(), TOLERANCE);
    assertEquals(4, parses.get(0).getDenotation());
    assertEquals(2.0, parses.get(1).getSubtreeProbability(), TOLERANCE);
    assertEquals(3, parses.get(1).getDenotation());
  }

  public List<GroundedCcgParse> beamSearch(GroundedParser parser, List<String> words) {
    AnnotatedSentence sentence = new AnnotatedSentence(words,
        Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG));
    Object initialDiagram = null;
    // GroundedParserInference inf = new GroundedParserBeamSearchInference(10, -1);
    // GroundedParserInference inf = new GroundedParserPipelinedInference(
    // CcgCkyInference.getDefault(100), 10, 100);
    return inf.beamSearch(parser, sentence, initialDiagram);
  }
  
  public GroundedCcgParse parse(GroundedParser parser, List<String> words) {
    List<GroundedCcgParse> parses = beamSearch(parser, words);

    if (parses.size() > 0) {
      return parses.get(0);
    } else {
      return null;
    }
  }
}
