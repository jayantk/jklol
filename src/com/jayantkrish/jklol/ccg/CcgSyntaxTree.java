package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.ArrayUtils;

public class CcgSyntaxTree {
  // The syntactic category at the root of this tree, 
  // possibly after the application of a unary rule.
  private final SyntacticCategory syntax;
  // Pre-unary rule syntactic category. May be equal to
  // syntax.
  private final SyntacticCategory originalSyntax;
  // Portion of the sentence spanned by this tree.
  private final int spanStart;
  private final int spanEnd;
  
  // Non-null if this is a nonterminal.
  private final CcgSyntaxTree left;
  private final CcgSyntaxTree right;
  
  // Non-null if this is a terminal.
  private final List<String> words;
  
  private CcgSyntaxTree(SyntacticCategory syntax, SyntacticCategory originalSyntax, 
      int spanStart, int spanEnd, CcgSyntaxTree left, CcgSyntaxTree right, List<String> words) {
    this.syntax = syntax;
    this.originalSyntax = originalSyntax;
    this.spanStart = spanStart;
    this.spanEnd = spanEnd;
    
    this.left = left;
    this.right = right;
    
    this.words = words;
    
    // Either both left and right are null, or only one is.
    Preconditions.checkArgument(!(left == null ^ right == null));
    Preconditions.checkArgument(left == null ^ words == null);
  }
  
  public static CcgSyntaxTree createTerminal(SyntacticCategory syntax, SyntacticCategory originalSyntax, 
      int spanStart, int spanEnd, List<String> words) {
    return new CcgSyntaxTree(syntax, originalSyntax, spanStart, spanEnd, null, null, words);
  }
  
  public static CcgSyntaxTree createNonterminal(SyntacticCategory syntax, SyntacticCategory originalSyntax, 
      CcgSyntaxTree left, CcgSyntaxTree right) {
    int spanStart = left.getSpanStart();
    int spanEnd = right.getSpanEnd();
    return new CcgSyntaxTree(syntax, originalSyntax, spanStart, spanEnd, left, right, null);
  }
  
  public static CcgSyntaxTree parseFromString(String treeString) {
    return parseFromString(treeString, 0);
  }
  
  private static CcgSyntaxTree parseFromString(String treeString, int numWordsOnLeft) {
    int curDepth = 0;
    List<Integer> treeStartIndexes = Lists.newArrayList();
    List<Integer> treeEndIndexes = Lists.newArrayList();
    for (int i = 0; i < treeString.length(); i++) {
      if (treeString.charAt(i) == '<') {
        if (curDepth == 0) {
          treeStartIndexes.add(i);
        }
        curDepth++;
      } else if (treeString.charAt(i) == '>') {
        curDepth--;
        if (curDepth == 0) {
          treeEndIndexes.add(i);
        }
      }
    }
    
    Preconditions.checkState(treeStartIndexes.size() == treeEndIndexes.size());
    int size = treeStartIndexes.size();
    Preconditions.checkState(size >= 0 || size <= 2);
    if (size == 0) {
      String[] parts = treeString.trim().split(" ");
      String syntaxPart = parts[0];
      List<String> words = Arrays.asList(ArrayUtils.copyOfRange(parts, 1, parts.length));
      return CcgSyntaxTree.createTerminal(SyntacticCategory.parseFrom(syntaxPart), null,
          numWordsOnLeft, numWordsOnLeft + words.size() - 1, words);
    } if (size == 1) {
      return parseFromString(treeString.substring(treeStartIndexes.get(0) + 1, treeEndIndexes.get(0)),
          numWordsOnLeft);
    } else {
      CcgSyntaxTree leftTree = parseFromString(treeString.substring(treeStartIndexes.get(0) + 1,
          treeEndIndexes.get(0)), numWordsOnLeft);
      CcgSyntaxTree rightTree = parseFromString(treeString.substring(treeStartIndexes.get(1) + 1,
          treeEndIndexes.get(1)), leftTree.getSpanEnd() + 1);
      String syntaxPart = treeString.substring(0, treeStartIndexes.get(0));
      return CcgSyntaxTree.createNonterminal(SyntacticCategory.parseFrom(syntaxPart), null, 
          leftTree, rightTree);
    }
  }

  public SyntacticCategory getRootSyntax() {
    return syntax;
  }
  
  public SyntacticCategory getPreUnaryRuleSyntax() {
    return originalSyntax;
  }
  
  public boolean isTerminal() {
    return left == null;
  }
  
  public List<String> getWords() {
    return words;
  }
  
  public CcgSyntaxTree getLeft() {
    return left;
  }
  
  public CcgSyntaxTree getRight() {
    return right;
  }
  
  public int getSpanStart() {
    return spanStart;
  }
  
  public int getSpanEnd() {
    return spanEnd;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<");
    sb.append(syntax);
    if (!isTerminal()) {
      sb.append(" ");
      sb.append(left.toString());
      sb.append(" ");
      sb.append(right.toString());
    } else {
      sb.append(" ");
      sb.append(Joiner.on(" ").join(words));
    }
    sb.append(">");
    return sb.toString();
  }
}
