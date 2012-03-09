package com.jayantkrish.jklol.evaluation;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A {@link Predictor} which is a probabilistic mixture of several underlying
 * predictors.
 * 
 * <p>
 * This predictor can be used to perform smoothing by mixing a trained, MLE
 * predictor with {@link Baselines#uniform()}. The mixing proportion parameter
 * of this class determines the number of virtual counts added during smoothing.
 * 
 * @author jayantk
 */
public class MixturePredictor<I, O> extends AbstractPredictor<I, O> {

  private final ImmutableList<Predictor<I, O>> predictors;
  private final ImmutableList<Double> mixingProportions;
  private final double mixingProportionsDenominator;

  /**
   * Creates a predictor which mixes {@code predictors} with the given
   * {@code mixingProportions}. The entries of {@code mixingProportions} do not
   * need to sum to 1; they are automatically normalized by this class.
   * 
   * @param predictors
   * @param mixingProportions
   */
  public MixturePredictor(List<Predictor<I, O>> predictors, List<Double> mixingProportions) {
    Preconditions.checkArgument(predictors.size() == mixingProportions.size());
    this.predictors = ImmutableList.copyOf(predictors);
    this.mixingProportions = ImmutableList.copyOf(mixingProportions);

    double denominator = 0.0;
    for (Double mixingProportion : mixingProportions) {
      denominator += mixingProportion;
    }
    this.mixingProportionsDenominator = denominator;
    Preconditions.checkArgument(mixingProportionsDenominator > 0);
  }

  @Override
  public O getBestPrediction(I input) {
    // Implementing this method requires adding some new functionality to the
    // Predictor interface, specifically the ability to get all possible
    // predictions of a predictor.
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public List<O> getBestPredictions(I input, int numBest) {
    // Implementing this method requires adding some new functionality to the
    // Predictor interface.
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public double getProbability(I input, O output) {
    double unnormalizedProbability = 0.0;
    for (int i = 0; i < predictors.size(); i++) {
      unnormalizedProbability += predictors.get(i).getProbability(input, output) *
          mixingProportions.get(i);
    }
    return unnormalizedProbability / mixingProportionsDenominator;
  }

  /**
   * Gets the predictors which are mixed to create the predictions of
   * {@code this}.
   * 
   * @return
   */
  public ImmutableList<Predictor<I, O>> getPredictors() {
    return predictors;
  }

  /**
   * Gets the proportions with which {@code this.getPredictors()} are mixed
   * together. The returned proportions sum to 1.
   * 
   * @return
   */
  public List<Double> getMixingProportions() {
    List<Double> proportions = Lists.newArrayList();
    for (Double mixingProportion : mixingProportions) {
      proportions.add(mixingProportion / mixingProportionsDenominator);
    }
    return proportions;
  }
}
