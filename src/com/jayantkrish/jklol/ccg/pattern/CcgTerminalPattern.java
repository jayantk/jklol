package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParse;

public class CcgTerminalPattern implements CcgPattern {
  
  private final boolean matchTerminal;
  
  public CcgTerminalPattern(boolean matchTerminal) {
    this.matchTerminal = matchTerminal;
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    if (parse.isTerminal() == matchTerminal) {
      return Arrays.asList(parse);
    } else {
      return Collections.emptyList();
    }
  }
}
