package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgAndPattern implements CcgPattern {

  private final List<CcgPattern> patterns;
  
  public CcgAndPattern(List<CcgPattern> patterns) {
    this.patterns = ImmutableList.copyOf(patterns);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    boolean matches = true;
    for (CcgPattern pattern : patterns) {
      if (pattern.match(parse).size() == 0) {
        matches = false;
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
