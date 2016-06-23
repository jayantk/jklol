package com.jayantkrish.jklol.experiments.wikitables;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.enumeratelf.EnumerationRuleFilter;
import com.jayantkrish.jklol.ccg.enumeratelf.LfNode;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionExecutor;

public class WikiTableDenotationRuleFilter implements EnumerationRuleFilter {
  
  private final ExpressionExecutor eval;
  private final String tableId;
  
  public WikiTableDenotationRuleFilter(ExpressionExecutor eval, String tableId) {
    this.eval = Preconditions.checkNotNull(eval);
    this.tableId = tableId;
  }

  @Override
  public boolean apply(LfNode original, LfNode result) {
    if (!original.getType().equals(result.getType())) {
      return true;
    } 

    Optional<Object> originalDenotation = eval.evaluateSilent(original.getLf(), tableId);
    Optional<Object> resultDenotation = eval.evaluateSilent(result.getLf(), tableId);

    return originalDenotation.isPresent() && resultDenotation.isPresent()
        && !originalDenotation.get().equals(resultDenotation.get());
  }
}
