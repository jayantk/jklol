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

  public SufficientStatistics getGradient() {
    return gradient;
  }

  public void setObjectiveValue(double newObjectiveValue) {
    objectiveValue = newObjectiveValue;
  }

  public void incrementObjectiveValue(double increment) {
    objectiveValue += increment;
  }

  public double getObjectiveValue() {
    return objectiveValue;
  }

  public int getSearchErrors() {
    return searchErrors;
  }

  public void incrementSearchErrors(int increment) {
    searchErrors += increment;
  }

  public void increment(GradientEvaluation other){
    gradient.increment(other.gradient, 1.0);
    objectiveValue += other.objectiveValue;
    searchErrors += other.searchErrors;
  }
}
