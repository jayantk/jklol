package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgNotPattern implements CcgPattern {
  
  private final CcgPattern pattern;

  public CcgNotPattern(CcgPattern pattern) {
    this.pattern = Preconditions.checkNotNull(pattern);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    if (pattern.match(parse).size() > 0) {
      return Collections.emptyList();
    } else {
      return Arrays.asList(parse);
    }
  }
}
