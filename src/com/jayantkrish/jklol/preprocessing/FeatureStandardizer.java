package com.jayantkrish.jklol.preprocessing;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Utilities for standardizing (centering and unit-variance-ing) features.
 * Instances of this class store a mean offset and a normalization constant for
 * each feature; these values are typically computed from the training data
 * features, and can be applied to the test features.
 * <p>
 * In order to handle bias features, this class uses a post-standardization
 * offset. (Standardization zeros out bias features.) The post-standardization
 * offset is added to the standardized features before they are returned.
 * 
 * @author jayantk
 */
public class FeatureStandardizer implements Serializable {
  private static final long serialVersionUID = -697677707530815873L;
  
  private final DiscreteFactor means;
  private final DiscreteFactor inverseStdDevs;
  private final DiscreteFactor finalOffset;
  
  private final double rescalingFactor;

  public FeatureStandardizer(DiscreteFactor means, DiscreteFactor inverseStdDevs,
      DiscreteFactor finalOffset, double rescalingFactor) {
    this.means = Preconditions.checkNotNull(means);
    this.inverseStdDevs = Preconditions.checkNotNull(inverseStdDevs);
    this.finalOffset = Preconditions.checkNotNull(finalOffset);
    
    this.rescalingFactor = rescalingFactor;
  }
  
  public DiscreteFactor getMeans() {
    return means;
  }
  
  public DiscreteFactor getInverseStdDevs() {
    return inverseStdDevs;
  }
  
  public DiscreteFactor getFinalOffset() {
    return finalOffset;
  }

  /**
   * Estimates standardization parameters (empirical feature means and
   * variances) from {@code featureFactor}. Each assignment to the 
   * non-feature variables in {@code featureFactor} corresponds to a 
   * feature vector.
   * 
   * @param featureFactor
   * @param featureVariableNum
   * @param biasFeature If {@code null} no bias feature is used.
   * @param rescalingFactor amount by which to multiply each feature vector after 
   * standardization.
   * @return
   */
  public static FeatureStandardizer estimateFrom(DiscreteFactor featureFactor, int featureVariableNum,
      Assignment biasFeature, double rescalingFactor) {
    return FeatureStandardizer.estimateFrom(Arrays.asList(featureFactor), featureVariableNum,
        biasFeature, rescalingFactor);
  }

  /**
   * Estimates standardization parameters (empirical feature means and
   * variances) from {@code featureFactors}. Each element of {@code featureFactors}
   * behaves like an independent set of feature vector observations; if these
   * factors contain variables other than the feature variable, then each
   * assignment to these variables defines a single feature vector.
   * 
   * @param featureFactor
   * @param featureVariableNum
   * @param biasFeature If {@code null} no bias feature is used.
   * @param rescalingFactor amount by which to multiply each feature vector after 
   * standardization.
   * @return
   */
  public static FeatureStandardizer estimateFrom(Collection<DiscreteFactor> featureFactors, 
      int featureVariableNum, Assignment biasFeature, double rescalingFactor) {
    Preconditions.checkArgument(featureFactors.size() > 0);
    
    DiscreteFactor means = getMeans(featureFactors, featureVariableNum);
    DiscreteFactor variances = getVariances(featureFactors, featureVariableNum);
    DiscreteFactor stdDev = new TableFactor(variances.getVars(), variances.getWeights().elementwiseSqrt());
    
    VariableNumMap featureVariable = Iterables.getFirst(featureFactors, null).getVars()
        .intersection(featureVariableNum);
    DiscreteFactor offset = null;
    if (biasFeature == null || biasFeature.size() == 0) {
      offset = TableFactor.zero(featureVariable);
    } else {
      offset = TableFactor.pointDistribution(featureVariable, biasFeature);
    }

    return new FeatureStandardizer(means, stdDev.inverse(), offset, rescalingFactor);
  }

  /**
   * Applies this standardization procedure to the features in
   * {@code featureFactor}.
   * 
   * @param featureFactor
   * @return
   */
  public DiscreteFactor apply(DiscreteFactor featureFactor) {
    return featureFactor.add(means.product(-1.0)).product(inverseStdDevs).add(finalOffset)
        .product(rescalingFactor);
  }

