package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class AlignmentExample {
  private final List<String> words;
  private final ExpressionTree tree;

  public AlignmentExample(List<String> words, ExpressionTree tree) {
    this.words = ImmutableList.copyOf(words);
    this.tree = Preconditions.checkNotNull(tree);
  }

  public List<String> getWords() {
    return words;
  }

  public ExpressionTree getTree() {
    return tree;
  }
}
