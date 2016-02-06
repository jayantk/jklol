package com.jayantkrish.jklol.lisp.inc;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.gi.GroundedCcgParse;
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

public class AmbIncEval extends AbstractIncEval {
  
  private final AmbEval eval;
  private final Environment env;
  private final ExpressionSimplifier simplifier;
  
  private final ExpressionParser<SExpression> sexpParser;
  
  public AmbIncEval(AmbEval eval, Environment env, ExpressionSimplifier simplifier) {
    this.eval = Preconditions.checkNotNull(eval);
    this.env = Preconditions.checkNotNull(env);
    this.simplifier = Preconditions.checkNotNull(simplifier);
    
    this.sexpParser = ExpressionParser.sExpression(eval.getSymbolTable());
  }

  @Override
  public void evaluateContinuation(IncEvalState state, List<IncEvalState> resultQueue) {
    Object continuation = state.getContinuation();
    Environment continuationEnv = state.getEnvironment();
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
      resultQueue.add(new IncEvalState(null, null, values.get(i), null, probs.get(i), null));
    }
  }

  @Override
  public Environment getEnvironment() {
    return Environment.extend(env);
  }

  @Override
  public Object parseToContinuation(GroundedCcgParse parse, Environment env) {
    Expression2 lf = parse.getUnevaluatedLogicalForm(env, eval.getSymbolTable());
    return lfToContinuation(lf, env);
  }
  
  @Override
  public Object lfToContinuation(Expression2 lf, Environment env) {
    return simplifier.apply(lf);
  }

  @Override
  public boolean isEvaluatable(HeadedSyntacticCategory syntax) {
    return syntax.isAtomic();
  }
}
