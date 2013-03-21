package com.jayantkrish.jklol.boost;

import java.io.Serializable;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * A weighted collection of model parameters. Each element of the
 * collection is a set of parameters ({@code SufficientStatistics})
 * for the same model.
 * 
 * @author jayantk
 */
public class SufficientStatisticsEnsemble implements Serializable {
  private static final long serialVersionUID = 1L;

  private final List<SufficientStatistics> statistics;
  private final List<Double> weights;

  public SufficientStatisticsEnsemble(List<SufficientStatistics> statistics, List<Double> weights) {
    this.statistics = Preconditions.checkNotNull(statistics);
    this.weights = Preconditions.checkNotNull(weights);
    Preconditions.checkArgument(statistics.size() == weights.size());
  }

  public List<SufficientStatistics> getStatistics() {
    return statistics;
  }

  public List<Double> getStatisticWeights() {
    return weights;
  }
  
  public void addStatistics(SufficientStatistics statistic, double weight) {
    statistics.add(statistic);
    weights.add(weight);
  }
}
