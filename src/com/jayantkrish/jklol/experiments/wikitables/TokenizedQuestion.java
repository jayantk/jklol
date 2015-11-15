package com.jayantkrish.jklol.experiments.wikitables;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class TokenizedQuestion {
  private final List<String> tokens;
  private final List<Integer> tokenStartIndexes;
  private final List<Integer> tokenEndIndexes;
  
  public TokenizedQuestion(List<String> tokens,
      List<Integer> tokenStartIndexes, List<Integer> tokenEndIndexes) {
    this.tokens = ImmutableList.copyOf(tokens);
    this.tokenStartIndexes = ImmutableList.copyOf(tokenStartIndexes);
    this.tokenEndIndexes = ImmutableList.copyOf(tokenEndIndexes);
  }
  
  public List<String> getTokens() {
    return tokens;
  }
  
  public List<Integer> getTokenStartIndexes() {
    return tokenStartIndexes;
  }
  
  public List<Integer> getTokenEndIndexes() {
    return tokenEndIndexes;
  }
}
