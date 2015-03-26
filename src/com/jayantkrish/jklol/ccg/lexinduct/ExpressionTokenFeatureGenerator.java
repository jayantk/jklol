package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.util.CountAccumulator;

public class ExpressionTokenFeatureGenerator implements FeatureGenerator<Expression, String> {
  
  private static final long serialVersionUID = 1L;
  
  private final Set<String> tokensToIgnore;
  
  public ExpressionTokenFeatureGenerator(Collection<String> tokensToIgnore) {
    this.tokensToIgnore = Sets.newHashSet(tokensToIgnore);
  }

  @Override
  public Map<String, Double> generateFeatures(Expression item) {
    // List<String> tokens = ExpressionParser.lambdaCalculus().tokenize(item.toString());
    Set<String> tokens = Sets.newHashSet();
    for (ConstantExpression expression : item.getFreeVariables()) {
      tokens.add(expression.getName());
    }

    tokens.removeAll(tokensToIgnore);

    CountAccumulator<String> counts = CountAccumulator.create();
    counts.incrementByOne(tokens);
    Map<String, Double> countMap = counts.getCountMap();
    return countMap;
  }
}
