package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public class CcgLogicalFormPattern implements CcgPattern {
  private final String lfRegex;
  
  public CcgLogicalFormPattern(String lfRegex) {
    this.lfRegex = Preconditions.checkNotNull(lfRegex);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    Expression2 lf = parse.getLogicalForm();
    if (lf != null && Pattern.matches(lfRegex, lf.toString())) {
      return Arrays.asList(parse);
    } else {
      return Collections.emptyList();
    }
  }
}
