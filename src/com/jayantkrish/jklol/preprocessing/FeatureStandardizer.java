package com.jayantkrish.jklol.preprocessing;

import java.io.Serializable;

import com.google.common.base.Preconditions;
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

  public FeatureStandardizer(DiscreteFactor means, DiscreteFactor inverseStdDevs,
      DiscreteFactor finalOffset) {
    this.means = Preconditions.checkNotNull(means);
    this.inverseStdDevs = Preconditions.checkNotNull(inverseStdDevs);
    this.finalOffset = Preconditions.checkNotNull(finalOffset);
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
   * variances) from {@code featureFactor}.
   * 
   * @param featureFactor
   * @param featureVariableNum
   * @param biasFeature
   * @return
   */
  public static FeatureStandardizer estimateFrom(DiscreteFactor featureFactor, int featureVariableNum,
      Assignment biasFeature) {
    DiscreteFactor means = getMeans(featureFactor, featureVariableNum);
    DiscreteFactor variances = getVariances(featureFactor, featureVariableNum);
    DiscreteFactor stdDev = new TableFactor(variances.getVars(), variances.getWeights().elementwiseSqrt());
    
    VariableNumMap featureVariable = featureFactor.getVars().intersection(featureVariableNum);
    DiscreteFactor offset = null;
    if (biasFeature == null || biasFeature.size() == 0) {
      offset = TableFactor.zero(featureVariable);
    } else {
      offset = TableFactor.pointDistribution(featureVariable, biasFeature);
    }

    return new FeatureStandardizer(means, stdDev.inverse(), offset);
  }

  /**
   * Applies this standardization procedure to the features in
   * {@code featureFactor}.
   * 
   * @param featureFactor
   * @return
   */
  public DiscreteFactor apply(DiscreteFactor featureFactor) {
    return featureFactor.add(means.product(-1.0)).product(inverseStdDevs).add(finalOffset);
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
    VariableNumMap nonFeatureVars = featureFactor.getVars().remove(featureVariableNum);
    int numEntries = nonFeatureVars.getNumberOfPossibleAssignments();

    return featureFactor.marginalize(nonFeatureVars).product(1.0 / numEntries).coerceToDiscrete();
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
    VariableNumMap nonFeatureVars = featureFactor.getVars().remove(featureVariableNum);
    int numEntries = nonFeatureVars.getNumberOfPossibleAssignments();

    DiscreteFactor means = getMeans(featureFactor, featureVariableNum);
    DiscreteFactor sumSquare = featureFactor.product(featureFactor).marginalize(nonFeatureVars)
        .coerceToDiscrete();
    
    return sumSquare.add(means.product(means).product(-1.0 * numEntries))
        .product(1.0 / numEntries).coerceToDiscrete();
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
