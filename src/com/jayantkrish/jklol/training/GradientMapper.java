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
  private final GradientOracle<M, ? super E> oracle;
  private final LogFunction log;

  public GradientMapper(M instantiatedModel, GradientOracle<M, ? super E> oracle, LogFunction log) {
    this.instantiatedModel = instantiatedModel;
    this.oracle = oracle;
    this.log = log;
  }

  @Override
  public GradientEvaluation map(E item) {
    log.startTimer("gradient_mapper");
    double objective = 0.0;
    int searchErrors = 0;
    SufficientStatistics gradient = oracle.initializeGradient();
    try {
      objective += oracle.accumulateGradient(gradient, instantiatedModel, item, log);
    } catch (ZeroProbabilityError e) {
      // Ignore the example, returning the zero vector.
      searchErrors = 1;
    }
    log.stopTimer("gradient_mapper");
    return new GradientEvaluation(gradient, objective, searchErrors);
  }

  public static class GradientEvaluation {
    private SufficientStatistics gradient;
    private double objectiveValue;
    private int searchErrors;

    public GradientEvaluation(SufficientStatistics gradient, double objectiveValue, 
        int searchErrors) {
      this.gradient = gradient;
      this.objectiveValue = objectiveValue;
      this.searchErrors = searchErrors;
    }

    public SufficientStatistics getGradient() {
      return gradient;
    }
    
    public void setObjectiveValue(double newObjectiveValue) {
      this.objectiveValue = newObjectiveValue;
    }
        
    public double getObjectiveValue() {
      return objectiveValue;
    }

    public int getSearchErrors() {
      return searchErrors;
    }
    
    public void increment(GradientEvaluation other){
      gradient.increment(other.gradient, 1.0);
      objectiveValue += other.objectiveValue;
      searchErrors += other.searchErrors;
    }
  }
}
