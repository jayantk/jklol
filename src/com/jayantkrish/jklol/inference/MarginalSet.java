package com.jayantkrish.jklol.inference;

import java.util.Collection;

import com.jayantkrish.jklol.models.Factor;

/**
 * A set of (possibly approximate) marginal distributions over a set of
 * variables.
 * 
 * @author jayant
 */
public interface MarginalSet {

  /**
   * Gets the unnormalized marginal distribution associated with the given
   * variables as a {@link Factor}.
   * <p>
   * Note that the sum of the unnormalized probabilities in the returned
   * marginal may not equal the partition function of the entire graphical
   * model, as returned by {@link getPartitionFunction}. This possibility occurs
   * when the graphical model includes mixture nodes (i.e., nodes where
   * probabilities sum over a set of disjoint outcomes). An example of a model
   * containing mixture nodes is the graphical model representing parse trees of
   * a context-free grammar. In most cases, {@link getPartitionFunction} should
   * be used to normalize the factors returned by this method, even if the
   * resulting probabilities sum to less than one.
   * 
   * @param varNums
   * @return
   */
  public Factor getMarginal(Collection<Integer> varNums);

  /**
   * Gets the partition function for the graphical model, which is the
   * normalizing constant for the unnormalized marginals returned by
   * {@link getMarginal}. As noted in {@code getMarginal}, the partition
   * function may not be equal to the sum of the outcomes in an unnormalized
   * marginal.
   * 
   * @return
   */
  public double getPartitionFunction();
}
