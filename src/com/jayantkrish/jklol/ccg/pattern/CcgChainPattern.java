package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgChainPattern implements CcgPattern {

  private final List<CcgPattern> patterns;
  
  public CcgChainPattern(List<CcgPattern> patterns) {
    this.patterns = ImmutableList.copyOf(patterns);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    List<CcgParse> results = Arrays.asList(parse);
    for (CcgPattern pattern : patterns) {
      List<CcgParse> nextResults = Lists.newArrayList();
      for (CcgParse result : results) {
        nextResults.addAll(pattern.match(result));
      }
      results = nextResults;
    }

    return results;
  }
}
