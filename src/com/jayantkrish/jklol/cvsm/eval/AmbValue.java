package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * A nondeterministic value that takes on values
 * from a discrete set.
 * 
 * @author jayantk
 */
public class AmbValue {

  private final VariableNumMap factorGraphVariable;

  public AmbValue(VariableNumMap factorGraphVariable) {
    Preconditions.checkArgument(factorGraphVariable.size() == 1);
    this.factorGraphVariable = Preconditions.checkNotNull(factorGraphVariable);
  }

  public VariableNumMap getVar() {
    return factorGraphVariable;
  }

  public List<Object> getPossibleValues() {
    return factorGraphVariable.getDiscreteVariables().get(0).getValues();
  }
}
