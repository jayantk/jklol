package com.jayantkrish.jklol.training;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.inference.FactorMarginalSet;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;

/**
 * Trains a {@link #ParametricFactorGraph} using empirical outcome counts from a data set.
 */
public class CountTrainer {

  /**
   * Selects a model from the set of {@code bn} by counting the outcome
   * occurrences in {@code trainingData} and constructing a parameter vector
   * from the result.
   * 
   * <p>
   * Note that the returned estimates are unsmoothed; to get smoothed estimates,
   * use {@link SufficientStatistics#increment(double)} on the result.
   * 
   * @param bn
   * @param trainingData
   */
  public SufficientStatistics train(ParametricFactorGraph bn, List<DynamicAssignment> trainingData) {
    // Instantiate a factor graph using an arbitrary set of parameters.
    // We must instantiate a graph in order to handle dynamic factor graphs,
    // as dynamic factor graphs have assignments of variable size. 
    SufficientStatistics parameters = bn.getNewSufficientStatistics();
    DynamicFactorGraph factorGraph = bn.getModelFromParameters(parameters);

    // Compute sufficient statistics for all examples in parallel.
    SufficientStatisticsBatch result = MapReduceConfiguration.getMapReduceExecutor()
        .mapReduce(trainingData,
            new SufficientStatisticsMapper(factorGraph, new AssignmentMarginalCalculator(), new NullLogFunction()),
            new SufficientStatisticsReducer(bn, parameters));
    return result.getStatistics();
  }
  
  /**
   * {@link MarginalCalculator} which simply verifies that all variables in the
   * passed-in factor graph have been conditioned on, then returns marginals based
   * on the conditioned values.
   * 
   * @author jayant
   */
  private static class AssignmentMarginalCalculator implements MarginalCalculator {
    private static final long serialVersionUID = 1L;

    @Override
    public MarginalSet computeMarginals(FactorGraph factorGraph) {
      Preconditions.checkArgument(factorGraph.getVariables().size() == 0);
      return FactorMarginalSet.fromAssignment(factorGraph.getConditionedVariables(), 
          factorGraph.getConditionedValues(), 1.0);
    }
    
    @Override
    public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph) {
      throw new UnsupportedOperationException();
    }
  }
}