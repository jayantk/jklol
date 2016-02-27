package com.jayantkrish.jklol.ccg.gi;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.CountAccumulator;


public class GroundedParserPipelinedInference extends AbstractGroundedParserInference {
  
  private final CcgInference ccgInference;
  private final ExpressionSimplifier simplifier;
  private final int numLogicalForms;
  private final int evalBeamSize;
  
  public GroundedParserPipelinedInference(CcgInference ccgInference,
      ExpressionSimplifier simplifier, int numLogicalForms, int evalBeamSize) {
    this.ccgInference = Preconditions.checkNotNull(ccgInference);
    this.simplifier = Preconditions.checkNotNull(simplifier);
    this.numLogicalForms = numLogicalForms;
    this.evalBeamSize = evalBeamSize;
  }
  
  @Override
  public List<GroundedCcgParse> beamSearch(GroundedParser parser, AnnotatedSentence sentence,
      Object initialDiagram, ChartCost chartFilter, GroundedParseCost evalCost,
      LogFunction log) {

    List<CcgParse> ccgParses = ccgInference.beamSearch(parser.getCcgParser(),
        sentence, chartFilter, log);
    CountAccumulator<Expression2> lfs = SemanticParserUtils.estimateLfProbabilities(ccgParses,
        simplifier);

    List<Expression2> lfsToEvaluate = lfs.getSortedKeys();
    lfsToEvaluate = lfsToEvaluate.subList(0, Math.min(lfsToEvaluate.size(), numLogicalForms));
    IncEval eval = parser.getEval();
    for (Expression2 lf : lfsToEvaluate) {
      // eval.evaluateBeam(lf, initialDiagram, filter, initialEnv, log, beamSize)
    }
   
    return null;
  }
}
