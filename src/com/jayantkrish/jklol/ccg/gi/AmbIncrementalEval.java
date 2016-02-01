package com.jayantkrish.jklol.ccg.gi;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.gi.GroundedParser.State;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.lisp.ParametricBfgBuilder;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.util.KbestHeap;

public class AmbIncrementalEval implements IncrementalEval {
  
  private final AmbEval eval;
  private final Environment env;
  private final ExpressionSimplifier simplifier;
  
  private final ExpressionParser<SExpression> sexpParser;
  
  public AmbIncrementalEval(AmbEval eval, Environment env, ExpressionSimplifier simplifier) {
    this.eval = Preconditions.checkNotNull(eval);
    this.env = Preconditions.checkNotNull(env);
    this.simplifier = Preconditions.checkNotNull(simplifier);
    
    this.sexpParser = ExpressionParser.sExpression(eval.getSymbolTable());
  }

  @Override
  public void evaluateContinuation(State state, KbestHeap<State> heap, CcgChart chart,
      CcgParser parser) {
    Object continuation = state.continuation;
    Environment continuationEnv = state.continuationEnv;
    Preconditions.checkArgument(continuation instanceof Expression2);
    
    SExpression sexp = sexpParser.parse(continuation.toString());
    ParametricBfgBuilder builder = new ParametricBfgBuilder(true);
    EvalResult evalResult = eval.eval(sexp, continuationEnv, builder);
    Object value = evalResult.getValue();
    
    List<Object> values = Lists.newArrayList();
    List<Double> probs = Lists.newArrayList();
    
    if (value instanceof AmbValue) {
      DiscreteFactor marginals = eval.ambToMarginals((AmbValue) value, builder, false);

      Iterator<Outcome> iter = marginals.outcomeIterator();
      while (iter.hasNext()) {
        Outcome outcome = iter.next();
        values.add(outcome.getAssignment().getOnlyValue());
        probs.add(outcome.getProbability());
      }
    } else {
      values.add(value);
      probs.add(1.0);
    }
    
    // System.out.println("evaluated: " + continuation + " -> ");
    for (int i = 0; i < values.size(); i++) {
      // System.out.println("   " + probs.get(i) + " " + values.get(i));
      IncrementalEval.queueState(values.get(i), null, probs.get(i), state.stack, heap, chart, parser);
    }
  }

  @Override
  public Environment getEnvironment() {
    return Environment.extend(env);
  }

  @Override
  public Object parseToContinuation(GroundedCcgParse parse, Environment env) {
    Expression2 lf = parse.getUnevaluatedLogicalForm(env, eval.getSymbolTable());
    return simplifier.apply(lf);
  }

  @Override
  public boolean isEvaluatable(HeadedSyntacticCategory syntax) {
    return syntax.isAtomic();
  }
}
