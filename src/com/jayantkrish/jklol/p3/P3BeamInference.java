package com.jayantkrish.jklol.p3;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.CountAccumulator;


public class P3BeamInference extends AbstractGroundedParserInference {
  
  private final CcgInference ccgInference;
  private final ExpressionSimplifier simplifier;
  private final int numLogicalForms;
  private final int evalBeamSize;
  private final boolean locallyNormalize;
  
  // Natural ordering on parses that sorts them by probability.
  private final static Ordering<P3Parse> parseOrdering = new Ordering<P3Parse>() {
    public int compare(P3Parse left, P3Parse right) {
      return Doubles.compare(left.getSubtreeProbability(), right.getSubtreeProbability());
    }
  };
  
  public P3BeamInference(CcgInference ccgInference, ExpressionSimplifier simplifier,
      int numLogicalForms, int evalBeamSize, boolean locallyNormalize) {
    this.ccgInference = Preconditions.checkNotNull(ccgInference);
    this.simplifier = simplifier;
    this.numLogicalForms = numLogicalForms;
    this.evalBeamSize = evalBeamSize;
    this.locallyNormalize = locallyNormalize;
  }

  @Override
  public List<P3Parse> beamSearch(P3Model parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, IncEvalCost cost,
      LogFunction log) {

    log.startTimer("p3_beam/ccg_parse");
    List<CcgParse> ccgParses = ccgInference.beamSearch(parser.getCcgParser(),
        sentence, chartFilter, log);
    double parsePartitionFunction = 1.0;
    if (locallyNormalize) {
      parsePartitionFunction = 0.0;
      for (CcgParse p : ccgParses) {
        parsePartitionFunction += p.getSubtreeProbability();
      }
    }
    log.stopTimer("p3_beam/ccg_parse");

    IncEval eval = parser.getEval();
    
    log.startTimer("p3_beam/aggregate_lf");
    Multimap<Expression2, CcgParse> lfMap = HashMultimap.create();
    CountAccumulator<Expression2> lfProbs = CountAccumulator.create();
    aggregateParsesByLogicalForm(ccgParses, lfMap, lfProbs, simplifier);
    
    List<Expression2> sortedLfs = lfProbs.getSortedKeys();
    log.stopTimer("p3_beam/aggregate_lf");

    int numEvaluated = 0;
    List<P3Parse> parses = Lists.newArrayList();
    log.startTimer("p3_beam/eval");
    for (Expression2 lf : sortedLfs) {
      if (numEvaluated == numLogicalForms) {
        break;
      }

      List<IncEvalState> states = eval.evaluateBeam(lf, initialDiagram, cost,
          log, evalBeamSize);

      /*
      if (states.size() == 1) {
        for (IncEvalState state : states) {
          System.out.println("  " + state.getDenotation() + " " + state.getDiagram());
        }
      }
      */

      if (states.size() > 1) {
        numEvaluated++;
      }

      double evalPartitionFunction = 1.0;
      if (locallyNormalize) {
        evalPartitionFunction = 0.0;
        for (IncEvalState state : states) {
          evalPartitionFunction += state.getProb();
        }
      }

      for (CcgParse ccgParse : lfMap.get(lf)) {
        for (IncEvalState state : states) {
          P3Parse parse = P3Parse.fromCcgParse(ccgParse).addDiagram(state.getDiagram())
              .addState(state, ccgParse.getNodeProbability() * state.getProb()
                  / (parsePartitionFunction * evalPartitionFunction));
          parses.add(parse);
        }
      }
    }
    log.stopTimer("p3_beam/eval");
    
    log.startTimer("p3_beam/sort");
    parses.sort(parseOrdering.reverse());
    log.stopTimer("p3_beam/sort");
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
}
