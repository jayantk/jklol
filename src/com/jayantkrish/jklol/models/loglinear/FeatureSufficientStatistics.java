package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.models.CoercionError;
import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * {@link SufficientStatistics} for {@link LogLinearFactor}s. This class stores
 * expected counts for a set of feature functions.
 * 
 * @author jayantk
 */
public class FeatureSufficientStatistics implements SufficientStatistics {

  private final ImmutableList<FeatureFunction> features;
  private final double[] weights;

  /**
   * Creates a {@code FeatureSufficientStatistics} containing {@code features}.
   * Each feature has 0 weight.
   * 
   * @param features
   */
  public FeatureSufficientStatistics(List<FeatureFunction> features) {
    this.features = ImmutableList.copyOf(features);
    this.weights = new double[features.size()];
    Arrays.fill(weights, 0.0);
  }

  /**
   * Creates a {@code FeatureSufficientStatistics} containing {@code features}.
   * The {@code i}th feature in {@code features} has weight {@code weights[i]}.
   * 
   * Note that {@code weights} are not defensively copied (for efficiency), and
   * therefore should not be modified by the caller after being used in this
   * constructor.
   * 
   * @param features
   */
  public FeatureSufficientStatistics(List<FeatureFunction> features, double[] weights) {
    this.features = ImmutableList.copyOf(features);
    this.weights = Preconditions.checkNotNull(weights);
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    FeatureSufficientStatistics otherFeatures = other.coerceToFeature();
    // Really, the two sufficient statistics must have the exact same set of
    // features, but this check is going to be less expensive.
    Preconditions.checkArgument(otherFeatures.features.size() == this.features.size());
    for (int i = 0; i < weights.length; i++) {
      weights[i] += otherFeatures.weights[i] * multiplier;
    }
  }

  @Override
  public void increment(double amount) {
    for (int i = 0; i < weights.length; i++) {
      weights[i] += amount;
    }
  }
  
  @Override
  public void multiply(double amount) {
    for (int i = 0; i < weights.length; i++) {
      weights[i] *= amount;
    }
  }
  
  @Override
  public double getL2Norm() {
    double norm = 0.0;
    for (int i = 0; i < weights.length; i++) {
      norm += weights[i] * weights[i];
    }
    return Math.sqrt(norm);
  }

  @Override
  public FeatureSufficientStatistics coerceToFeature() {
    return this;
  }

  @Override
  public Cpt coerceToCpt() {
    throw new CoercionError("Cannot coerce FeatureSufficientStatistics to CPT");
  }

  @Override
  public ListSufficientStatistics coerceToList() {
    throw new CoercionError("Cannot coerce FeatureSufficientStatistics to List");
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < features.size(); i++) {
      if (i != 0) {
        sb.append(",");
      }
      sb.append(features.get(i));
      sb.append("=");
      sb.append(weights[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Gets the features with weights in {@code this}.
   * 
   * @return
   */
  public List<FeatureFunction> getFeatures() {
    return features;
  }

  /**
   * Gets the weights associated with the features in {@code this}. The
   * {@code i}th weight corresponds to the {@code i}th feature in
   * {@link #getFeatures()}.
   * 
   * The returned weights are not defensively copied and should not be modified
   * by the caller.
   * 
   * @return
   */
  public double[] getWeights() {
    return weights;
  }
}
