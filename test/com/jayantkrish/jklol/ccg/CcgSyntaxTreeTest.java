package com.jayantkrish.jklol.ccg;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.ccg.data.CcgSyntaxTreeFormat;
import com.jayantkrish.jklol.ccg.data.CcgbankSyntaxTreeFormat;
import com.jayantkrish.jklol.data.DataFormat;

public class CcgSyntaxTreeTest extends TestCase {
  
  private DataFormat<CcgSyntaxTree> reader;
  private DataFormat<CcgSyntaxTree> ccgbankReader;
  
  public void setUp() {
    reader = new CcgSyntaxTreeFormat();
    ccgbankReader = CcgbankSyntaxTreeFormat.defaultFormat();
  }

  public void testParseTerminal() {
    CcgSyntaxTree tree = reader.parseFrom("N\\N IN NN that eats");

    assertTrue(tree.isTerminal());
    assertEquals(SyntacticCategory.parseFrom("N\\N"), tree.getRootSyntax());
    assertEquals(Arrays.asList("that", "eats"), tree.getWords());
    assertEquals(Arrays.asList("IN", "NN"), tree.getPosTags());
    assertEquals(0, tree.getSpanStart());
    assertEquals(1, tree.getSpanEnd());
  }
  
  public void testParseNonterminal() {
    CcgSyntaxTree tree = reader.parseFrom("<N < (N/N) DT the> <N NN NN basketball game>>");

    assertTrue(!tree.isTerminal());        
    assertEquals(SyntacticCategory.parseFrom("N"), tree.getRootSyntax());
    assertEquals(0, tree.getSpanStart());
    assertEquals(2, tree.getSpanEnd());

    assertEquals(SyntacticCategory.parseFrom("N/N"), tree.getLeft().getRootSyntax());
    assertEquals(Arrays.asList("the"), tree.getLeft().getWords());
    assertEquals(Arrays.asList("DT"), tree.getLeft().getPosTags());
    assertEquals(0, tree.getLeft().getSpanStart());
    assertEquals(0, tree.getLeft().getSpanEnd());

    assertEquals(SyntacticCategory.parseFrom("N"), tree.getRight().getRootSyntax());
    assertEquals(Arrays.asList("basketball", "game"), tree.getRight().getWords());
    assertEquals(Arrays.asList("NN", "NN"), tree.getRight().getPosTags());
    assertEquals(1, tree.getRight().getSpanStart());
    assertEquals(2, tree.getRight().getSpanEnd());
  }
  
  public void testParseCcgbank() {
    String treeString = "(<T S[dcl] 0 2> (<T S[dcl] 1 2> (<T NP 0 1> (<T N 1 2> (<L N/N NNP NNP Ms. N_254/N_254>) (<L N NNP NNP Haag N>) ) ) (<T S[dcl]\\NP 0 2> (<L (S[dcl]\\NP)/NP VBZ VBZ plays (S[dcl]\\NP_241)/NP_242>) (<T NP 0 1> (<L N NNP NNP Elianti N>) ) ) ) (<L . . . . .>) )";
    CcgSyntaxTree tree = ccgbankReader.parseFrom(treeString);
    
    assertTrue(!tree.isTerminal());
    assertEquals(SyntacticCategory.parseFrom("S[dcl]"), tree.getRootSyntax());
    assertEquals(0, tree.getSpanStart());
    assertEquals(4, tree.getSpanEnd());
    
    CcgSyntaxTree msTerminal = tree.getLeft().getLeft().getLeft();
    assertTrue(msTerminal.isTerminal());
    assertTrue(HeadedSyntacticCategory.parseFrom("(N{1}/N{1}){0}")
        .isUnifiableWith(msTerminal.getHeadedSyntacticCategory()));
    assertEquals(0, msTerminal.getSpanStart());
    assertEquals(0, msTerminal.getSpanEnd());
  }
}
