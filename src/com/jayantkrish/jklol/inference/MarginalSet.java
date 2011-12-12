package com.jayantkrish.jklol.inference;

import java.util.Collection;

import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A set of (possibly approximate) conditional marginal distributions over a set
 * of variables. The distribution is conditioned the assignment given by
 * {@link #getConditionedValues()}. {@code MarginalSet} is immutable.
 * 
 * @author jayant
 */
public interface MarginalSet {

  /**
   * Gets the variables that this marginal distribution is defined over. The
   * returned variables include any variables whose values have deterministic
   * assignments (see {@link #getConditionedValues()}).
   * 
   * @return
   */
  public VariableNumMap getVariables();

  /**
   * Gets the assignments to the variables which this set of marginals
   * conditions on.
   * 
   * @return
   */
  public Assignment getConditionedValues();

  /**
   * Gets the unnormalized marginal distribution associated with the given
   * variables as a {@link Factor}. {@link #getPartitionFunction()} returns the
   * normalization constant required to convert the values of the returned
   * factor into a probability distribution.
   * 
   * @param varNums
   * @return
   */
  public Factor getMarginal(Collection<Integer> varNums);

  /**
   * Gets the partition function for the graphical model, which is the
   * normalizing constant for the unnormalized marginals returned by
   * {@link getMarginal}.
   * 
   * @return
   */
  public double getPartitionFunction();
}
