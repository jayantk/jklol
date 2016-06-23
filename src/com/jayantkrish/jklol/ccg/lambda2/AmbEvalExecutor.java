package com.jayantkrish.jklol.ccg.lambda2;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.EvalError;
import com.jayantkrish.jklol.lisp.SExpression;

public class AmbEvalExecutor implements ExpressionExecutor {
  private final ExpressionParser<SExpression> sexpParser;
  private final AmbEval eval;
  private final Environment env;
  
  public AmbEvalExecutor(ExpressionParser<SExpression> sexpParser, AmbEval eval, Environment env) {
    this.sexpParser = Preconditions.checkNotNull(sexpParser);
    this.eval = Preconditions.checkNotNull(eval);
    this.env = Preconditions.checkNotNull(env);
  }
  
  protected Expression2 rewriteLf(Expression2 lf, Object context) {
    // Hook for subclasses to implement contextual evaluation.
    return lf;
  }

  @Override
  public Object evaluate(Expression2 lf) {
    return evaluate(lf, null);
  }
  
  @Override
  public Object evaluate(Expression2 lf, Object context) {
    Expression2 newLf = rewriteLf(lf, context);
    SExpression sexp = sexpParser.parse(newLf.toString());
    return eval.eval(sexp, env, null).getValue();
  }
  
  @Override
  public Optional<Object> evaluateSilent(Expression2 lf) {
    return evaluateSilent(lf, null);
  }
  
  @Override
  public Optional<Object> evaluateSilent(Expression2 lf, Object context) {
    try {
      return Optional.of(evaluate(lf, context));
    } catch (EvalError e) {
      return Optional.absent();
    }
  }
}
