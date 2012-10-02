package com.jayantkrish.jklol.models.dynamic;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * Pattern that identifies a fixed set of variables, represented by a {@code VariableNumMap}.
 * 
 * @author jayant
 */
public class WrapperVariablePattern extends AbstractVariablePattern {

  private final VariableNumMap variables;
  
  public WrapperVariablePattern(VariableNumMap variables) {
    this.variables = variables;
  }
  
  @Override
  public List<VariableMatch> matchVariables(VariableNumMap inputVariables) {
    if (inputVariables.containsAll(variables)) {
      return Lists.newArrayList(new VariableMatch(variables));
    } else {
      return Collections.emptyList();
    }
  }
}
