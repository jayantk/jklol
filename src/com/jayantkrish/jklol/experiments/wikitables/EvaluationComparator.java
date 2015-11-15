package com.jayantkrish.jklol.experiments.wikitables;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.EvalError;
import com.jayantkrish.jklol.lisp.SExpression;


public class EvaluationComparator implements ExpressionComparator {
  
  private ExpressionSimplifier simplifier;
  
  private ExpressionParser<SExpression> sexpParser;
  private AmbEval eval;
  private Environment env;
    
  public EvaluationComparator(ExpressionSimplifier simplifier,
      ExpressionParser<SExpression> sexpParser, AmbEval eval, Environment env) {
    this.simplifier = Preconditions.checkNotNull(simplifier);
    this.sexpParser = Preconditions.checkNotNull(sexpParser);
    this.eval = Preconditions.checkNotNull(eval);
    this.env = Preconditions.checkNotNull(env);
  }

  @Override
  public boolean equals(Expression2 a, Expression2 b) {
    List<Expression2> subexpressions = b.getSubexpressions();
    String tableId = subexpressions.get(1).getConstant();

    SExpression answerSexp = sexpParser.parseSingleExpression(subexpressions.get(2).toString());
    Object answer = evaluate(answerSexp, "ANS-ERROR");
    
    a = simplifier.apply(a);
    SExpression sexpression = sexpParser.parseSingleExpression(
        "(eval-table \"" + tableId + "\" (quote (get-values " + a.toString() + ")))");
    Object value = evaluate(sexpression, "ERROR");

    /*
    System.out.println(a);
    System.out.println(value);
    System.out.println(answer);
    */

    // TODO: may need more sophisticated comparison logic for
    // numerics and yes/no questions.
    return answer.equals(value);
  }
  
  private Object evaluate(SExpression sexp, String errorVal) {
    Object value = errorVal;
    try {
      value = eval.eval(sexp, env, null).getValue();
    } catch (EvalError e) {
      value = errorVal + "(" + e.getMessage() + ")";
    }
    return value;
  }
}
