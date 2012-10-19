package com.jayantkrish.jklol.models.dynamic;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * Implementations of common {@link VariablePattern} methods.
 * 
 * @author jayant
 */
public abstract class AbstractVariablePattern implements VariablePattern {

  private static final long serialVersionUID = 1L;

  @Override
  public VariableNumMap getAllMatchingVariables(VariableNumMap inputVariables) {
    List<VariableNumMap> allMatches = Lists.newArrayList();
    for (VariableMatch match : matchVariables(inputVariables)) {
      allMatches.add(match.getMatchedVariables());
    }
    return VariableNumMap.unionAll(allMatches);
  }
}
