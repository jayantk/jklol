package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;

public class CcgParse {

  private final HeadedSyntacticCategory syntax;
  
  // The lexicon entry used to create this parse.
  // Non-null only when this is a terminal.
  private final CcgCategory lexiconEntry;
  // The words spanned by this portion of the parse tree. 
  // Non-null only when this is a terminal.
  private final List<String> spannedWords;

  // The semantic heads of this part.
  private final Set<IndexedPredicate> heads;
  // The semantic dependencies instantiated at this node in the parse.
  private final List<DependencyStructure> dependencies;

  // Probability represents the probability of the lexical entry if this is a
  // terminal,
  // otherwise it represents the probability of the generated dependencies.
  private final double probability;
  // Total probability of the parse subtree rooted at this node.
  private final double subtreeProbability;

  private final CcgParse left;
  private final CcgParse right;

  /**
   * 
   * @param syntax
   * @param lexiconEntry
   * @param spannedWords
   * @param heads
   * @param dependencies
   * @param probability
   * @param left
   * @param right
   */
  private CcgParse(HeadedSyntacticCategory syntax, CcgCategory lexiconEntry, List<String> spannedWords, 
      Set<IndexedPredicate> heads, List<DependencyStructure> dependencies, double probability,
      CcgParse left, CcgParse right) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.lexiconEntry = lexiconEntry;
    this.spannedWords = spannedWords;
    this.heads = Preconditions.checkNotNull(heads);
    this.dependencies = Preconditions.checkNotNull(dependencies);

    this.probability = probability;
    
    this.left = left;
    this.right = right;

    // Both left and right must agree on null/non-null.
    Preconditions.checkArgument((left == null) ^ (right == null) == false);
    
    if (left != null) {
      this.subtreeProbability = left.getSubtreeProbability() * right.getSubtreeProbability() * probability;
    } else {
      this.subtreeProbability = probability;
    }
  }

  /**
   * Create a CCG parse for a terminal of the CCG parse tree. This terminal 
   * parse represents using {@code lexiconEntry} as the initial CCG category for
   * {@code spannedWords}.
   * 
   * @param syntax
   * @param lexiconEntry
   * @param heads
   * @param deps
   * @param spannedWords
   * @param lexicalProbability
   * @return
   */
  public static CcgParse forTerminal(HeadedSyntacticCategory syntax, CcgCategory lexiconEntry, 
      Set<IndexedPredicate> heads, List<DependencyStructure> deps, List<String> spannedWords,
      double lexicalProbability) {
    return new CcgParse(syntax, lexiconEntry, spannedWords, heads, deps, lexicalProbability,
        null, null);
  }

  public static CcgParse forNonterminal(HeadedSyntacticCategory syntax, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double dependencyProbability, CcgParse left, CcgParse right) {
    return new CcgParse(syntax, null, null, heads, dependencies, dependencyProbability, left, right);
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
   * The result is null unless this is a terminal in the parse tree.
   *  
   * @return
   */
  public List<String> getSpannedWords() {
    return spannedWords;
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
   * Gets the probability of the entire subtree of the CCG parse headed at this
   * node.
   * 
   * @return
   */
  public double getSubtreeProbability() {
    return subtreeProbability;
  }

  /**
   * Returns the probability of the dependencies / lexical entries applied at
   * this particular node.
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

  /**
   * Returns dependency structures that were filled by the rule application at
   * this node only.
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
    List<DependencyStructure> deps = Lists.newArrayList(dependencies);

    if (!isTerminal()) {
      deps.addAll(left.getAllDependencies());
      deps.addAll(right.getAllDependencies());
    }
    return deps;
  }

  @Override
  public String toString() {
    if (left != null && right != null) {
      return "<" + syntax.getSyntax() + ", " + left + ", " + right + ">";
    } else {
      return "<" + syntax.getSyntax() + ">";
    }
  }
}
