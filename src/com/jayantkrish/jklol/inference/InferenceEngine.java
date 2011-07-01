package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.models.*;

import java.util.Map;
import java.util.List;

/**
 * An InferenceEngine is an algorithm for computing marginal distributions
 * of a factor graph.
 */
public interface InferenceEngine {

    /**
     * Give the inference engine a factor graph to perform
     * inference on.
     */
    public void setFactorGraph(FactorGraph f);

    /**
     * Compute (unconditional) marginal distributions over the factors in the factor graph. Computed
     * marginals are stored for retrieval (by getMarginal()) until they are recomputed.
     */
    public void computeMarginals();

    /**
     * Compute marginals conditioned on the provided variable assignments.  Passing an empty
     * assignment results in unconditional marginals.
     */
    public void computeMarginals(Assignment assignment);

    /**
     * Compute unconditional max marginals. Again, these marginals can be retrieved by calling
     * getMarginal().
     */
    public void computeMaxMarginals();

    /**
     * Compute max marginals conditioned on th provided assignment.
     */
    public void computeMaxMarginals(Assignment assignment);
    

    /**
     * Retrieve an already computed marginal distribution.     
     */
    public Factor getMarginal(List<Integer> varNums);

}