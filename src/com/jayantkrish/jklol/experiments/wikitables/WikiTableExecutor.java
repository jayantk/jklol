package com.jayantkrish.jklol.experiments.wikitables;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.AmbEvalExecutor;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.SExpression;

public class WikiTableExecutor extends AmbEvalExecutor {
  
  public WikiTableExecutor(ExpressionParser<SExpression> sexpParser, AmbEval eval,
      Environment env) {
    super(sexpParser, eval, env);
  }

  @Override
  protected Expression2 rewriteLf(Expression2 lf, Object context) {
    if (context == null) {
      return lf;
    } else {
      Preconditions.checkArgument(context instanceof String, 
          "Illegal context. Expected a table id, got: %s", context);
      return WikiTablesUtil.getQueryExpression((String) context, lf);
    }
  }
}
