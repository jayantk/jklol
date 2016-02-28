package com.jayantkrish.jklol.ccg.gi;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.parallel.LocalMapReduceExecutor;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.KbestQueue;


public class GroundedParserPipelinedInference extends AbstractGroundedParserInference {
  
  private final CcgInference ccgInference;
  private final int numLogicalForms;
  private final int evalBeamSize;
  private final int numThreads;
  
  public GroundedParserPipelinedInference(CcgInference ccgInference,
      int numLogicalForms, int evalBeamSize, int numThreads) {
    Preconditions.checkArgument(numThreads == 1,
        "A thread safety issue currently does not permit the use of multiple threads.");
    this.ccgInference = Preconditions.checkNotNull(ccgInference);
    this.numLogicalForms = numLogicalForms;
    this.evalBeamSize = evalBeamSize;
    this.numThreads = numThreads;
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
    EvalMapper mapper = new EvalMapper(eval, initialDiagram, evalCost, log, evalBeamSize);
    List<List<GroundedCcgParse>> parseResults = null;
    if (numThreads == 1) {
      parseResults = Lists.newArrayList();
      for (CcgParse parse : ccgParses) {
        parseResults.add(mapper.apply(parse));
      }
    } else {
      MapReduceExecutor executor = new LocalMapReduceExecutor(numThreads, parsesToEvaluate.size());
      parseResults = executor.map(ccgParses, mapper);
    }

    KbestQueue<GroundedCcgParse> parses = KbestQueue.create(numLogicalForms * evalBeamSize,
        new GroundedCcgParse[0]);
    for (List<GroundedCcgParse> results : parseResults) {
      for (GroundedCcgParse parse : results) {
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
  
  private static class EvalMapper extends Mapper<CcgParse, List<GroundedCcgParse>> {
    
    private final IncEval eval;
    private final Object initialDiagram;
    private final IncEvalCost evalCost;
    private final LogFunction log;
    private final int evalBeamSize;

    public EvalMapper(IncEval eval, Object initialDiagram, IncEvalCost evalCost, LogFunction log,
        int evalBeamSize) {
      super();
      this.eval = eval;
      this.initialDiagram = initialDiagram;
      this.evalCost = evalCost;
      this.log = log;
      this.evalBeamSize = evalBeamSize;
    }

    @Override
    public List<GroundedCcgParse> map(CcgParse ccgParse) {
      Expression2 lf = ccgParse.getLogicalForm();
      if (lf == null) {
        return Collections.emptyList();
      }
      
      List<IncEvalState> states = eval.evaluateBeam(lf, initialDiagram, evalCost,
          log, evalBeamSize);

      List<GroundedCcgParse> parses = Lists.newArrayList();
      for (IncEvalState state : states) {
        GroundedCcgParse parse = GroundedCcgParse.fromCcgParse(ccgParse).addDiagram(state.getDiagram())
            .addState(state, ccgParse.getNodeProbability() * state.getProb());
        parses.add(parse);
      }
      return parses;
    }
  }
}
