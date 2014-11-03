package com.jayantkrish.jklol.models;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.dtree.RegressionTree;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Factor that uses a set of regression trees to predict the weights of outcomes.
 *  
 * @author jayant
 */
public class RegressionTreeFactor extends ClassifierFactor {
  
  private static final long serialVersionUID = 1L;
  
  private final RegressionTree[] trees;
  private final Tensor outputTensor;
    
  public RegressionTreeFactor(VariableNumMap inputVar, VariableNumMap outputVars,
      DiscreteVariable featureDictionary, RegressionTree[] trees, Tensor outputTensor) {
    super(inputVar, outputVars, featureDictionary);
    this.trees = Preconditions.checkNotNull(trees);
    this.outputTensor = Preconditions.checkNotNull(outputTensor);
    Preconditions.checkArgument(Arrays.equals(outputTensor.getDimensionNumbers(),
        outputVars.getVariableNumsArray()));
  }
  
  @Override
  protected Tensor getOutputLogProbTensor(Tensor featureVector) {
    double[] weights = new double[trees.length];
    for (int i = 0; i < trees.length; i++) {
      weights[i] = trees[i].regress(featureVector);
    }
    return outputTensor.replaceValues(weights);
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    return new RegressionTreeFactor(relabeling.apply(getInputVariable()),
        relabeling.apply(getOutputVariables()), getFeatureVariableType(), trees,
        outputTensor.relabelDimensions(relabeling.getVariableIndexReplacementMap()));
  }
}
