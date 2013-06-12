package com.jayantkrish.jklol.cvsm.ccg;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;

public class CcgParseAugmenter {
  
  private final List<CategoryPattern> patterns;
  
  public CcgParseAugmenter(List<CategoryPattern> patterns) {
    this.patterns = ImmutableList.copyOf(patterns);
  }

  public static CcgParseAugmenter parseFrom(List<String> lines) {
    List<CategoryPattern> patterns = Lists.newArrayList();
    for (String line : lines) {
      patterns.add(SemTypePattern.parseFrom(line));
    }

    return new CcgParseAugmenter(patterns);
  }
  
  public CcgParse addLogicalForms(CcgParse input) {
    if (input.isTerminal()) {
      HeadedSyntacticCategory cat = input.getHeadedSyntacticCategory();
      for (CategoryPattern pattern : patterns) {
        if (pattern.matches(input.getWords(), cat.getSyntax())) {
          CcgCategory currentEntry = input.getLexiconEntry();
          CcgCategory lexiconEntry = new CcgCategory(cat, pattern.getLogicalForm(input.getWords(), cat.getSyntax()), currentEntry.getSubjects(), 
              currentEntry.getArgumentNumbers(), currentEntry.getObjects(), currentEntry.getAssignment());
          
          return CcgParse.forTerminal(cat, lexiconEntry, input.getLexiconTriggerWords(), 
              input.getSpannedPosTags(), input.getSemanticHeads(), input.getNodeDependencies(), 
              input.getWords(), input.getNodeProbability(), input.getUnaryRule(),
              input.getSpanStart(), input.getSpanEnd()); 
        }
      }
      throw new IllegalArgumentException("Unsupported category: " + cat);
    } else {
      CcgParse left = addLogicalForms(input.getLeft());
      CcgParse right = addLogicalForms(input.getRight());
      return CcgParse.forNonterminal(input.getHeadedSyntacticCategory(), input.getSemanticHeads(),
          input.getNodeDependencies(), input.getNodeProbability(), left, right, input.getCombinator(),
          input.getUnaryRule(), input.getSpanStart(), input.getSpanEnd());
    }
  }
}
