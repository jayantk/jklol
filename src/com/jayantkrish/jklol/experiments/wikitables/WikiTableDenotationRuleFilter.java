package com.jayantkrish.jklol.experiments.wikitables;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.enumeratelf.EnumerationRuleFilter;
import com.jayantkrish.jklol.ccg.enumeratelf.LfNode;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionEvaluator;

public class WikiTableDenotationRuleFilter implements EnumerationRuleFilter {
  
  private final ExpressionEvaluator eval;
  private final String tableId;
  
  private static final String ERROR = "ERROR";
  
  public WikiTableDenotationRuleFilter(ExpressionEvaluator eval, String tableId) {
    this.eval = Preconditions.checkNotNull(eval);
    this.tableId = tableId;
  }

  @Override
  public boolean apply(LfNode original, LfNode result) {
    if (!original.getType().equals(result.getType())) {
      return true;
    } 

    Expression2 originalQuery = WikiTablesUtil.getQueryExpression(tableId, original.getLf());
    Object originalDenotation = eval.evaluateSilentErrors(originalQuery, ERROR);
    Expression2 resultQuery = WikiTablesUtil.getQueryExpression(tableId, result.getLf());
    Object resultDenotation = eval.evaluateSilentErrors(resultQuery, ERROR);

    // System.out.println(originalQuery + " " + originalDenotation + " " + resultQuery + " " + resultDenotation);
    
    return !originalDenotation.equals(resultDenotation);
  }
}
