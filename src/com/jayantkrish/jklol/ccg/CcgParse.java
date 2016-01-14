package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

/**
 * A syntactic and semantic parse of a text.
 *  
 * @author jayant
 *
 */
public class CcgParse {

  // Syntactic category at the root of the parse tree.
  private final HeadedSyntacticCategory syntax;

  // The lexicon entry used to create this parse.
  // Non-null only when this is a terminal.
  private final LexiconEntryInfo lexiconEntry;

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
  // spanEnd are inclusive indexes, (i.e., a tree spanning one word
  // has spanStart == spanEnd).
  private final int spanStart;
  private final int spanEnd;

  private CcgParse(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
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
   * @param probability
   * @param unaryRule
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public static CcgParse forTerminal(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
      List<String> posTags, Set<IndexedPredicate> heads, List<DependencyStructure> deps,
      List<String> spannedWords, double probability, UnaryCombinator unaryRule,
      int spanStart, int spanEnd) {
    return new CcgParse(syntax, lexiconEntry, spannedWords, posTags, heads, deps, probability,
        null, null, null, unaryRule, spanStart, spanEnd);
  }

  public static CcgParse forNonterminal(HeadedSyntacticCategory syntax, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, CcgParse left,
      CcgParse right, Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd) {
    return new CcgParse(syntax, null, null, null, heads, dependencies, probability, left,
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
  
  public boolean hasUnaryRule() {
    return unaryRule != null;
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
  
  public int getSpanStart() {
    return spanStart;
  }
  
  public int getSpanEnd() {
    return spanEnd;
  }
  
  public CcgParse getParseForSpan(int spanStart, int spanEnd) {
    if (!isTerminal()) {
      if (left.spanStart <= spanStart && left.spanEnd >= spanEnd) {
        return left.getParseForSpan(spanStart, spanEnd);
      } else if (right.spanStart <= spanStart && right.spanEnd >= spanEnd) {
        return right.getParseForSpan(spanStart, spanEnd);
      }
    }

    // Either this is a terminal, or neither the left nor right subtrees
    // completely contain the given span.
    return this;
  }

  /**
   * Gets a representation of the syntactic structure of this parse,
   * omitting all semantic information.
   * 
   * @return
   */
  public CcgSyntaxTree getSyntacticParse() {
    HeadedSyntacticCategory originalSyntax = null;
    if (unaryRule != null) {
      originalSyntax = unaryRule.getUnaryRule().getInputSyntacticCategory().getCanonicalForm();
    } else {
      originalSyntax = syntax;
    }

    if (isTerminal()) {
      return CcgSyntaxTree.createTerminal(syntax.getSyntax(), originalSyntax.getSyntax(),
          spanStart, spanEnd, spannedWords, posTags, originalSyntax);
    } else {
      CcgSyntaxTree leftTree = left.getSyntacticParse();
      CcgSyntaxTree rightTree = right.getSyntacticParse();

      return CcgSyntaxTree.createNonterminal(syntax.getSyntax(), originalSyntax.getSyntax(),
          leftTree, rightTree);
    }
  }

  /**
   * Gets the logical form for this CCG parse.
   * 
   * @return
   */
  public Expression2 getLogicalForm() {
    Expression2 preUnaryLogicalForm = getPreUnaryLogicalForm();
    if (unaryRule == null) {
      return preUnaryLogicalForm;
    } else if (preUnaryLogicalForm == null || unaryRule.getUnaryRule().getLogicalForm() == null) {
      return null;
    } else {
      return Expression2.nested(unaryRule.getUnaryRule().getLogicalForm(), preUnaryLogicalForm);
    }
  }

  /**
   * Returns the logical form for the smallest subtree of the parse which
   * completely contains the given span.
   * 
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public SpannedExpression getLogicalFormForSpan(int spanStart, int spanEnd) {
    CcgParse spanningParse = getParseForSpan(spanStart, spanEnd);
    Expression2 lf = spanningParse.getPreUnaryLogicalForm();
    if (lf != null) {
      return new SpannedExpression(spanningParse.getHeadedSyntacticCategory(),
          spanningParse.getLogicalForm(), spanningParse.getSpanStart(), spanningParse.getSpanEnd());
    } else {
      return null;
    }
  }

  /**
   * Gets the logical forms for every subspan of this parse tree.
   * Many of the returned logical forms combine with each other 
   * during the parse of the sentence.
   * 
   * @return
   */
  public List<SpannedExpression> getSpannedLogicalForms() {
    List<SpannedExpression> spannedExpressions = Lists.newArrayList();
    getSpannedLogicalFormsHelper(spannedExpressions);
    return spannedExpressions;
  }

  private void getSpannedLogicalFormsHelper(List<SpannedExpression> spannedExpressions) {
    Expression2 logicalForm = getLogicalForm();
    if (logicalForm != null) {
      spannedExpressions.add(new SpannedExpression(syntax, logicalForm, spanStart, spanEnd));
    } 
    Expression2 preUnaryLogicalForm = getPreUnaryLogicalForm();

    if (preUnaryLogicalForm != null && unaryRule != null) {
      spannedExpressions.add(new SpannedExpression(unaryRule.getInputType().getCanonicalForm(),
          preUnaryLogicalForm, spanStart, spanEnd));
    }

    if (!isTerminal()) {
      left.getSpannedLogicalFormsHelper(spannedExpressions);
      right.getSpannedLogicalFormsHelper(spannedExpressions);
    }
  }

  /**
   * Gets the logical form for this parse without applying the unary
   * rule (if any) at the root.
   * 
   * @return
   */
  private Expression2 getPreUnaryLogicalForm() {
    if (isTerminal()) {
      Expression2 logicalForm = lexiconEntry.getCategory().getLogicalForm();
      return logicalForm;
    } else {
      Expression2 leftLogicalForm = left.getLogicalForm();
      Expression2 rightLogicalForm = right.getLogicalForm();

      if (leftLogicalForm == null || rightLogicalForm == null) {
        return null;
      }

      if (combinator.getBinaryRule() != null) {
        // Binary rules have a two-argument function that is applied to the 
        // left and right logical forms to produce the answer.
        Expression2 combinatorExpression = combinator.getBinaryRule().getLogicalForm();
        if (combinatorExpression != null) {
          return Expression2.nested(Expression2.nested(combinatorExpression, leftLogicalForm),
              rightLogicalForm);
        }

      } else if (leftLogicalForm != null && rightLogicalForm != null) {
        // Function application or composition.
        Expression2 functionLogicalForm = null;
        Expression2 argumentLogicalForm = null;
        if (combinator.isArgumentOnLeft()) {
          functionLogicalForm = rightLogicalForm;
          argumentLogicalForm = leftLogicalForm;
        } else {
          functionLogicalForm = leftLogicalForm;
          argumentLogicalForm = rightLogicalForm;
        }

        int numArgsToKeep = combinator.getArgumentReturnDepth();
        if (numArgsToKeep == 0) {
          // Function application
          return Expression2.nested(functionLogicalForm, argumentLogicalForm);
        } else {
          // Composition.
          List<String> remainingArgsRenamed = StaticAnalysis.getNewVariableNames(numArgsToKeep, argumentLogicalForm);
          List<Expression2> remainingArgExpressions = Expression2.constants(remainingArgsRenamed);
          List<Expression2> applicationExpressions = Lists.newArrayList(argumentLogicalForm);
          applicationExpressions.addAll(remainingArgExpressions);
          Expression2 argumentApplication = Expression2.nested(applicationExpressions);

          Expression2 body = Expression2.nested(functionLogicalForm, argumentApplication);
          List<Expression2> functionExpressions = Lists.newArrayList();
          functionExpressions.add(Expression2.constant("lambda"));
          functionExpressions.addAll(remainingArgExpressions);
          functionExpressions.add(body);
          return Expression2.nested(functionExpressions);
        }
      }

      // Unknown combinator.
      return null;
    }
  }

  /**
   * Get the words mapped to this node of the parse tree in 
   * the CCG parse. The returned words are taken directly from
   * the input sentence, without any normalization that may
   * have been performed to map the words to a lexicon entry. 
   * 
   * The result is null unless this node is a terminal. See
   * {@link getSpannedWords} to get all of the words spanned
   * by a node, i.e., the words from every terminal child of
   * the node. 
   * 
   * @return
   */
  public List<String> getWords() {
    return spannedWords;
  }

  /**
   * Gets the part-of-speech tags mapped to this node in the
   * parse tree.
   * 
   * The result is null unless this node is a terminal. See
   * {@link getSpannedLexiconPosTags} to get all of the
   * part-of-speech tags spanned by a node, i.e., the tags
   * from every terminal child of the node.
   * 
   * @return
   */
  public List<String> getPosTags() {
    return posTags;
  }

  /**
   * Gets all of the words spanned by this node of the parse tree,
   * in sentence order.
   * 
   * @return
   */
  public List<String> getSpannedWords() {
    if (isTerminal()) {
      return spannedWords;
    } else {
      List<String> words = Lists.newArrayList();
      words.addAll(left.getSpannedWords());
      words.addAll(right.getSpannedWords());
      return words;
    }
  }

  /**
   * Gets all of the part-of-speech tags spanned by this node of
   * the parse tree, in sentence order.
   * 
   * @return
   */
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
   * Gets the lexicon entries for all terminal children of this
   * parse tree node, in left-to-right order.
   * 
   * @return
   */
  public List<LexiconEntryInfo> getSpannedLexiconEntries() {
    if (isTerminal()) {
      return Arrays.asList(lexiconEntry);
    } else {
      List<LexiconEntryInfo> lexiconEntries = Lists.newArrayList();
      lexiconEntries.addAll(left.getSpannedLexiconEntries());
      lexiconEntries.addAll(right.getSpannedLexiconEntries());
      return lexiconEntries;
    }
  }

  /**
   * If this is a terminal, gets the lexicon entry that created
   * the terminal. Returns {@code null} if this is not a terminal.
   * See {@link getSpannedLexiconEntries} to get all of the lexicon
   * entries spanned by a node, i.e., the entries for for every
   * terminal child of the node.
   * 
   * @return
   */
  public LexiconEntryInfo getLexiconEntry() {
    return lexiconEntry;
  }

  public LexiconEntryInfo getLexiconEntryForWordIndex(int index) {
    Preconditions.checkArgument(spanStart <= index && index <= spanEnd, 
        "Illegal word index: %s (current span: %s,%s)", index, spanStart, spanEnd);
    if (isTerminal()) {
      return lexiconEntry;
    } else {
      if (index <= left.spanEnd) {
        return left.getLexiconEntryForWordIndex(index);
      } else {
        return right.getLexiconEntryForWordIndex(index);
      }
    }
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

  /**
   * Gets all dependency structures populated during parsing, indexed
   * by the word that projects the dependency.
   * 
   * @return
   */
  public Multimap<Integer, DependencyStructure> getAllDependenciesIndexedByHeadWordIndex() {
    Multimap<Integer, DependencyStructure> map = HashMultimap.create();
    for (DependencyStructure dep : getAllDependencies()) {
      map.put(dep.getHeadWordIndex(), dep);
    }
    return map;
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

  public List<DependencyStructure> getDependenciesWithHeadInSpan(int spanStart, int spanEnd) {
    List<DependencyStructure> deps = getAllDependencies();
    List<DependencyStructure> filteredDeps = Lists.newArrayList();
    for (DependencyStructure dep : deps) {
      if (dep.getHeadWordIndex() >= spanStart && dep.getHeadWordIndex() <= spanEnd) {
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

  public CcgParse addUnaryRule(UnaryCombinator rule, HeadedSyntacticCategory newSyntax) {
    return new CcgParse(newSyntax, lexiconEntry, spannedWords, posTags, heads, dependencies,
        probability, left, right, combinator, rule, spanStart, spanEnd);
  }

  /**
   * Returns a representation of this tree in HTML format.
   * 
   * @return
   */
  public String toHtmlString() {
    StringBuilder sb = new StringBuilder();
    toHtmlStringHelper(sb);
    return sb.toString();
  }
  
  private void toHtmlStringHelper(StringBuilder sb) {
    String syntaxString = syntax.getSyntax().toString();
    Expression2 logicalForm = getLogicalForm();
    // TODO: static analysis simplification here.
    String lfString = logicalForm != null ? logicalForm.toString() : "";

    /*
    if (unaryRule != null) {
      syntaxString += "_" + unaryRule.getUnaryRule().getInputSyntacticCategory();
    }
    */
    if (left != null && right != null) {
      sb.append("<div class=\"nonterminalNode\">");
      sb.append("<div class=\"leftTree\">");
      left.toHtmlStringHelper(sb);
      sb.append("</div>");
      
      sb.append("<div class=\"rightTree\">");
      right.toHtmlStringHelper(sb);
      sb.append("</div>");
      
      sb.append("<div class=\"clear\">");
      sb.append("<p class=\"nonterminalSyntax\">");
      sb.append(syntaxString);
      sb.append("</p>");

      sb.append("<p class=\"nonterminalLf\">");
      sb.append(lfString);
      sb.append("</p>");
      sb.append("</div>");
      
      sb.append("</div>");
    } else {
      sb.append("<div class=\"terminalNode\">");
      sb.append("<p class=\"terminalWords\">");
      sb.append(Joiner.on(" ").join(spannedWords));
      sb.append("</p>");

      sb.append("<p class=\"terminalSyntax\">");
      sb.append(syntaxString);
      sb.append("</p>");
      
      sb.append("<p class=\"terminalLf\">");
      sb.append(lfString);
      sb.append("</p>");
      
      sb.append("</div>");
    }
  }

  @Override
  public String toString() {
    String syntaxString = syntax.toString();
    if (unaryRule != null) {
      syntaxString += "_" + unaryRule.getUnaryRule().getInputSyntacticCategory();
    }

    if (left != null && right != null) {
      return "<" + syntaxString + " " + left + " " + right + ">";
    } else {
      return "<" + syntaxString + ">";
    }
  }
  
  public static class SpannedExpression {
    private final HeadedSyntacticCategory syntax;
    private final Expression2 expression;
    private final int spanStart;
    private final int spanEnd;

    public SpannedExpression(HeadedSyntacticCategory syntax, Expression2 expression, int spanStart,
        int spanEnd) {
      this.syntax = Preconditions.checkNotNull(syntax);
      this.expression = Preconditions.checkNotNull(expression);
      this.spanStart = spanStart;
      this.spanEnd = spanEnd;
    }

    public HeadedSyntacticCategory getSyntax() {
      return syntax;
    }
    
    public Expression2 getExpression() {
      return expression;
    }
    
    public int getSpanStart() {
      return spanStart;
    }
    
    public int getSpanEnd() {
      return spanEnd;
    }
    
    @Override
    public String toString() {
      return spanStart + "," + spanEnd + ": " + expression.toString();
    }
  }
}
