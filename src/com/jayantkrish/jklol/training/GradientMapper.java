package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.training.GradientMapper.GradientEvaluation;

/**
 * Mapper for parallelizing gradient / objective function computation for
 * multiple examples.
 * 
 * @author jayantk
 */
public class GradientMapper<M, E> extends Mapper<E, GradientEvaluation> {
  private final M instantiatedModel;
  private final GradientOracle<M, E> oracle;
  private final LogFunction log;

  public GradientMapper(M instantiatedModel, GradientOracle<M, E> oracle, LogFunction log) {
    this.instantiatedModel = instantiatedModel;
    this.oracle = oracle;
    this.log = log;
  }

  @Override
  public GradientEvaluation map(E item) {
    log.startTimer("compute_gradient_(parallel)");
    double objective = 0.0;
    SufficientStatistics gradient = oracle.initializeGradient();
    try {
      objective += oracle.accumulateGradient(gradient, instantiatedModel, item, log);
    } catch (ZeroProbabilityError e) {
      // Ignore the example, returning the zero vector.
    }
    log.stopTimer("compute_gradient_(parallel)");
    return new GradientEvaluation(gradient, objective);
  }

  public static class GradientEvaluation {
    private SufficientStatistics gradient;
    private double objectiveValue;

    public GradientEvaluation(SufficientStatistics gradient, double objectiveValue) {
      this.gradient = gradient;
      this.objectiveValue = objectiveValue;
    }

    public SufficientStatistics getGradient() {
      return gradient;
    }
    
    public void incrementGradient(SufficientStatistics other) {
      gradient.increment(other, 1.0);
    }

    public double getObjectiveValue() {
      return objectiveValue;
    }
    
    public void incrementObjectiveValue(double amount) {
      objectiveValue += amount;
    }
  }
}
