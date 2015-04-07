package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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

  public List<List<String>> getNGrams(int n) {
    List<List<String>> ngrams = Lists.newArrayList();
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < words.size() - i; j++) {
        List<String> ngram = Lists.newArrayList();
        for (int k = 0; k < i + 1; k++) {
          ngram.add(words.get(j + k));
        }
        ngrams.add(ngram);
      }
    }
    return ngrams;
  }

  public ExpressionTree getTree() {
    return tree;
  }
}
