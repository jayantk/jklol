package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.util.CountAccumulator;

public class ExpressionTokenFeatureGenerator implements FeatureGenerator<Expression2, String> {
  
  private static final long serialVersionUID = 1L;
  
  private final Set<String> tokensToIgnore;
  
  public ExpressionTokenFeatureGenerator(Collection<String> tokensToIgnore) {
    this.tokensToIgnore = Sets.newHashSet(tokensToIgnore);
  }

  @Override
  public Map<String, Double> generateFeatures(Expression2 item) {
    // List<String> tokens = ExpressionParser.lambdaCalculus().tokenize(item.toString());
    Set<String> tokens = Sets.newHashSet();
    for (String expression : StaticAnalysis.getFreeVariables(item)) {
      tokens.add(expression);
    }

    tokens.removeAll(tokensToIgnore);
    
    CountAccumulator<String> counts = CountAccumulator.create();
    for (String token : tokens) {
      int count = StaticAnalysis.getIndexesOfFreeVariable(item, token).length;
      counts.increment(token, count);
    }

    Map<String, Double> countMap = counts.getCountMap();
    return countMap;
  }
}
