package com.jayantkrish.jklol.inference;

import java.util.Collection;
import java.util.Set;

import com.jayantkrish.jklol.models.Factor;

/**
 * A set of (possibly approximate) marginal distributions over a set of
 * variables.
 * 
 * @author jayant
 */
public interface MarginalSet {
  
  /**
   * Gets the variables which this marginal distribution is defined over.
   *
   * @return
   */
  public Set<Integer> getVarNums();

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
