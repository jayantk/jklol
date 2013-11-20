package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * The evaluation of the gradient of a function, along with
 * the function value and other useful statistics. 
 * {@code GradientEvaluation} is mutable and intended to be used
 * as an accumulator.
 * 
 * @author jayant
 */
public class GradientEvaluation {
  private SufficientStatistics gradient;
  private double objectiveValue;
  private int searchErrors;

  public GradientEvaluation(SufficientStatistics gradient, double objectiveValue, 
      int searchErrors) {
    this.gradient = gradient;
    this.objectiveValue = objectiveValue;
    this.searchErrors = searchErrors;
  }

  public final SufficientStatistics getGradient() {
    return gradient;
  }

  public final void setObjectiveValue(double newObjectiveValue) {
    objectiveValue = newObjectiveValue;
  }

  public final void incrementObjectiveValue(double increment) {
    objectiveValue += increment;
  }

  public final double getObjectiveValue() {
    return objectiveValue;
  }

  public final int getSearchErrors() {
    return searchErrors;
  }

  public final void incrementSearchErrors(int increment) {
    searchErrors += increment;
  }

  public final void increment(GradientEvaluation other) {
    gradient.increment(other.gradient, 1.0);
    objectiveValue += other.objectiveValue;
    searchErrors += other.searchErrors;
  }
}
