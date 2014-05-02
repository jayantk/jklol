package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgLogicalFormPattern implements CcgPattern {
  private final String lfRegex;
  
  public CcgLogicalFormPattern(String lfRegex) {
    this.lfRegex = Preconditions.checkNotNull(lfRegex);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    if (Pattern.matches(lfRegex, parse.getLogicalForm(false).toString())) {
      return Arrays.asList(parse);
    } else {
      return Collections.emptyList();
    }
  }
}
