package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgWordPattern implements CcgPattern {

  private final List<String> tokens;

  public CcgWordPattern(List<String> tokens) {
    this.tokens = ImmutableList.copyOf(tokens);
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    if (tokens.equals(parse.getSpannedWords())) {
      return Arrays.asList(parse);
    }
    return Collections.<CcgParse>emptyList();
  }
}
