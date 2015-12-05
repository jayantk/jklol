package com.jayantkrish.jklol.ccg.enumeratelf;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;

public class LogicalFormEnumeratorTest extends TestCase {

  String[][] unaryRules = new String[][] {
      {"s", "(lambda $0 (column:<s,c> $0))"},
      {"s", "(lambda $0 (row:<s,c> $0))"},
      {"c", "(lambda $0 (first-row:<c,c> $0))"}};
    
  String[][] binaryRules = new String[][] {
      {"c", "c", "(lambda $L $R (intersect:<c,<c,c>> $L $R))"},
  };

  LogicalFormEnumerator enumerator;
  ExpressionParser<Expression2> lfParser;
  
  public void setUp() {
    ExpressionParser<Type> typeParser = ExpressionParser.typeParser();
    lfParser = ExpressionParser.expression2();
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();
    
    List<UnaryEnumerationRule> unaryRuleList = Lists.newArrayList();
    for (int i = 0; i < unaryRules.length; i++) {
      Type type = typeParser.parseSingleExpression(unaryRules[i][0]);
      Expression2 lf = lfParser.parseSingleExpression(unaryRules[i][1]);
      unaryRuleList.add(new UnaryEnumerationRule(type, lf, simplifier));
    }
    
    List<BinaryEnumerationRule> binaryRuleList = Lists.newArrayList();
    for (int i = 0; i < binaryRules.length; i++) {
      Type type1 = typeParser.parseSingleExpression(binaryRules[i][0]);
      Type type2 = typeParser.parseSingleExpression(binaryRules[i][1]);
      Expression2 lf = lfParser.parseSingleExpression(binaryRules[i][2]);

      binaryRuleList.add(new BinaryEnumerationRule(type1, type2, lf, simplifier));
    }
    
    enumerator = new LogicalFormEnumerator(unaryRuleList, binaryRuleList);
  }
  
  public void testUnary() {
    Set<Expression2> actual = Sets.newHashSet(enumerate(5, "foo:s"));
    Set<Expression2> expected = Sets.newHashSet();
    expected.add(lfParser.parseSingleExpression("foo:s"));
    expected.add(lfParser.parseSingleExpression("(column:<s,c> foo:s)"));
    expected.add(lfParser.parseSingleExpression("(row:<s,c> foo:s)"));
    expected.add(lfParser.parseSingleExpression("(first-row:<c,c> (column:<s,c> foo:s))"));
    expected.add(lfParser.parseSingleExpression("(first-row:<c,c> (row:<s,c> foo:s))"));
    
    assertEquals(expected, actual);
  }
  
  public void testBinary() {
    Set<Expression2> actual = Sets.newHashSet(enumerate(30, "foo:s", "bar:s"));

    for (Expression2 lf : actual) {
      System.out.println(lf);
      // TODO: test case.
    }
  }
  
  private List<Expression2> enumerate(int max, String... expressions) {
    List<Expression2> lfs = Lists.newArrayList();
    for (int i = 0; i < expressions.length; i++) {
      lfs.add(lfParser.parseSingleExpression(expressions[i]));
    }
    
    return enumerator.enumerate(lfs, max);
  }
}
