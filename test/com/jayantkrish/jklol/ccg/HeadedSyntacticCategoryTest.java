package com.jayantkrish.jklol.ccg;

import junit.framework.TestCase;

/**
 * Unit tests for {@link HeadedSyntacticCategory}.
 * 
 * @author jayantk
 */
public class HeadedSyntacticCategoryTest extends TestCase {

  public void testParseFrom() {
    String transVerb = "((S{0}\\N{1}){0}/N{2}){0}";
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(transVerb);
    
    assertEquals(0, cat.getRootVariable());
    assertEquals(2, cat.getArgumentType().getRootVariable());
    assertEquals(0, cat.getReturnType().getRootVariable());
    assertTrue(cat.getArgumentType().getSyntax().isAtomic());
    assertEquals(0, cat.getReturnType().getReturnType().getRootVariable());
    assertEquals(1, cat.getReturnType().getArgumentType().getRootVariable());
  }
  
  public void testParseFrom2() {
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(
        "(((S{1}\\N{2}){1}/N{0}){1}/((S{1}\\N{2})/N{0})){3}");
    
    assertEquals(3, cat.getRootVariable());
    assertEquals(-1, cat.getArgumentType().getRootVariable());
    assertEquals(0, cat.getArgumentType().getArgumentType().getRootVariable());
    assertEquals(-1, cat.getArgumentType().getReturnType().getRootVariable());
    assertEquals(1, cat.getArgumentType().getReturnType().getReturnType().getRootVariable());
    assertEquals(1, cat.getReturnType().getRootVariable());
    assertEquals(1, cat.getReturnType().getReturnType().getRootVariable());
    assertEquals(0, cat.getReturnType().getArgumentType().getRootVariable());
    
    System.out.println(cat.toString());
  }
}
