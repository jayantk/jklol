package com.jayantkrish.jklol.experiments.wikitables;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionEvaluator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;


public class WikiTableEvaluationComparator implements ExpressionComparator {
  
  private final ExpressionSimplifier simplifier;
  
  private final ExpressionEvaluator evaluator;
  
  private final Map<Expression2, Object> lruCache;
    
  public WikiTableEvaluationComparator(ExpressionSimplifier simplifier, ExpressionEvaluator evaluator) {
    this.simplifier = Preconditions.checkNotNull(simplifier);
    this.evaluator = Preconditions.checkNotNull(evaluator);
    
    int cacheSize = 100;
    this.lruCache = new LinkedHashMap<Expression2, Object>(cacheSize*4/3, 0.75f, true) {
      private static final long serialVersionUID = 1L;
      @Override
      protected boolean removeEldestEntry(Map.Entry<Expression2,Object> eldest) {
        return size() > cacheSize;
      }
    };
  }

  @Override
  public boolean equals(Expression2 a, Expression2 b) {
    List<Expression2> subexpressions = b.getSubexpressions();
    String tableId = subexpressions.get(1).getConstant();

    Object answer = evaluator.evaluateSilentErrors(subexpressions.get(2), "ANS-ERROR");
    
    a = simplifier.apply(a);
    Object value = null;
    if (lruCache.containsKey(a)) {
      // XXX: The keys need to include the table id.
      value = lruCache.get(a);
    } else {
      Expression2 sexpression = ExpressionParser.expression2().parse(
          "(eval-table \"" + tableId + "\" (quote (get-values " + a.toString() + ")))");
      value = evaluator.evaluateSilentErrors(sexpression, "ERROR");
      lruCache.put(a, value);
    }

    /*
    System.out.println(a);
    System.out.println(value);
    System.out.println(answer);
    */

    if (value instanceof Integer) {
      value = Sets.newHashSet(Integer.toString((Integer) value));
    }

    // TODO: may need more sophisticated comparison logic for
    // numerics and yes/no questions.
    return answer.equals(value);
  }
}
