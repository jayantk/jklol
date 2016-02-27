package com.jayantkrish.jklol.ccg.gi;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.KbestQueue;


public class GroundedParserPipelinedInference extends AbstractGroundedParserInference {
  
  private final CcgInference ccgInference;
  private final int numLogicalForms;
  private final int evalBeamSize;
  
  public GroundedParserPipelinedInference(CcgInference ccgInference,
      int numLogicalForms, int evalBeamSize) {
    this.ccgInference = Preconditions.checkNotNull(ccgInference);
    this.numLogicalForms = numLogicalForms;
    this.evalBeamSize = evalBeamSize;
  }
  
  @Override
  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, GroundedParseCost cost,
      LogFunction log) {

    List<CcgParse> ccgParses = ccgInference.beamSearch(parser.getCcgParser(),
        sentence, chartFilter, log);
    List<CcgParse> parsesToEvaluate = ccgParses.subList(0,
        Math.min(ccgParses.size(), numLogicalForms));
    
    IncEval eval = parser.getEval();
    IncEvalCost evalCost = cost != null ? new WrapperIncEvalCost(cost) : null;
    KbestQueue<GroundedCcgParse> parses = KbestQueue.create(numLogicalForms * evalBeamSize,
        new GroundedCcgParse[0]);
    for (CcgParse ccgParse : parsesToEvaluate) {
      Expression2 lf = ccgParse.getLogicalForm();
      if (lf == null) {
        continue;
      }

      List<IncEvalState> states = eval.evaluateBeam(lf, initialDiagram, evalCost,
          log, evalBeamSize);

      for (IncEvalState state : states) {
        GroundedCcgParse parse = GroundedCcgParse.fromCcgParse(ccgParse).addDiagram(state.getDiagram())
            .addState(state, ccgParse.getNodeProbability() * state.getProb());
        parses.offer(parse, parse.getSubtreeProbability());
      }
    }

    return parses.toSortedList();
  }
  
  private static class WrapperIncEvalCost implements IncEvalCost {
    
    private final GroundedParseCost evalCost;
    
    public WrapperIncEvalCost(GroundedParseCost evalCost) {
      this.evalCost = Preconditions.checkNotNull(evalCost);
    }

    @Override
    public double apply(IncEvalState state) {
      State parserState = new State(null, null, null, state);
      return evalCost.apply(parserState);
    }
  }
}
