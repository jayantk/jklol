package com.jayantkrish.jklol.ccg.gi;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.ShiftReduceStack;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.CountAccumulator;


public class GroundedParserPipelinedInference extends AbstractGroundedParserInference {
  
  private final CcgInference ccgInference;
  private final ExpressionSimplifier simplifier;
  private final int numLogicalForms;
  private final int evalBeamSize;
  
  // Natural ordering on parses that sorts them by probability.
  private final static Ordering<GroundedCcgParse> parseOrdering = new Ordering<GroundedCcgParse>() {
    public int compare(GroundedCcgParse left, GroundedCcgParse right) {
      return Doubles.compare(left.getSubtreeProbability(), right.getSubtreeProbability());
    }
  };
  
  public GroundedParserPipelinedInference(CcgInference ccgInference, ExpressionSimplifier simplifier,
      int numLogicalForms, int evalBeamSize) {
    this.ccgInference = Preconditions.checkNotNull(ccgInference);
    this.simplifier = simplifier;
    this.numLogicalForms = numLogicalForms;
    this.evalBeamSize = evalBeamSize;
  }
  
  @Override
  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, GroundedParseCost cost,
      LogFunction log) {

    List<CcgParse> ccgParses = ccgInference.beamSearch(parser.getCcgParser(),
        sentence, chartFilter, log);
    
    IncEval eval = parser.getEval();
    IncEvalCost evalCost = cost != null ? new WrapperIncEvalCost(cost) : null;
    
    Multimap<Expression2, CcgParse> lfMap = HashMultimap.create();
    CountAccumulator<Expression2> lfProbs = CountAccumulator.create();
    aggregateParsesByLogicalForm(ccgParses, lfMap, lfProbs, simplifier);
    
    List<Expression2> sortedLfs = lfProbs.getSortedKeys();

    int numEvaluated = 0;
    List<GroundedCcgParse> parses = Lists.newArrayList();
    for (Expression2 lf : sortedLfs) {
      if (numEvaluated == numLogicalForms) {
        break;
      }

      List<IncEvalState> states = eval.evaluateBeam(lf, initialDiagram, evalCost,
          log, evalBeamSize);
      System.out.println("evaluated: " + states.size() + " " + lf);

      if (states.size() > 1) {
        numEvaluated++;
      }

      for (CcgParse ccgParse : lfMap.get(lf)) {
        for (IncEvalState state : states) {
          GroundedCcgParse parse = GroundedCcgParse.fromCcgParse(ccgParse).addDiagram(state.getDiagram())
              .addState(state, ccgParse.getNodeProbability() * state.getProb());
          parses.add(parse);
        }
      }
    }
    
    parses.sort(parseOrdering.reverse());
    return parses;
  }
  
  public static void aggregateParsesByLogicalForm(List<CcgParse> parses, Multimap<Expression2, CcgParse> map,
      CountAccumulator<Expression2> probs, ExpressionSimplifier simplifier) {
    for (CcgParse parse : parses) {
      Expression2 lf = parse.getLogicalForm();
      if (lf != null) {
        lf = simplifier.apply(lf);
      }
      map.put(lf, parse);
      probs.increment(lf, parse.getSubtreeProbability());
    }
  }
  
  private static class WrapperIncEvalCost implements IncEvalCost {
    
    private final GroundedParseCost evalCost;
    
    public WrapperIncEvalCost(GroundedParseCost evalCost) {
      this.evalCost = Preconditions.checkNotNull(evalCost);
    }

    @Override
    public double apply(IncEvalState state) {
      State parserState = new State(ShiftReduceStack.empty(), null, null, state);
      return evalCost.apply(parserState);
    }
  }
}
