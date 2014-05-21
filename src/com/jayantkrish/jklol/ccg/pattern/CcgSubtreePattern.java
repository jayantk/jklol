package com.jayantkrish.jklol.ccg.pattern;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.IndexedPredicate;

public class CcgSubtreePattern implements CcgPattern {
  
  private final CcgPattern pattern;
  private final boolean matchSameHead;
  private final boolean returnWholeTree;

  public CcgSubtreePattern(CcgPattern pattern, boolean matchSameHead, boolean returnWholeTree) {
    this.pattern = Preconditions.checkNotNull(pattern);
    this.matchSameHead = matchSameHead;
    this.returnWholeTree = returnWholeTree;
  }

  @Override
  public List<CcgParse> match(CcgParse parse) {
    List<CcgParse> matches = Lists.newArrayList();
    Set<IndexedPredicate> heads = parse.getSemanticHeads();

    matchHelper(matches, heads, parse);
    if (returnWholeTree) {
      return Arrays.asList(parse);
    } else {
      return matches;
    }
  }

  private final void matchHelper(List<CcgParse> accumulator, Set<IndexedPredicate> heads,
      CcgParse parse) {
    if (!matchSameHead || heads.containsAll(parse.getSemanticHeads())) {
      accumulator.addAll(pattern.match(parse));
    }

    if (!parse.isTerminal()) {
      matchHelper(accumulator, heads, parse.getLeft());
      matchHelper(accumulator, heads, parse.getRight());
    }
  }
}
