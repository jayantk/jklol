package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgOrPattern implements CcgPattern {

  private final List<CcgPattern> patterns;
  
  public CcgOrPattern(List<CcgPattern> patterns) {
    this.patterns = ImmutableList.copyOf(patterns);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    boolean matches = false;
    for (CcgPattern pattern : patterns) {
      if (pattern.match(parse).size() > 0) {
        matches = true;
        break;
      }
    }

    if (matches) {
      return Arrays.asList(parse);
    } else {
      return Collections.emptyList();
    }
  }
}
