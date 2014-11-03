package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgIfPattern implements CcgPattern {

  private final CcgPattern pattern;
  private final CcgPattern replacement;

  public CcgIfPattern(CcgPattern pattern, CcgPattern replacement) {
    this.pattern = Preconditions.checkNotNull(pattern);
    this.replacement = Preconditions.checkNotNull(replacement);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    if (pattern.match(parse).size() > 0) {
      return replacement.match(parse); 
    } else {
      return Arrays.asList(parse);
    }
  }
}
