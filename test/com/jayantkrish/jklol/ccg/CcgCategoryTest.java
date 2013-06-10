package com.jayantkrish.jklol.ccg;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;

public class CcgCategoryTest extends TestCase {
  
  ExpressionParser parser;
  
  public void setUp() {
    parser = new ExpressionParser();
  }
  
  public void testInduceLogicalForm() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom("((N{0}\\N{0}){1}/N{2}){1}");
    
    Expression expression = CcgCategory.induceLogicalFormFromSyntax(cat);
    Expression expected = parser.parseSingleExpression("(lambda $2 $0 $0)");
    assertEquals(expected, expression);
  }
  
  public void testInduceLogicalForm2() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom("(((S[9]{0}\\N{1}){0}\\(S[9]{0}\\N{1}){0}){2}/N{3}){2}");
    
    Expression expression = CcgCategory.induceLogicalFormFromSyntax(cat);
    Expression expected = parser.parseSingleExpression("(lambda $3 $0 $1 ($0 $1))");
    assertEquals(expected, expression);
  }
  
  public void testInduceLogicalForm3() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom("((N{0}\\N{0}){1}/(S{2}/N{0}){2}){1}");
    
    Expression expression = CcgCategory.induceLogicalFormFromSyntax(cat);
    Expression expected = parser.parseSingleExpression("(lambda $2 $0 $0)");
    assertEquals(expected, expression);
  }
}
