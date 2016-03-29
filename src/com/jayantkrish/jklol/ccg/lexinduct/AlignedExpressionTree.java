package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

public class AlignedExpressionTree {
  private final Expression2 expression;
  private final Type type;

  // Number of arguments of expression that get
  // applied in this tree.
  private final int numAppliedArguments;

  // Possible spans of the input sentence that this
  // node of the tree could be aligned to.
  private final int[] possibleSpanStarts;
  private final int[] possibleSpanEnds;

  // Children of this expression tree whose expressions
  // can be composed to produce this one. Null unless
  // this node is a nonterminal.
  private final AlignedExpressionTree left;
  private final AlignedExpressionTree right;

  // The words that this expression is aligned to.
  // Null unless this node is a terminal.
  // The words are consecutive in the sentence.
  private final List<String> words;

  private AlignedExpressionTree(Expression2 expression, Type type, int numAppliedArguments,
      int[] possibleSpanStarts, int[] possibleSpanEnds, AlignedExpressionTree left,
      AlignedExpressionTree right, List<String> words) {
    this.expression = Preconditions.checkNotNull(expression);
    this.type = Preconditions.checkNotNull(type);
    this.numAppliedArguments = numAppliedArguments;
    Preconditions.checkArgument(possibleSpanStarts.length == possibleSpanEnds.length);
    this.possibleSpanStarts = possibleSpanStarts;
    this.possibleSpanEnds = possibleSpanEnds;

    Preconditions.checkArgument( (left == null && right == null) || (left != null && right != null));
    this.left = left;
    this.right = right;

    Preconditions.checkArgument(left == null ^ words == null);
    this.words = words;
  }

  public static AlignedExpressionTree forTerminal(Expression2 expression, Type type,
      int numAppliedArguments, int[] possibleSpanStarts, int[] possibleSpanEnds, List<String> words) {
    return new AlignedExpressionTree(expression, type, numAppliedArguments, 
        possibleSpanStarts, possibleSpanEnds, null, null, words);
  }

  public static AlignedExpressionTree forNonterminal(Expression2 expression, Type type,
      int numAppliedArguments, AlignedExpressionTree left, AlignedExpressionTree right) {

    List<Integer> spanStarts = Lists.newArrayList();
    List<Integer> spanEnds = Lists.newArrayList();
    for (int i = 0; i < left.possibleSpanStarts.length; i++) {
      for (int j = 0; j < right.possibleSpanStarts.length; j++) {
        int leftSpanStart = left.possibleSpanStarts[i];
        int leftSpanEnd = left.possibleSpanEnds[i];
        int rightSpanStart = right.possibleSpanStarts[j];
        int rightSpanEnd = right.possibleSpanEnds[j];

        // Spans can compose as long as they do not overlap.
        if (leftSpanEnd <= rightSpanStart || rightSpanEnd <= leftSpanStart) {
          spanStarts.add(Math.min(leftSpanStart, rightSpanStart));
          spanEnds.add(Math.max(leftSpanEnd, rightSpanEnd));
        }
      }
    }

    return new AlignedExpressionTree(expression, type, numAppliedArguments, Ints.toArray(spanStarts),
        Ints.toArray(spanEnds), left, right, null);
  }

  public Expression2 getExpression() {
    return expression;
  }

  public List<String> getWords() {
    return words;
  }
  
  public boolean isLeaf() {
    return words != null;
  }

  public AlignedExpressionTree getLeft() {
    return left;
  }

  public AlignedExpressionTree getRight() {
    return right;
  }

  public int[] getSpanStarts() {
    return possibleSpanStarts;
  }

  public int[] getSpanEnds() {
    return possibleSpanEnds;
  }

  public int getNumAppliedArguments() {
    return numAppliedArguments;
  }

  public Multimap<List<String>, AlignedExpression> getWordAlignments() {
    Multimap<List<String>, AlignedExpression> alignments = HashMultimap.create();
    getWordAlignmentsHelper(alignments);
    return alignments;
  }

