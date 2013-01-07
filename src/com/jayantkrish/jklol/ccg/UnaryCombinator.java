package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;

import com.google.common.base.Preconditions;

public class UnaryCombinator implements Serializable {
  private static final long serialVersionUID = 1L;
  
  private final int syntax;
  private final int[] syntaxUniqueVars;

  private final int[] variableRelabeling;
  
  private final CcgUnaryRule unaryRule;
  private final HeadedSyntacticCategory inputType;
  
  public UnaryCombinator(HeadedSyntacticCategory inputType, int syntax, int[] syntaxUniqueVars,
      int[] variableRelabeling, CcgUnaryRule unaryRule) {
    this.inputType = Preconditions.checkNotNull(inputType);
    this.syntax = syntax;
    this.syntaxUniqueVars = Preconditions.checkNotNull(syntaxUniqueVars);
    this.variableRelabeling = Preconditions.checkNotNull(variableRelabeling);
    this.unaryRule = Preconditions.checkNotNull(unaryRule);
  }
  
  public HeadedSyntacticCategory getInputType() {
    return inputType;
  }

  public int getSyntax() {
    return syntax;
  }

  public int[] getSyntaxUniqueVars() {
    return syntaxUniqueVars;
  }

  public int[] getVariableRelabeling() {
    return variableRelabeling;
  }
  
  public CcgUnaryRule getUnaryRule() {
    return unaryRule;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((inputType == null) ? 0 : inputType.hashCode());
    result = prime * result + syntax;
    result = prime * result + Arrays.hashCode(syntaxUniqueVars);
    result = prime * result + ((unaryRule == null) ? 0 : unaryRule.hashCode());
    result = prime * result + Arrays.hashCode(variableRelabeling);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    UnaryCombinator other = (UnaryCombinator) obj;
    if (inputType == null) {
      if (other.inputType != null)
        return false;
    } else if (!inputType.equals(other.inputType))
      return false;
    if (syntax != other.syntax)
      return false;
    if (!Arrays.equals(syntaxUniqueVars, other.syntaxUniqueVars))
      return false;
    if (unaryRule == null) {
      if (other.unaryRule != null)
        return false;
    } else if (!unaryRule.equals(other.unaryRule))
      return false;
    if (!Arrays.equals(variableRelabeling, other.variableRelabeling))
      return false;
    return true;
  }
}