  /**
   * Gets the mean value of each assignment to {@code featureVariableNum} in
   * {@code featureFactor}.
   * 
   * @param featureFactor
   * @param featureVariableNum
   * @return
   */
  public static DiscreteFactor getMeans(DiscreteFactor featureFactor, int featureVariableNum) {
    return getMeans(Arrays.asList(featureFactor), featureVariableNum);
  }
  
  /**
   * Gets the mean value of each assignment to {@code featureVariableNum} in
   * {@code featureFactors}. Each factor in {@code featureFactors} must have
   * the same variable type at index {@code featureVariableNum}.
   * 
   * @param featureFactors
   * @param featureVariableNum
   * @return
   */
  public static DiscreteFactor getMeans(Collection<DiscreteFactor> featureFactors,
      int featureVariableNum) {
    Preconditions.checkArgument(featureFactors.size() > 0);
    DiscreteFactor sums = null;
    int numEntries = 0;
    for (DiscreteFactor featureFactor : featureFactors) {
      // Calculate the number of feature vectors contained in this factor.
      VariableNumMap nonFeatureVars = featureFactor.getVars().removeAll(featureVariableNum);
      numEntries += nonFeatureVars.getNumberOfPossibleAssignments();
      
      DiscreteFactor featureSums = featureFactor.marginalize(nonFeatureVars);
      if (sums == null) {
        sums = featureSums;
      } else {
        sums = sums.add(featureSums);
      }
    }

    return sums.product(1.0 / numEntries);    
  }

  /**
   * Gets the variance of the values of each assignment to
   * {@code featureVariableNum} in {@code featureFactor}.
   * 
   * @param featureFactor
   * @param featureVariableNum
   * @return
   */
  public static DiscreteFactor getVariances(DiscreteFactor featureFactor, int featureVariableNum) {
    return getVariances(Arrays.asList(featureFactor), featureVariableNum);
  }

  /**
   * Gets the variance of the values of each assignment to
   * {@code featureVariableNum} in {@code featureFactors}.
   * 
   * @param featureFactors
   * @param featureVariableNum
   * @return
   */
  public static DiscreteFactor getVariances(Collection<DiscreteFactor> featureFactors,
      int featureVariableNum) {
    DiscreteFactor means = getMeans(featureFactors, featureVariableNum);
    
    DiscreteFactor sumSquares = null;
    int numEntries = 0;
    for (DiscreteFactor featureFactor : featureFactors) {
      VariableNumMap nonFeatureVars = featureFactor.getVars().removeAll(featureVariableNum);
      numEntries += nonFeatureVars.getNumberOfPossibleAssignments();

      DiscreteFactor factorSumSquares = featureFactor.product(featureFactor)
          .marginalize(nonFeatureVars);
      if (sumSquares == null) {
        sumSquares = factorSumSquares;
      } else {
        sumSquares = sumSquares.add(factorSumSquares);
      }
    }
    
    return sumSquares.add(means.product(means).product(-1.0 * numEntries))
        .product(1.0 / numEntries);
  }

  /**
   * Computes the empirical mean of the features in {@code featureFactor}, then
   * subtracts that mean from each feature.
   * 
   * @param featureFactor
   * @param featureVariableNum
   * @return
   */
  public static DiscreteFactor centerFeatures(DiscreteFactor featureFactor, int featureVariableNum) {
    return featureFactor.add(getMeans(featureFactor, featureVariableNum).product(-1.0)).coerceToDiscrete();
  }

  /**
   * Computes the empirical variance of the features in {@code featureFactor}
   * and normalizes each feature such that its empirical variance is 1.
   * 
   * @param featureFactor
   * @param featureVariableNum
   * @return
   */
  public static DiscreteFactor normalizeVariance(DiscreteFactor featureFactor, int featureVariableNum) {
    DiscreteFactor variance = getVariances(featureFactor, featureVariableNum);
    DiscreteFactor stdDev = new TableFactor(variance.getVars(), variance.getWeights().elementwiseSqrt());

    return featureFactor.product(stdDev.inverse());
  }
}
