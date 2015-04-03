package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

public class AlignedExpressionTree {
  private final Expression2 expression;

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

  // The word that this expression is aligned to.
  // Null unless this node is a terminal.
  private final String word;

  private AlignedExpressionTree(Expression2 expression, int numAppliedArguments,
      int[] possibleSpanStarts, int[] possibleSpanEnds, AlignedExpressionTree left,
      AlignedExpressionTree right, String word) {
    this.expression = Preconditions.checkNotNull(expression);
    this.numAppliedArguments = numAppliedArguments;
    Preconditions.checkArgument(possibleSpanStarts.length == possibleSpanEnds.length);
    this.possibleSpanStarts = possibleSpanStarts;
    this.possibleSpanEnds = possibleSpanEnds;

    Preconditions.checkArgument( (left == null && right == null) || (left != null && right != null));
    this.left = left;
    this.right = right;

    Preconditions.checkArgument(left == null ^ word == null);
    this.word = word;
  }

  public static AlignedExpressionTree forTerminal(Expression2 expression, int numAppliedArguments,
      int[] possibleSpanStarts, int[] possibleSpanEnds, String word) {
    return new AlignedExpressionTree(expression, numAppliedArguments, 
        possibleSpanStarts, possibleSpanEnds, null, null, word);
  }

  public static AlignedExpressionTree forNonterminal(Expression2 expression, int numAppliedArguments,
      AlignedExpressionTree left, AlignedExpressionTree right) {

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

    return new AlignedExpressionTree(expression, numAppliedArguments, Ints.toArray(spanStarts),
        Ints.toArray(spanEnds), left, right, null);
  }

  public Expression2 getExpression() {
    return expression;
  }

  public String getWord() {
    return word;
  }
  
  public boolean isLeaf() {
    return word != null;
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

  public Multimap<String, AlignedExpression> getWordAlignments() {
    Multimap<String, AlignedExpression> alignments = HashMultimap.create();
    getWordAlignmentsHelper(alignments);
    return alignments;
  }

  private void getWordAlignmentsHelper(Multimap<String, AlignedExpression> map) {
    if (word != null && !word.equals(ParametricAlignmentModel.NULL_WORD)) {
      for (int i = 0 ; i < possibleSpanStarts.length; i++) {
        map.put(word, new AlignedExpression(word, expression, numAppliedArguments,
            possibleSpanStarts[i], possibleSpanEnds[i]));
      }
    }

    if (left != null) {
      left.getWordAlignmentsHelper(map);
      right.getWordAlignmentsHelper(map);
    }
  }

  public List<LexiconEntry> generateLexiconEntries(Map<String, String> typeReplacements) {
    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    generateLexiconEntriesHelper(typeReplacements, Collections.<AlignedExpressionTree>emptyList(),
        lexiconEntries);
    return lexiconEntries;
  }

  private void generateLexiconEntriesHelper(Map<String, String> typeReplacements,
      List<AlignedExpressionTree> argumentStack, List<LexiconEntry> lexiconEntries) {
    if (isLeaf()) {
      Type type = Type.createAtomic("unknown");
      List<Direction> argDirs = Lists.newArrayList();
      for (int i = 0; i < argumentStack.size(); i++) {
        Expression2 arg = argumentStack.get(i).getExpression();
        Type argType = StaticAnalysis.inferType(arg, typeReplacements);
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

      Type initialType = StaticAnalysis.inferType(getExpression(), typeReplacements);
      Type returnType = StaticAnalysis.inferType(getExpression(), StaticAnalysis.unify(initialType, type), typeReplacements);;
      List<Type> argumentTypes = Lists.newArrayList();
      for (int i = 0; i < getNumAppliedArguments(); i++) {
        argumentTypes.add(returnType.getArgumentType());
        returnType = returnType.getReturnType();
      }
      Collections.reverse(argumentTypes);
      
      // Build a syntactic category for the expression based on the 
      // number of arguments it accepted in the sentence. Simultaneously
      // generate its dependencies and head assignment.
      String head = getWord();
      List<String> subjects = Lists.newArrayList();
      List<Integer> argumentNums = Lists.newArrayList();
      List<Integer> objects = Lists.newArrayList();
      List<Set<String>> assignments = Lists.newArrayList();
      assignments.add(Sets.newHashSet(head));
      HeadedSyntacticCategory syntax = HeadedSyntacticCategory.parseFrom("N:" + returnType + "{0}");
      for (int i = 0; i < getNumAppliedArguments(); i++) {
        HeadedSyntacticCategory argSyntax = HeadedSyntacticCategory
            .parseFrom("N:" + argumentTypes.get(i) + "{" + (i + 1) +"}");
        syntax = syntax.addArgument(argSyntax, argDirs.get(i), 0);

        subjects.add(head);
        argumentNums.add(i + 1);
        objects.add(i + 1);
        assignments.add(Collections.<String>emptySet());
      }

      CcgCategory ccgCategory = new CcgCategory(syntax, getExpression(), subjects,
            argumentNums, objects, assignments);
      LexiconEntry entry = new LexiconEntry(Arrays.asList(getWord()), ccgCategory);

      lexiconEntries.add(entry);
    } else {      
      left.generateLexiconEntriesHelper(typeReplacements, Collections.<AlignedExpressionTree>emptyList(),
          lexiconEntries);

      List<AlignedExpressionTree> newArgs = Lists.newArrayList(argumentStack);
      newArgs.add(left);
      right.generateLexiconEntriesHelper(typeReplacements, newArgs, lexiconEntries);
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

    if (tree.word != ParametricAlignmentModel.NULL_WORD) {
      sb.append(" -> \"");
      sb.append(tree.word);
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
    private final String word;
    private final Expression2 expression;
    private final int numAppliedArgs;
    private final int spanStart;
    private final int spanEnd;

    public AlignedExpression(String word, Expression2 expression, int numAppliedArgs,
        int spanStart, int spanEnd) {
      this.word = Preconditions.checkNotNull(word);
      this.expression = Preconditions.checkNotNull(expression);
      this.numAppliedArgs = numAppliedArgs;
      this.spanStart = spanStart;
      this.spanEnd = spanEnd;
    }

    public String getWord() {
      return word;
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
      result = prime * result + ((word == null) ? 0 : word.hashCode());
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
      if (word == null) {
        if (other.word != null)
          return false;
      } else if (!word.equals(other.word))
        return false;
      return true;
    }
  }
}
