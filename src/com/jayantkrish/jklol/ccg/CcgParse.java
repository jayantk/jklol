package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;

public class CcgParse {

  // Syntactic category at the root of the parse tree.
  private final HeadedSyntacticCategory syntax;

  // The lexicon entry used to create this parse.
  // Non-null only when this is a terminal.
  private final CcgCategory lexiconEntry;
  // The words spanned by this portion of the parse tree.
  // Non-null only when this is a terminal.
  private final List<String> spannedWords;
  // The POS tag assigned to the words spanned by this tree.
  // Non-null only when this is a terminal.
  private final List<String> posTags;

  // The semantic heads of this part.
  private final Set<IndexedPredicate> heads;
  // The semantic dependencies instantiated at this node in the parse.
  private final List<DependencyStructure> dependencies;
  
  // Partially evaluated logical forms at this node.
  private final Expression evaluatedLogicalForm;

  // Probability represents the probability of the lexical entry if
  // this is a terminal, otherwise it represents the probability of
  // the generated dependencies.
  private final double probability;
  // Total probability of the parse subtree rooted at this node.
  private final double subtreeProbability;

  // If this is a nonterminal, the subtrees combined to produce this
  // tree.
  private final CcgParse left;
  private final CcgParse right;
  private final Combinator combinator;

  // If non-null, the unary rule applied to produce syntax, either
  // from lexiconEntry (if this is a terminal) or from left and right
  // (if this is a nonterminal).
  private final UnaryCombinator unaryRule;

  // Portion of the sentence spanned by this tree. Both spanStart and 
  // spanEnd are inclusive indexes, (i.e., a tree spanning one word has
  // spanStart == spanEnd).
  private final int spanStart;
  private final int spanEnd;

  /**
   * 
   * @param syntax
   * @param lexiconEntry
   * @param spannedWords
   * @param posTags
   * @param heads
   * @param dependencies
   * @param probability
   * @param left
   * @param right
   */
  private CcgParse(HeadedSyntacticCategory syntax, CcgCategory lexiconEntry,
      List<String> spannedWords, List<String> posTags, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, CcgParse left, CcgParse right,
      Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.lexiconEntry = lexiconEntry;
    this.spannedWords = spannedWords;
    this.posTags = posTags;
    this.heads = Preconditions.checkNotNull(heads);
    this.dependencies = Preconditions.checkNotNull(dependencies);

    this.probability = probability;

    this.left = left;
    this.right = right;
    this.combinator = combinator;

    this.unaryRule = unaryRule;
    this.spanStart = spanStart;
    this.spanEnd = spanEnd;

    // Both left and right must agree on null/non-null.
    Preconditions.checkArgument((left == null) ^ (right == null) == false);
    Preconditions.checkArgument(left == null || combinator != null);

    if (left != null) {
      this.subtreeProbability = left.getSubtreeProbability() * right.getSubtreeProbability() * probability;
    } else {
      this.subtreeProbability = probability;
    }
    
    this.evaluatedLogicalForm = initializeEvaluatedLogicalForm();
  }

  /**
   * Create a CCG parse for a terminal of the CCG parse tree. This
   * terminal parse represents using {@code lexiconEntry} as the
   * initial CCG category for {@code spannedWords}.
   * 
   * @param syntax
   * @param lexiconEntry
   * @param posTags
   * @param heads
   * @param deps
   * @param spannedWords
   * @param lexicalProbability
   * @return
   */
  public static CcgParse forTerminal(HeadedSyntacticCategory syntax, CcgCategory lexiconEntry,
      List<String> posTags, Set<IndexedPredicate> heads, List<DependencyStructure> deps,
      List<String> spannedWords, double lexicalProbability, UnaryCombinator unaryRule,
      int spanStart, int spanEnd) {
    return new CcgParse(syntax, lexiconEntry, spannedWords, posTags, heads, deps,
        lexicalProbability, null, null, null, unaryRule, spanStart, spanEnd);
  }

  public static CcgParse forNonterminal(HeadedSyntacticCategory syntax, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double dependencyProbability, CcgParse left,
      CcgParse right, Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd) {
    return new CcgParse(syntax, null, null, null, heads, dependencies, dependencyProbability, left,
        right, combinator, unaryRule, spanStart, spanEnd);
  }

  /**
   * Returns {@code true} if this parse represents a terminal in the
   * parse tree, i.e., a lexicon entry.
   * 
   * @return
   */
  public boolean isTerminal() {
    return left == null && right == null;
  }

  /**
   * Gets the CCG syntactic category at the root of the parse tree.
   * 
   * @return
   */
  public SyntacticCategory getSyntacticCategory() {
    return syntax.getSyntax();
  }

  public HeadedSyntacticCategory getHeadedSyntacticCategory() {
    return syntax;
  }

  /**
   * Gets the CCG unary rule applied to produce the syntactic category
   * at the root of this parse. If {@code null}, no unary rule was
   * applied.
   * 
   * @return
   */
  public UnaryCombinator getUnaryRule() {
    return unaryRule;
  }

