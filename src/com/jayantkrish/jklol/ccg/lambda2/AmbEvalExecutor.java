package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
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
  
  private Object evaluate(Expression2 lf, Object context, Environment environment) {
    Expression2 newLf = rewriteLf(lf, context);
    SExpression sexp = sexpParser.parse(newLf.toString());
    return eval.eval(sexp, environment, null).getValue();
  }

  @Override
  public Object evaluate(Expression2 lf) {
    return evaluate(lf, null);
  }
  
  @Override
  public Object evaluate(Expression2 lf, Object context) {
    return evaluate(lf, context, env);
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

  @Override
  public Object apply(Expression2 funcLf, List<Object> args) {
    return apply(funcLf, null, args);
  }

  @Override
  public Object apply(Expression2 funcLf, Object context, List<Object> args) {
    // XXX: mutating the global environment is ugly,
    // but necessary if the evaluated expression uses (quote)
    Environment newEnv = env;
    // Environment newEnv = Environment.extend(env);

    List<Expression2> applicationElts = Lists.newArrayList();
    applicationElts.add(funcLf);
    for (int i = 0; i < args.size(); i++) {
      // TODO: guarantee unique var names.
      String varName = "apply-arg-" + i;
      newEnv.bindName(varName, args.get(i), eval.getSymbolTable());
      applicationElts.add(Expression2.constant(varName));
    }
    Expression2 lf = Expression2.nested(applicationElts);
    return evaluate(lf, context, newEnv);
  }
  
  @Override
  public Optional<Object> applySilent(Expression2 funcLf, List<Object> args) {
    return applySilent(funcLf, null, args);
  }
  
  @Override
  public Optional<Object> applySilent(Expression2 lf, Object context, List<Object> args) {
    try {
      return Optional.of(apply(lf, context, args));
    } catch (EvalError e) {
      return Optional.absent();
    }
  }
}
