package com.jayantkrish.jklol.cfg;

import java.util.List;

import com.google.common.base.Preconditions;

/**
 * Training example for a CFG parser.
 * 
 * @author jayant
 *
 */
public class CfgExample {

  private final List<Object> words;
  private final CfgParseTree parse;
  
  public CfgExample(List<Object> words, CfgParseTree parse) {
    this.words = Preconditions.checkNotNull(words);
    this.parse = Preconditions.checkNotNull(parse);
  }

  public List<Object> getWords() {
    return words;
  }

  public CfgParseTree getParseTree() {
    return parse;
  }
}
