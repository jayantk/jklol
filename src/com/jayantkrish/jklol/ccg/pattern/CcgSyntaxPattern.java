package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;

public class CcgSyntaxPattern implements CcgPattern {
  
  private final HeadedSyntacticCategory syntax;
  
  public CcgSyntaxPattern(HeadedSyntacticCategory syntax) {
    this.syntax = Preconditions.checkNotNull(syntax).getCanonicalForm();
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    if (syntax.isUnifiableWith(parse.getHeadedSyntacticCategory())) {
      return Arrays.asList(parse);
    } 
    return Collections.emptyList();
  }
}
