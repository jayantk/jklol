package com.jayantkrish.jklol.ccg.gi;

import java.util.List;
import java.util.Set;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.Combinator;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.IndexedPredicate;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.ccg.UnaryCombinator;

public class GroundedCcgParse extends CcgParse {
  
  private final GroundedCcgParse myLeft;
  private final GroundedCcgParse myRight;

  protected GroundedCcgParse(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
      List<String> spannedWords, List<String> posTags, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, GroundedCcgParse left, GroundedCcgParse right,
      Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd) {
    super(syntax, lexiconEntry, spannedWords, posTags, heads, dependencies, probability, left, right,
        combinator, unaryRule, spanStart, spanEnd);
    
    myLeft = left;
    myRight = right;
  }
  
  public static GroundedCcgParse forTerminal(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
      List<String> posTags, Set<IndexedPredicate> heads, List<DependencyStructure> deps,
      List<String> spannedWords, double probability, UnaryCombinator unaryRule,
      int spanStart, int spanEnd) {
    return new GroundedCcgParse(syntax, lexiconEntry, spannedWords, posTags, heads, deps, probability,
        null, null, null, unaryRule, spanStart, spanEnd);
  }

  public static GroundedCcgParse forNonterminal(HeadedSyntacticCategory syntax, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, GroundedCcgParse left,
      GroundedCcgParse right, Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd) {
    return new GroundedCcgParse(syntax, null, null, null, heads, dependencies, probability, left,
        right, combinator, unaryRule, spanStart, spanEnd);
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
}
