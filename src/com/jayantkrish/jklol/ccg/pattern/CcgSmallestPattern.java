package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgSmallestPattern implements CcgPattern {

  private final CcgPattern pattern;
  
  public CcgSmallestPattern(CcgPattern pattern) {
    this.pattern = Preconditions.checkNotNull(pattern);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    List<CcgParse> patternMatches = pattern.match(parse);
    
    int minLength = Integer.MAX_VALUE;
    CcgParse bestMatch = null;
    for (CcgParse patternMatch : patternMatches) {
      int length = patternMatch.getSpannedWords().size();
      if (length < minLength) {
        minLength = length;
        bestMatch = patternMatch;
      }
    }
    
    if (bestMatch != null) {
      return Arrays.asList(bestMatch);
    } else {
      return Collections.emptyList();
    }
  }
}