  /**
   * Gets a representation of the syntactic structure of this parse,
   * omitting all semantic information.
   * 
   * @return
   */
  public CcgSyntaxTree getSyntacticParse() {
    SyntacticCategory originalSyntax = null;
    if (unaryRule != null) {
      originalSyntax = unaryRule.getUnaryRule().getInputSyntacticCategory().getSyntax();
    } else {
      originalSyntax = syntax.getSyntax();
    }

    if (isTerminal()) {
      return CcgSyntaxTree.createTerminal(syntax.getSyntax(), originalSyntax, spanStart, spanEnd, spannedWords, posTags);
    } else {
      CcgSyntaxTree leftTree = left.getSyntacticParse();
      CcgSyntaxTree rightTree = right.getSyntacticParse();

      return CcgSyntaxTree.createNonterminal(syntax.getSyntax(), originalSyntax, leftTree, rightTree);
    }
  }
  
  /**
   * Gets the logical form for this CCG parse.
   * 
   * @return
   */
  public Expression getLogicalForm() {
    return evaluatedLogicalForm;
    /*
    List<Expression> expressions = Lists.newArrayList();
    for (IndexedPredicate head : heads) {
      Expression expression = getLfFromWordIndex(head.getHeadIndex());
      if (expression != null) {
        expressions.add(expression);
      }
    }

    if (expressions.size() == 0) {
      return null;
    } else if (expressions.size() == 1) {
      return expressions.get(0);
    } else {
      ConstantExpression andExpr = new ConstantExpression("and");
      List<Expression> subexpressions = Lists.<Expression>newArrayList(andExpr);
      subexpressions.addAll(expressions);
      return new ApplicationExpression(subexpressions);
    } 
    */
  }

  /**
   * The result is null unless this is a terminal in the parse tree.
   * 
   * @return
   */
  public List<String> getWords() {
    return spannedWords;
  }

  public List<String> getSpannedPosTags() {
    if (isTerminal()) {
      return posTags;
    } else {
      List<String> tags = Lists.newArrayList();
      tags.addAll(left.getSpannedPosTags());
      tags.addAll(right.getSpannedPosTags());
      return tags;
    }
  }

  /**
   * Returns one POS tag per lexicon entry. Differs from
   * {@link #getSpannedPosTags()} because lexicon entries may
   * span multiple words. In these cases, only the last tag in 
   * the spanned sequence is included in the returned list.
   * 
   * @return
   */
  public List<String> getSpannedPosTagsByLexiconEntry() {
    if (isTerminal()) {
      return Arrays.asList(posTags.get(posTags.size() - 1));
    } else {
      List<String> tags = Lists.newArrayList();
      tags.addAll(left.getSpannedPosTags());
      tags.addAll(right.getSpannedPosTags());
      return tags;
    }
  }

  /**
   * Gets the CCG lexicon entries used for the words in this parse, in
   * left-to-right order.
   * 
   * @return
   */
  public List<LexiconEntry> getSpannedLexiconEntries() {
    if (isTerminal()) {
      return Arrays.asList(new LexiconEntry(spannedWords, lexiconEntry));
    } else {
      List<LexiconEntry> lexiconEntries = Lists.newArrayList();
      lexiconEntries.addAll(left.getSpannedLexiconEntries());
      lexiconEntries.addAll(right.getSpannedLexiconEntries());
      return lexiconEntries;
    }
  }

  /**
   * The result is null unless this is a terminal in the parse tree.
   * 
   * @return
   */
  public CcgCategory getLexiconEntry() {
    return lexiconEntry;
  }

  /**
   * Gets the left subtree of this parse.
   * 
   * @return
   */
  public CcgParse getLeft() {
    return left;
  }

  /**
   * Gets the right subtree of this parse.
   * 
   * @return
   */
  public CcgParse getRight() {
    return right;
  }

  /**
   * Gets the combinator used to produce this tree from its left and
   * right subtrees.
   * 
   * @return
   */
  public Combinator getCombinator() {
    return combinator;
  }

  /**
   * Gets the probability of the entire subtree of the CCG parse
   * headed at this node.
   * 
   * @return
   */
  public double getSubtreeProbability() {
    return subtreeProbability;
  }

  /**
   * Returns the probability of the dependencies / lexical entries
   * applied at this particular node.
   * 
   * @return
   */
  public double getNodeProbability() {
    return probability;
  }

  /**
   * Gets the semantic heads of this parse.
   * 
   * @return
   */
  public Set<IndexedPredicate> getSemanticHeads() {
    return heads;
  }
  
  public Set<Integer> getHeadWordIndexes() {
    Set<Integer> indexes = Sets.newHashSet();
    for (IndexedPredicate head : heads) {
      indexes.add(head.getHeadIndex());
    }
    return indexes;
  }

