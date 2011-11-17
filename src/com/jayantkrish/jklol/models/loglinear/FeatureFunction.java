package com.jayantkrish.jklol.models.loglinear;

import java.util.Iterator;
import java.util.List;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A FeatureFunction is a feature in the model.
 */
public interface FeatureFunction {

  /**
   * The value of the feature for a particular assignment to variable values.
   */
  public double getValue(Assignment assignment);

  /**
   * An iterator over all assignments for which the feature has a non-zero
   * value.
   */
  public Iterator<Assignment> getNonzeroAssignments();

  /**
   * The varnums which this feature operates on, returned in sorted order.
   */
  public List<Integer> getVarNums();

  /**
   * Gets the expected value of this feature on the conditional distribution
   * {@code factor}, conditioned on {@code assignment}. If {@code factor} is
   * unnormalized, then the returned value is an unnormalized expectation.
   * 
   * Taken together, {@code factor} and {@code assignment} define a disjoint
   * partition of {@code this.getVarNums()}.
   * 
   * @param factor
   * @return
   */
  public double computeExpectation(Factor factor, Assignment assignment);
}