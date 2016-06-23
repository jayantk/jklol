package com.jayantkrish.jklol.experiments.wikitables;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionExecutor;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;


public class WikiTableExecutionComparator implements ExpressionComparator {
  
  private final ExpressionSimplifier simplifier;
  
  private final ExpressionExecutor executor;
  
  private final Map<Expression2, Optional<Object>> lruCache;
    
  public WikiTableExecutionComparator(ExpressionSimplifier simplifier, ExpressionExecutor executor) {
    this.simplifier = Preconditions.checkNotNull(simplifier);
    this.executor = Preconditions.checkNotNull(executor);
    
    int cacheSize = 100;
    this.lruCache = new LinkedHashMap<Expression2, Optional<Object>>(cacheSize*4/3, 0.75f, true) {
      private static final long serialVersionUID = 1L;
      @Override
      protected boolean removeEldestEntry(Map.Entry<Expression2,Optional<Object>> eldest) {
        return size() > cacheSize;
      }
    };
  }

  @Override
  public boolean equals(Expression2 a, Expression2 b) {
    List<Expression2> subexpressions = b.getSubexpressions();
    String tableId = subexpressions.get(1).getConstant();

    Optional<Object> answerOption = executor.evaluateSilent(subexpressions.get(2));
    Preconditions.checkArgument(answerOption.isPresent(), "Error executing answer expression: %s",
        subexpressions.get(2));
    Object answer = answerOption.get();
    
    a = simplifier.apply(a);
    Optional<Object> value = null;
    if (lruCache.containsKey(a)) {
      // XXX: The keys need to include the table id.
      value = lruCache.get(a);
    } else {
      value = executor.evaluateSilent(a, tableId);
      lruCache.put(a, value);
    }
    
    if (!value.isPresent()) {
      return false;
    }
    Object presentValue = value.get();

    if (presentValue instanceof Integer) {
      presentValue = Sets.newHashSet(Integer.toString((Integer) presentValue));
    }

    // TODO: may need more sophisticated comparison logic for
    // numerics and yes/no questions.
    return answer.equals(presentValue);
  }
}
