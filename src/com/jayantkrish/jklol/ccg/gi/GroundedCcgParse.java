package com.jayantkrish.jklol.ccg.gi;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.Combinator;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.IndexedPredicate;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.ccg.UnaryCombinator;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.util.IndexedList;

public class GroundedCcgParse extends CcgParse {
  
  private final GroundedCcgParse myLeft;
  private final GroundedCcgParse myRight;
  
  private final Object denotation;
  private final Object diagram;

  protected GroundedCcgParse(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
      List<String> spannedWords, List<String> posTags, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, GroundedCcgParse left, GroundedCcgParse right,
      Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd, Object denotation,
      Object diagram) {
    super(syntax, lexiconEntry, spannedWords, posTags, heads, dependencies, probability, left, right,
        combinator, unaryRule, spanStart, spanEnd);
    this.myLeft = left;
    this.myRight = right;
    this.denotation = denotation;
    this.diagram = diagram;
  }

  public static GroundedCcgParse forTerminal(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
      List<String> posTags, Set<IndexedPredicate> heads, List<DependencyStructure> deps,
      List<String> spannedWords, double probability, UnaryCombinator unaryRule,
      int spanStart, int spanEnd, Object denotation) {
    return new GroundedCcgParse(syntax, lexiconEntry, spannedWords, posTags, heads, deps, probability,
        null, null, null, unaryRule, spanStart, spanEnd, denotation, null);
  }

  public static GroundedCcgParse forNonterminal(HeadedSyntacticCategory syntax, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, GroundedCcgParse left,
      GroundedCcgParse right, Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd,
      Object denotation) {
    return new GroundedCcgParse(syntax, null, null, null, heads, dependencies, probability, left,
        right, combinator, unaryRule, spanStart, spanEnd, denotation, null);
  }

  public GroundedCcgParse getParseForSpan(int spanStart, int spanEnd) {
    if (!isTerminal()) {
      if (getLeft().getSpanStart() <= spanStart && getLeft().getSpanEnd() >= spanEnd) {
        return getLeft().getParseForSpan(spanStart, spanEnd);
      } else if (getRight().getSpanStart() <= spanStart && getRight().getSpanEnd() >= spanEnd) {
        return getRight().getParseForSpan(spanStart, spanEnd);
      }
    }

    // Either this is a terminal, or neither the left nor right subtrees
    // completely contain the given span.
    return this;
  }

  /**
   * Gets the left subtree of this parse.
   * 
   * @return
   */
  public GroundedCcgParse getLeft() {
    return myLeft;
  }

  /**
   * Gets the right subtree of this parse.
   * 
   * @return
   */
  public GroundedCcgParse getRight() {
    return myRight;
  }
  
  public GroundedCcgParse addUnaryRule(UnaryCombinator rule, HeadedSyntacticCategory newSyntax) {
    return new GroundedCcgParse(newSyntax, getLexiconEntry(), getWords(), getPosTags(), getSemanticHeads(),
        getNodeDependencies(), getNodeProbability(), getLeft(), getRight(), getCombinator(), rule,
        getSpanStart(), getSpanEnd(), denotation, diagram);
  }

  public Object getDenotation() {
    return denotation;
  }
  
  public Object getDiagram() {
    return diagram;
  }

  public GroundedCcgParse addDiagram(Object diagram) {
    return new GroundedCcgParse(getHeadedSyntacticCategory(), getLexiconEntry(), getWords(), getPosTags(), getSemanticHeads(),
        getNodeDependencies(), getNodeProbability(), getLeft(), getRight(), getCombinator(), getUnaryRule(),
        getSpanStart(), getSpanEnd(), denotation, diagram);
  }

  /**
   * Gets an expression that evaluates to the denotation of
   * this parse. The expression will not re-evaluate any 
   * already evaluated subexpressions of this parse. {@code env}
   * may be extended with additional variable bindings to
   * capture denotations of already-evaluated subparses.
   * 
   * @param env
   * @param symbolTable
   * @return
   */
  public Expression2 getUnevaluatedLogicalForm(Environment env, IndexedList<String> symbolTable) {
    List<String> newBindings = Lists.newArrayList();
    return getUnevaluatedLogicalForm(env, symbolTable, newBindings);
  }

  private Expression2 getUnevaluatedLogicalForm(Environment env,
      IndexedList<String> symbolTable, List<String> newBindings) {
    if (denotation != null) {
      String varName = "denotation:" + newBindings.size();
      env.bindName(varName, denotation, symbolTable);
      newBindings.add(varName);
      return Expression2.constant(varName);
    }
    
    if (isTerminal()) {
      return getLogicalForm();
    } else {
      Expression2 leftLf = myLeft.getUnevaluatedLogicalForm(env, symbolTable, newBindings);
      Expression2 rightLf = myRight.getUnevaluatedLogicalForm(env, symbolTable, newBindings);
      
      return getCombinatorLogicalForm(leftLf, rightLf);
    }
  }
}
