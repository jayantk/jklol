package com.jayantkrish.jklol.p3;

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
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.util.IndexedList;

public class P3Parse extends CcgParse {
  
  private final P3Parse myLeft;
  private final P3Parse myRight;
  
  private final IncEvalState state;
  private final Object diagram;

  protected P3Parse(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
      List<String> spannedWords, List<String> posTags, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, P3Parse left, P3Parse right,
      Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd, IncEvalState state,
      Object diagram) {
    super(syntax, lexiconEntry, spannedWords, posTags, heads, dependencies, probability, left, right,
        combinator, unaryRule, spanStart, spanEnd);
    this.myLeft = left;
    this.myRight = right;
    this.state = state;
    this.diagram = diagram;
  }

  public static P3Parse forTerminal(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
      List<String> posTags, Set<IndexedPredicate> heads, List<DependencyStructure> deps,
      List<String> spannedWords, double probability, UnaryCombinator unaryRule,
      int spanStart, int spanEnd, IncEvalState state) {
    return new P3Parse(syntax, lexiconEntry, spannedWords, posTags, heads, deps, probability,
        null, null, null, unaryRule, spanStart, spanEnd, state, null);
  }

  public static P3Parse forNonterminal(HeadedSyntacticCategory syntax, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, P3Parse left,
      P3Parse right, Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd,
      IncEvalState state) {
    return new P3Parse(syntax, null, null, null, heads, dependencies, probability, left,
        right, combinator, unaryRule, spanStart, spanEnd, state, null);
  }
  
  public static P3Parse fromCcgParse(CcgParse parse) {
    if (parse.isTerminal()) {
      return new P3Parse(parse.getHeadedSyntacticCategory(),
          parse.getLexiconEntry(), parse.getWords(), parse.getPosTags(), parse.getSemanticHeads(),
          parse.getNodeDependencies(), parse.getNodeProbability(), null, null,
          null, parse.getUnaryRule(), parse.getSpanStart(), parse.getSpanEnd(), null, null);
    } else {
      P3Parse left = fromCcgParse(parse.getLeft());
      P3Parse right = fromCcgParse(parse.getRight());
      
      return new P3Parse(parse.getHeadedSyntacticCategory(),
          parse.getLexiconEntry(), parse.getWords(), parse.getPosTags(), parse.getSemanticHeads(),
          parse.getNodeDependencies(), parse.getNodeProbability(), left, right,
          parse.getCombinator(), parse.getUnaryRule(), parse.getSpanStart(), parse.getSpanEnd(),
          null, null);
    }
  }

  public P3Parse getParseForSpan(int spanStart, int spanEnd) {
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
  public P3Parse getLeft() {
    return myLeft;
  }

  /**
   * Gets the right subtree of this parse.
   * 
   * @return
   */
  public P3Parse getRight() {
    return myRight;
  }
  
  public P3Parse addUnaryRule(UnaryCombinator rule, HeadedSyntacticCategory newSyntax) {
    return new P3Parse(newSyntax, getLexiconEntry(), getWords(), getPosTags(), getSemanticHeads(),
        getNodeDependencies(), getNodeProbability(), getLeft(), getRight(), getCombinator(), rule,
        getSpanStart(), getSpanEnd(), state, diagram);
  }

  public Object getDenotation() {
    if (state != null) {
      return state.getDenotation();
    } else {
      return null;
    }
  }

  public Object getDiagram() {
    return diagram;
  }
  
  public IncEvalState getState() {
    return state;
  }

  /**
   * Gets the result of all evaluations anywhere in this tree.
   * 
   * @return
   */
  public List<IncEvalState> getStates() {
    List<IncEvalState> states = Lists.newArrayList();
    getStatesHelper(states);
    return states;
  }
  
  private void getStatesHelper(List<IncEvalState> states) {
    if (state != null) {
      states.add(state);
    }

    if (!isTerminal()) {
      myLeft.getStatesHelper(states);
      myRight.getStatesHelper(states);
    }
  }

  public P3Parse addDiagram(Object diagram) {
    return new P3Parse(getHeadedSyntacticCategory(), getLexiconEntry(), getWords(), getPosTags(), getSemanticHeads(),
        getNodeDependencies(), getNodeProbability(), getLeft(), getRight(), getCombinator(), getUnaryRule(),
        getSpanStart(), getSpanEnd(), state, diagram);
  }
  
  public P3Parse addState(IncEvalState newState, double newProb) {
    return new P3Parse(getHeadedSyntacticCategory(), getLexiconEntry(), getWords(), getPosTags(), getSemanticHeads(),
        getNodeDependencies(), newProb, getLeft(), getRight(), getCombinator(), getUnaryRule(),
        getSpanStart(), getSpanEnd(), newState, diagram);
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
    if (state != null) {
      String varName = "denotation" + newBindings.size();
      env.bindName(varName, state.getDenotation(), symbolTable);
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
