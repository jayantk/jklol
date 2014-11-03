package com.jayantkrish.jklol.ccg.pattern;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgUnionPattern implements CcgPattern {

  private final List<CcgPattern> patterns;
  
  public CcgUnionPattern(List<CcgPattern> patterns) {
    this.patterns = ImmutableList.copyOf(patterns);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    List<CcgParse> results = Lists.newArrayList();
    for (CcgPattern pattern : patterns) {
      results.addAll(pattern.match(parse));
    }
    return results;
  }
}
