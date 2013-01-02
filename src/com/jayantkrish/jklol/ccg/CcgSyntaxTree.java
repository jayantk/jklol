package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
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
    this.syntax = Preconditions.checkNotNull(syntax);
    this.originalSyntax = Preconditions.checkNotNull(originalSyntax);
    this.spanStart = spanStart;
    this.spanEnd = spanEnd;
    Preconditions.checkArgument(spanEnd >= spanStart);
    
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
      SyntacticCategory rootCat = SyntacticCategory.parseFrom(syntaxPart);
      return CcgSyntaxTree.createTerminal(rootCat, rootCat, numWordsOnLeft, 
          numWordsOnLeft + words.size() - 1, words);
    } if (size == 1) {
      return parseFromString(treeString.substring(treeStartIndexes.get(0) + 1, treeEndIndexes.get(0)),
          numWordsOnLeft);
    } else {
      CcgSyntaxTree leftTree = parseFromString(treeString.substring(treeStartIndexes.get(0) + 1,
          treeEndIndexes.get(0)), numWordsOnLeft);
      CcgSyntaxTree rightTree = parseFromString(treeString.substring(treeStartIndexes.get(1) + 1,
          treeEndIndexes.get(1)), leftTree.getSpanEnd() + 1);
      String syntaxPart = treeString.substring(0, treeStartIndexes.get(0));
      SyntacticCategory rootCat = SyntacticCategory.parseFrom(syntaxPart);
      return CcgSyntaxTree.createNonterminal(rootCat, rootCat, leftTree, rightTree);
    }
  }

  public static CcgSyntaxTree parseFromCcgBankString(String treeString) {
    return correctCcgBankConj(parseFromCcgBankString(treeString, 0));
  }
  
  private static CcgSyntaxTree parseFromCcgBankString(String treeString, int numWordsOnLeft) {
    int curDepth = 0;
    boolean withinAngleBrackets = false;
    List<Integer> treeStartIndexes = Lists.newArrayList();
    List<Integer> treeEndIndexes = Lists.newArrayList();
    for (int i = 0; i < treeString.length(); i++) {
      if (treeString.charAt(i) == '<') {
        Preconditions.checkState(withinAngleBrackets == false);
        withinAngleBrackets = true;
      } else if (treeString.charAt(i) == '>') {
        Preconditions.checkState(withinAngleBrackets == true);
        withinAngleBrackets = false;
      } else if (treeString.charAt(i) == '(' && !withinAngleBrackets){
        if (curDepth == 1) {
          treeStartIndexes.add(i);
        }
        curDepth++;
      } else if (treeString.charAt(i) == ')' && !withinAngleBrackets) {
        curDepth--;
        if (curDepth == 1) {
          treeEndIndexes.add(i);
        }
      }
    }

    Preconditions.checkState(treeStartIndexes.size() == treeEndIndexes.size());
    int size = treeStartIndexes.size();
    Preconditions.checkState(size >= 0 || size <= 2);
    if (size == 0) {
      String[] parts = treeString.trim().split(" ");
      String syntaxPart = parts[1];
      List<String> words = Arrays.asList(parts[4]);
      SyntacticCategory rootCat = SyntacticCategory.parseFrom(syntaxPart);
      return CcgSyntaxTree.createTerminal(rootCat, rootCat, numWordsOnLeft, 
          numWordsOnLeft + words.size() - 1, words);
    } if (size == 1) {
      // Unary rule.
      String[] parts = treeString.substring(0, treeStartIndexes.get(0)).split(" ");
      SyntacticCategory rootCat = SyntacticCategory.parseFrom(parts[1]);
      CcgSyntaxTree baseTree = parseFromCcgBankString(treeString.substring(treeStartIndexes.get(0),
          treeEndIndexes.get(0) + 1), numWordsOnLeft);
      return new CcgSyntaxTree(rootCat, baseTree.getPreUnaryRuleSyntax(), baseTree.getSpanStart(),
          baseTree.getSpanEnd(), baseTree.getLeft(), baseTree.getRight(), baseTree.getWords());
    } else {
      CcgSyntaxTree leftTree = parseFromCcgBankString(treeString.substring(treeStartIndexes.get(0),
          treeEndIndexes.get(0) + 1), numWordsOnLeft);
      CcgSyntaxTree rightTree = parseFromCcgBankString(treeString.substring(treeStartIndexes.get(1),
          treeEndIndexes.get(1) + 1), leftTree.getSpanEnd() + 1);
      String syntaxPart = treeString.substring(0, treeStartIndexes.get(0)).split(" ")[1];
      SyntacticCategory rootCat = SyntacticCategory.parseFrom(syntaxPart);
      return CcgSyntaxTree.createNonterminal(rootCat, rootCat, leftTree, rightTree);
    }
  }
  
  private static CcgSyntaxTree correctCcgBankConj(CcgSyntaxTree origTree) {
    if (origTree.isTerminal()) {
      return origTree;
    } else {
      CcgSyntaxTree leftTree = correctCcgBankConj(origTree.getLeft());
      CcgSyntaxTree rightTree = correctCcgBankConj(origTree.getRight());
      
      SyntacticCategory leftRoot = leftTree.getRootSyntax();
      SyntacticCategory rightRoot = rightTree.getRootSyntax();
      
      if (isConj(leftRoot)) {
        // Move the application of any unary rules down so they apply before the conjunction.
        if (!origTree.getRootSyntax().equals(origTree.getPreUnaryRuleSyntax())) {
          rightTree = rightTree.updateRootSyntax(origTree.getRootSyntax());
          rightRoot = rightTree.getRootSyntax();
        }

        SyntacticCategory expectedCat = SyntacticCategory.createFunctional(Direction.LEFT, rightRoot, rightRoot);
        System.out.println("left: " + leftRoot + " right: " + rightRoot + " expected: " + expectedCat);
        return CcgSyntaxTree.createNonterminal(expectedCat, expectedCat, leftTree, rightTree);
      } else if (isConj(rightRoot)) {
        // Move the application of any unary rules down so they apply before the conjunction.
        if (!origTree.getRootSyntax().equals(origTree.getPreUnaryRuleSyntax())) {
          leftTree = leftTree.updateRootSyntax(origTree.getRootSyntax());
          leftRoot = leftTree.getRootSyntax();
        }
        
        SyntacticCategory expectedCat = SyntacticCategory.createFunctional(Direction.RIGHT, leftRoot, leftRoot);
        System.out.println("left: " + leftRoot + " right: " + rightRoot + " expected: " + expectedCat);
        return CcgSyntaxTree.createNonterminal(expectedCat, expectedCat, leftTree, rightTree);
      }
      
      if (isPunc(leftRoot) && !origTree.getPreUnaryRuleSyntax().equals(rightRoot)) {
        System.out.println("punc fix: " + leftRoot + " right: " + rightRoot + " origRoot: " + origTree.getPreUnaryRuleSyntax());
        if (origTree.getPreUnaryRuleSyntax().equals(origTree.getRootSyntax())) {
          return CcgSyntaxTree.createNonterminal(rightRoot, rightRoot, leftTree, rightTree);
        } else {
          return CcgSyntaxTree.createNonterminal(origTree.getRootSyntax(), rightRoot, leftTree, rightTree);
        }
      } else if (isPunc(rightRoot) && !origTree.getPreUnaryRuleSyntax().equals(leftRoot)) {
        System.out.println("punc fix: " + leftRoot + " right: " + rightRoot + " origRoot: " + origTree.getPreUnaryRuleSyntax());
        if (origTree.getPreUnaryRuleSyntax().equals(origTree.getRootSyntax())) {
          return CcgSyntaxTree.createNonterminal(leftRoot, leftRoot, leftTree, rightTree);
        } else {
          return CcgSyntaxTree.createNonterminal(origTree.getRootSyntax(), leftRoot, leftTree, rightTree);
        }
      }
      
      return CcgSyntaxTree.createNonterminal(origTree.getRootSyntax(), 
          origTree.getPreUnaryRuleSyntax(), leftTree, rightTree);
    }
  }
  
  private static boolean isConj(SyntacticCategory syntax) {
    return syntax.isAtomic() && syntax.getValue().equals("conj");
  }
  
  private static boolean isPunc(SyntacticCategory syntax) {
    return syntax.isAtomic() && syntax.getValue().equals(",");
  }

  public SyntacticCategory getRootSyntax() {
    return syntax;
  }
  
  public CcgSyntaxTree updateRootSyntax(SyntacticCategory newRoot) {
    if (isTerminal()) {
      return CcgSyntaxTree.createTerminal(newRoot, originalSyntax, spanStart, spanEnd, words);
    } else {
      return CcgSyntaxTree.createNonterminal(newRoot, originalSyntax, left, right);
    }
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
  
  public List<String> getAllSpannedWords() {
    List<String> spannedWords = Lists.newArrayList();
    getAllSpannedWordsHelper(spannedWords);
    return spannedWords;
  }

  private void getAllSpannedWordsHelper(List<String> wordAccumulator) {
    if (!isTerminal()) {
      left.getAllSpannedWordsHelper(wordAccumulator);
      right.getAllSpannedWordsHelper(wordAccumulator);
    } else {
      wordAccumulator.addAll(words);
    }
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
    sb.append("_");
    sb.append(originalSyntax);
    sb.append(spanStart);
    sb.append(".");
    sb.append(spanEnd);
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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((left == null) ? 0 : left.hashCode());
    result = prime * result + ((originalSyntax == null) ? 0 : originalSyntax.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
    result = prime * result + spanEnd;
    result = prime * result + spanStart;
    result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
    result = prime * result + ((words == null) ? 0 : words.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CcgSyntaxTree other = (CcgSyntaxTree) obj;
    if (left == null) {
      if (other.left != null)
        return false;
    } else if (!left.equals(other.left))
      return false;
    if (originalSyntax == null) {
      if (other.originalSyntax != null)
        return false;
    } else if (!originalSyntax.equals(other.originalSyntax))
      return false;
    if (right == null) {
      if (other.right != null)
        return false;
    } else if (!right.equals(other.right))
      return false;
    if (spanEnd != other.spanEnd)
      return false;
    if (spanStart != other.spanStart)
      return false;
    if (syntax == null) {
      if (other.syntax != null)
        return false;
    } else if (!syntax.equals(other.syntax))
      return false;
    if (words == null) {
      if (other.words != null)
        return false;
    } else if (!words.equals(other.words))
      return false;
    return true;
  }  
}