  /**
   * Returns dependency structures that were filled by the rule
   * application at this node only.
   * 
   * @return
   */
  public List<DependencyStructure> getNodeDependencies() {
    return dependencies;
  }

  /**
   * Gets all dependency structures populated during parsing.
   * 
   * @return
   */
  public List<DependencyStructure> getAllDependencies() {
    List<DependencyStructure> deps = Lists.newArrayList();
    if (!isTerminal()) {
      deps.addAll(left.getAllDependencies());
      deps.addAll(right.getAllDependencies());
    }
    deps.addAll(dependencies);
    return deps;
  }

  public Set<Integer> getWordIndexesProjectingDependencies() {
    List<DependencyStructure> deps = getAllDependencies();
    Set<Integer> wordIndexes = Sets.newHashSet();
    for (DependencyStructure dep : deps) {
      wordIndexes.add(dep.getHeadWordIndex());
    }
    return wordIndexes;
  }

  /**
   * Gets all filled dependencies projected by the lexical category
   * for the word at {@code wordIndex}. Expects
   * {@code 0 <= wordIndex < spannedWords.size()}. All returned
   * dependencies have {@code wordIndex} as their head word index.
   * 
   * @param wordIndex
   * @return
   */
  public List<DependencyStructure> getDependenciesWithHeadWord(int wordIndex) {
    List<DependencyStructure> deps = getAllDependencies();
    List<DependencyStructure> filteredDeps = Lists.newArrayList();
    for (DependencyStructure dep : deps) {
      if (dep.getHeadWordIndex() == wordIndex) {
        filteredDeps.add(dep);
      }
    }
    return filteredDeps;
  }

  public List<DependencyStructure> getDependenciesWithObjectWord(int wordIndex) {
    List<DependencyStructure> deps = getAllDependencies();
    List<DependencyStructure> filteredDeps = Lists.newArrayList();
    for (DependencyStructure dep : deps) {
      if (dep.getObjectWordIndex() == wordIndex) {
        filteredDeps.add(dep);
      }
    }
    return filteredDeps;
  }
  
  private Expression initializeEvaluatedLogicalForm() {
    if (isTerminal()) {
      return lexiconEntry.getLogicalForm();
    } else {
      if (getSemanticHeads().size() == 0) {
        return null;
      }
      // Get the expression which is the head of this parse
      int parseHeadWordIndex = getSemanticHeads().iterator().next().getHeadIndex();
      Expression result = getLfFromWordIndex(parseHeadWordIndex);
      if (result == null) {
        return null;
      }

      Expression leftLogicalForm = left.getLogicalForm();
      Expression rightLogicalForm = right.getLogicalForm();
      
      // Use the head words of the two parses to filter out long range
      // dependencies.
      /*
      Set<Integer> validHeads = Sets.newHashSet(left.getHeadWordIndexes());
      validHeads.addAll(right.getHeadWordIndexes());
      */

      for (DependencyStructure dep : dependencies) {
        int headIndex = dep.getHeadWordIndex();
        int objectIndex = dep.getObjectWordIndex();
        int argumentNumber = dep.getArgIndex();
        
        // if (validHeads.contains(headIndex) && validHeads.contains(objectIndex)) {
          Expression headLf = getLfFromWordIndex(headIndex);
          Expression objectLf = getLfFromWordIndex(objectIndex);
          System.out.println("  headIndex: " + parseHeadWordIndex + " " + getSemanticHeads());
          System.out.println("  dep: " + dep);
          System.out.println("Head lf: " + headLf);
          System.out.println("Object lf: " + objectLf);
          if (headLf != null && objectLf != null) {
            ConstantExpression constant = new ConstantExpression("$" + argumentNumber);
            if (headIndex == parseHeadWordIndex) {
              LambdaExpression resultAsLambda = (LambdaExpression) result;
              result = resultAsLambda.reduceArgument(constant, objectLf);
            } else if (objectIndex == parseHeadWordIndex) {
              LambdaExpression headAsLambda = (LambdaExpression) headLf;
              result = headAsLambda.reduceArgument(constant, result);
            }
          }
      // }
      }

      System.out.println(left.getSemanticHeads());
      System.out.println(leftLogicalForm);
      System.out.println(right.getSemanticHeads());
      System.out.println(rightLogicalForm);
      System.out.println(dependencies);

      return result;
    }
  }

  private Expression getLfFromWordIndex(int index) {
    Preconditions.checkArgument(spanStart <= index);
    Preconditions.checkArgument(index <= spanEnd);

    if (evaluatedLogicalForm != null || isTerminal()) {
      return evaluatedLogicalForm;
    } else {
      if (index <= left.spanEnd) {
        return left.getLfFromWordIndex(index);
      } else {
        return right.getLfFromWordIndex(index);
      }
    }
  }

  @Override
  public String toString() {
    if (left != null && right != null) {
      return "<" + syntax.getSyntax() + " " + left + " " + right + ">";
    } else {
      return "<" + syntax.getSyntax() + ">";
    }
  }
}