  private void getWordAlignmentsHelper(Multimap<List<String>, AlignedExpression> map) {
    if (words != null) {
      for (int i = 0 ; i < possibleSpanStarts.length; i++) {
        map.put(words, new AlignedExpression(words, expression, numAppliedArguments,
            possibleSpanStarts[i], possibleSpanEnds[i]));
      }
    }

    if (left != null) {
      left.getWordAlignmentsHelper(map);
      right.getWordAlignmentsHelper(map);
    }
  }

  public List<LexiconEntry> generateLexiconEntries(TypeDeclaration typeDeclaration) {
    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    generateLexiconEntriesHelper(typeDeclaration, Collections.<AlignedExpressionTree>emptyList(),
        Collections.<Type>emptyList(), null, lexiconEntries);
    return lexiconEntries;
  }

  private Type generateLexiconEntriesHelper(TypeDeclaration typeDeclaration,
      List<AlignedExpressionTree> argumentStack, List<Type> argumentTypeStack,
      AlignedExpressionTree func, List<LexiconEntry> lexiconEntries) {
    if (isLeaf()) {
      Type type = TypeDeclaration.TOP;
      List<Direction> argDirs = Lists.newArrayList();
      for (int i = 0; i < argumentStack.size(); i++) {
        Type argType = argumentTypeStack.get(i);
        type = type.addArgument(argType);

        if (argumentStack.get(i).getSpanStarts()[0] < getSpanStarts()[0]) {
          argDirs.add(Direction.LEFT);
        } else if (argumentStack.get(i).getSpanEnds()[0] > getSpanEnds()[0]) {
          argDirs.add(Direction.RIGHT);
        } else {
          // Unknown direction. This case shouldn't happen when decoding the CFG model.
          argDirs.add(Direction.BOTH);
        }
      }

      if (func != null) {
        Type funcType = StaticAnalysis.inferType(func.getExpression(), typeDeclaration);
        type = typeDeclaration.unify(type, funcType.getArgumentType());
      }

      Type initialType = StaticAnalysis.inferType(getExpression(), typeDeclaration);
      Type returnType = StaticAnalysis.inferType(getExpression(), typeDeclaration.unify(initialType, type), typeDeclaration);
      Type completeType = returnType;
      List<Type> argumentTypes = Lists.newArrayList();
      for (int i = 0; i < getNumAppliedArguments(); i++) {
        argumentTypes.add(returnType.getArgumentType());
        returnType = returnType.getReturnType();
      }
      Collections.reverse(argumentTypes);

      // Generate a list of possible words that could be mapped
      // to this syntactic category and logical form.
      List<List<String>> possibleWords = Lists.newArrayList();
      possibleWords.add(words);

      /*
      if (words.size() > 1) {
        for (String word : words) {
          possibleWords.add(Arrays.asList(word));
        }
      }
      */

      // Build a syntactic category for the expression based on the 
      // number of arguments it accepted in the sentence. Simultaneously
      // generate its dependencies and head assignment.
      for (List<String> curWords : possibleWords) {
        HeadedSyntacticCategory syntax = typeToSyntax(returnType, 0);
        for (int i = 0; i < getNumAppliedArguments(); i++) {
          int nextVar = Ints.max(syntax.getUniqueVariables()) + 1;
          HeadedSyntacticCategory argSyntax = typeToSyntax(argumentTypes.get(i), nextVar);
          syntax = syntax.addArgument(argSyntax, argDirs.get(i), 0);
        }

        CcgCategory ccgCategory = CcgCategory.fromSyntaxLf(syntax, getExpression());
        LexiconEntry entry = new LexiconEntry(curWords, ccgCategory);

        lexiconEntries.add(entry);
      }

      return completeType;
    } else {      
      Type leftType = left.generateLexiconEntriesHelper(typeDeclaration,
          Collections.<AlignedExpressionTree>emptyList(), Collections.<Type>emptyList(),
          right, lexiconEntries);

      List<AlignedExpressionTree> newArgs = Lists.newArrayList(argumentStack);
      newArgs.add(left);
      List<Type> newArgTypes = Lists.newArrayList(argumentTypeStack);
      newArgTypes.add(leftType);

      Type rightType = right.generateLexiconEntriesHelper(typeDeclaration, newArgs,
          newArgTypes, null, lexiconEntries);
      return rightType.getReturnType();
    }
  }
  
