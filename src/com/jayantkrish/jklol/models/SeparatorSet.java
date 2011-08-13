package com.jayantkrish.jklol.models;

import com.google.common.base.Preconditions;

/**
 * A directed edge connecting two {@link Factor}s in a {@link FactorGraph}. The
 * {@code SeparatorSet} represents the variables which are shared by adjacent
 * factors. In message-passing inference algorithms (e.g., {@link JunctionTree}
 * ), messages are sent along the separator sets of the factor graph. {@code
 * SeparatorSets} are directed in order to track the direction of these
 * messages.
 * 
 * @author jayant
 */
public class SeparatorSet {

  private final int startFactor;
  private final int endFactor;
  private final VariableNumMap sharedVars;

  /**
   * Creates a {@code SeparatorSet} out of the variables shared by {@code
   * startFactor} and {@code endFactor}. A message sent along this {@code
   * SeparatorSet} is passed from {@code startFactor} to {@code endFactor}.
   * 
   * @param startFactor
   * @param endFactor
   * @param sharedVars
   */
  public SeparatorSet(int startFactor, int endFactor, VariableNumMap sharedVars) {
    this.startFactor = Preconditions.checkNotNull(startFactor);
    this.endFactor = Preconditions.checkNotNull(endFactor);
    this.sharedVars = Preconditions.checkNotNull(sharedVars);
  }

  public int getStartFactor() {
    return startFactor;
  }

  public int getEndFactor() {
    return endFactor;
  }

  public VariableNumMap getSharedVars() {
    return sharedVars;
  }
  
  @Override
  public String toString() {
    return startFactor + " --> " + endFactor + " (" + sharedVars + ")"; 
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + endFactor;
    result = prime * result + ((sharedVars == null) ? 0 : sharedVars.hashCode());
    result = prime * result + startFactor;
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
    SeparatorSet other = (SeparatorSet) obj;
    if (endFactor != other.endFactor)
      return false;
    if (sharedVars == null) {
      if (other.sharedVars != null)
        return false;
    } else if (!sharedVars.equals(other.sharedVars))
      return false;
    if (startFactor != other.startFactor)
      return false;
    return true;
  }
}
