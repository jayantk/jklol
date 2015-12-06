package com.jayantkrish.jklol.ccg.lambda2;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.EvalError;
import com.jayantkrish.jklol.lisp.SExpression;

public class AmbEvalEvaluator implements ExpressionEvaluator {
  private final ExpressionParser<SExpression> sexpParser;
  private final AmbEval eval;
  private final Environment env;
  
  public AmbEvalEvaluator(ExpressionParser<SExpression> sexpParser, AmbEval eval, Environment env) {
    this.sexpParser = Preconditions.checkNotNull(sexpParser);
    this.eval = Preconditions.checkNotNull(eval);
    this.env = Preconditions.checkNotNull(env);
  }

  @Override
  public Object evaluate(Expression2 lf) {
    SExpression sexp = sexpParser.parseSingleExpression(lf.toString());
    return eval.eval(sexp, env, null).getValue();
  }
  
  @Override
  public Object evaluateSilentErrors(Expression2 lf, String errorValue) {
    SExpression sexp = sexpParser.parseSingleExpression(lf.toString());
    Object value = null;
    try {
      value = eval.eval(sexp, env, null).getValue();
    } catch (EvalError e) {
      value = errorValue + "(" + e.getMessage() + ")";
    }
    return value;
  }
}