  private static HeadedSyntacticCategory typeToSyntax(Type type, int nextHeadNum) {
    // return HeadedSyntacticCategory.parseFrom("N:" + type + "{" + nextHeadNum + "}");
    if (type.isAtomic()) {
      return HeadedSyntacticCategory.parseFrom("N:" + type.getAtomicTypeName() + "{" + nextHeadNum + "}");
    } else {
      HeadedSyntacticCategory returnCat = typeToSyntax(type.getReturnType(), nextHeadNum);
      int nextNum = Ints.max(returnCat.getUniqueVariables()) + 1;
      HeadedSyntacticCategory argCat = typeToSyntax(type.getArgumentType(), nextNum);
      nextNum = Ints.max(argCat.getUniqueVariables()) + 1;
      return returnCat.addArgument(argCat, Direction.BOTH, nextHeadNum);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toStringHelper(this, sb, 0);
    return sb.toString();
  }

  private static void toStringHelper(AlignedExpressionTree tree, StringBuilder sb, int depth) {
    for (int i = 0 ; i < depth; i++) {
      sb.append(" ");
    }
    sb.append(tree.expression);
    sb.append(" : ");
    sb.append(tree.type);
    sb.append(" ");
    sb.append(tree.numAppliedArguments);
    sb.append(" ");
    for (int i = 0; i < tree.possibleSpanStarts.length; i++) {
      sb.append("[");
      sb.append(tree.possibleSpanStarts[i]);
      sb.append(",");
      sb.append(tree.possibleSpanEnds[i]);
      sb.append("]");
    }

    if (tree.words != null) {
      sb.append(" -> \"");
      sb.append(tree.words);
      sb.append("\"");
    }
    sb.append("\n");

    if (tree.left != null) {
      toStringHelper(tree.left, sb, depth + 2);      
      toStringHelper(tree.right, sb, depth + 2);
    }
  }

  /**
   * A word aligned to an expression.
   * 
   * @author jayant
   *
   */
  public static class AlignedExpression {
    private final List<String> words;
    private final Expression2 expression;
    private final int numAppliedArgs;
    private final int spanStart;
    private final int spanEnd;

    public AlignedExpression(List<String> words, Expression2 expression, int numAppliedArgs,
        int spanStart, int spanEnd) {
      this.words = Preconditions.checkNotNull(words);
      this.expression = Preconditions.checkNotNull(expression);
      this.numAppliedArgs = numAppliedArgs;
      this.spanStart = spanStart;
      this.spanEnd = spanEnd;
    }

    public List<String> getWords() {
      return words;
    }

    public Expression2 getExpression() {
      return expression;
    }

    public int getNumAppliedArgs() {
      return numAppliedArgs;
    }
    
    public int getSpanStart() {
      return spanStart;
    }
    
    public int getSpanEnd() {
      return spanEnd;
    }
    
    @Override
    public String toString() {
      return "[" + expression + " " + spanStart + "," + spanEnd + "]";
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((expression == null) ? 0 : expression.hashCode());
      result = prime * result + numAppliedArgs;
      result = prime * result + spanEnd;
      result = prime * result + spanStart;
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
      AlignedExpression other = (AlignedExpression) obj;
      if (expression == null) {
        if (other.expression != null)
          return false;
      } else if (!expression.equals(other.expression))
        return false;
      if (numAppliedArgs != other.numAppliedArgs)
        return false;
      if (spanEnd != other.spanEnd)
        return false;
      if (spanStart != other.spanStart)
        return false;
      if (words == null) {
        if (other.words != null)
          return false;
      } else if (!words.equals(other.words))
        return false;
      return true;
    }
  }
}
