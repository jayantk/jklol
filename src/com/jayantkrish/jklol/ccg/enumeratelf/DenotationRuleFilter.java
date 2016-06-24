package com.jayantkrish.jklol.ccg.enumeratelf;


public class DenotationRuleFilter implements EnumerationRuleFilter {
  
  public DenotationRuleFilter() {}

  @Override
  public boolean apply(LfNode original, LfNode result) {
    if (!original.getType().equals(result.getType())) {
      return true;
    }

    return !original.getDenotation().equals(result.getDenotation());
  }
}
