package com.jayantkrish.jklol.inference;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Simplistic set of "max marginals" consisting of the single
 * maximum-probability assignment.
 * 
 * @author jayantk
 */
public class AssignmentMaxMarginalSet implements MaxMarginalSet {

  private final Assignment assignment;

  /**
   * {@code assignment} should contain all variables in the factor graph,
   * including any variables that were conditioned on before inference.
   * 
   * @param assignment
   */
  public AssignmentMaxMarginalSet(Assignment assignment) {
    this.assignment = Preconditions.checkNotNull(assignment);
  }

  @Override
  public int beamSize() {
    return 1;
  }

  @Override
  public Assignment getNthBestAssignment(int n) {
    Preconditions.checkArgument(n == 0);
    return assignment;
  }

  @Override
  public Assignment getNthBestAssignment(int n, Assignment portion) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor getMaxMarginal(VariableNumMap variables) {
    throw new UnsupportedOperationException();
  }
}
