package com.jayantkrish.jklol.ccg;

import java.util.Arrays;

import junit.framework.TestCase;

public class CcgSyntaxTreeTest extends TestCase {

  public void testParseTerminal() {
    CcgSyntaxTree tree = CcgSyntaxTree.parseFromString("N\\N that eats");

    assertTrue(tree.isTerminal());
    assertEquals(SyntacticCategory.parseFrom("N\\N"), tree.getRootSyntax());
    assertEquals(Arrays.asList("that", "eats"), tree.getWords());
    assertEquals(0, tree.getSpanStart());
    assertEquals(1, tree.getSpanEnd());
  }
  
  public void testParseNonterminal() {
    CcgSyntaxTree tree = CcgSyntaxTree.parseFromString("<N < (N/N) the> <N basketball game>>");

    assertTrue(!tree.isTerminal());        
    assertEquals(SyntacticCategory.parseFrom("N"), tree.getRootSyntax());
    assertEquals(0, tree.getSpanStart());
    assertEquals(2, tree.getSpanEnd());

    assertEquals(SyntacticCategory.parseFrom("N/N"), tree.getLeft().getRootSyntax());
    assertEquals(Arrays.asList("the"), tree.getLeft().getWords());
    assertEquals(0, tree.getLeft().getSpanStart());
    assertEquals(0, tree.getLeft().getSpanEnd());

    assertEquals(SyntacticCategory.parseFrom("N"), tree.getRight().getRootSyntax());
    assertEquals(Arrays.asList("basketball", "game"), tree.getRight().getWords());
    assertEquals(1, tree.getRight().getSpanStart());
    assertEquals(2, tree.getRight().getSpanEnd());
  }
}
