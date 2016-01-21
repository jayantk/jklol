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

  protected GroundedCcgParse(HeadedSyntacticCategory syntax, LexiconEntryInfo lexiconEntry,
      List<String> spannedWords, List<String> posTags, Set<IndexedPredicate> heads,
      List<DependencyStructure> dependencies, double probability, GroundedCcgParse left, GroundedCcgParse right,
      Combinator combinator, UnaryCombinator unaryRule, int spanStart, int spanEnd) {
    super(syntax, lexiconEntry, spannedWords, posTags, heads, dependencies, probability, left, right,
        combinator, unaryRule, spanStart, spanEnd);
  }

  
}
