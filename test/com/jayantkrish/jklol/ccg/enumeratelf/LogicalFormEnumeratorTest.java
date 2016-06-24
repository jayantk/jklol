package com.jayantkrish.jklol.ccg.enumeratelf;

import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.AmbEvalExecutor;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionExecutor;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.util.IndexedList;

public class LogicalFormEnumeratorTest extends TestCase {

  String[][] unaryRules = new String[][] {
      {"s", "(lambda ($0) (column:<s,c> $0))"},
      {"s", "(lambda ($0) (row:<s,c> $0))"},
      {"c", "(lambda ($0) (first-row:<c,c> $0))"}
  };

  String[][] binaryRules = new String[][] {
      {"c", "c", "(lambda ($L $R) (intersect:<c,<c,c>> $L $R))"},
  };

  LogicalFormEnumerator enumerator;
  ExpressionParser<Expression2> lfParser;
  TypeDeclaration typeDeclaration;
  ExpressionExecutor executor;
  
  public void setUp() {
    lfParser = ExpressionParser.expression2();
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();
    typeDeclaration = ExplicitTypeDeclaration.getDefault();
    
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable();
    Environment env = AmbEval.getDefaultEnvironment(symbolTable);
    ExpressionParser<SExpression> sParser = ExpressionParser.sExpression(symbolTable);
    AmbEval eval = new AmbEval(symbolTable);
    executor = new AmbEvalExecutor(sParser, eval, env);

    enumerator = LogicalFormEnumerator.fromRuleStrings(unaryRules, binaryRules,
        simplifier, typeDeclaration, executor);
  }

  public void testUnary() {
    Set<Expression2> actual = Sets.newHashSet(enumerate(5, "foo:s"));
    Set<Expression2> expected = Sets.newHashSet();
    expected.add(lfParser.parse("foo:s"));
    expected.add(lfParser.parse("(column:<s,c> foo:s)"));
    expected.add(lfParser.parse("(row:<s,c> foo:s)"));
    expected.add(lfParser.parse("(first-row:<c,c> (column:<s,c> foo:s))"));
    expected.add(lfParser.parse("(first-row:<c,c> (row:<s,c> foo:s))"));
    
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
    Set<Expression2> lfs = Sets.newHashSet();
    for (int i = 0; i < expressions.length; i++) {
      lfs.add(lfParser.parse(expressions[i]));
    }

    return enumerator.enumerate(lfs, null, max);
  }
}
