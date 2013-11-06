package com.jayantkrish.jklol.models;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@code LinearClassifierFactor} represents a conditional distribution over a
 * single variable, given an inputVar feature vector. This factor is an
 * efficiency hack that improves the speed of inference for linear classifiers
 * with sparse, high-dimensional feature vectors. One could represent such a
 * classifier by a factor graph with a single variable for each feature.
 * However, inference in such graphs will consider every inputVar feature, even
 * those with no influence on the conditional probability. This class converts
 * the inference step into a fast sparse vector operation, dramatically
 * improving performance.
 * <p>
 * This factor represents a conditional distribution, and hence does not support
 * most of {@code Factor}'s math operations.
 * 
 * @author jayantk
 */
public class LinearClassifierFactor extends ClassifierFactor {

  private static final long serialVersionUID = 7300787304056125779L;

  private final int[] inputVarNums;
  private final VariableNumMap conditionalVars;

  private final Tensor logWeights;

  public LinearClassifierFactor(VariableNumMap inputVar, VariableNumMap outputVars,
      DiscreteVariable featureDictionary, Tensor logWeights) {
    super(inputVar, outputVars, featureDictionary);
    Preconditions.checkArgument(inputVar.union(outputVars).containsAll(
        Ints.asList(logWeights.getDimensionNumbers())));
    Preconditions.checkArgument(outputVars.getDiscreteVariables().size() == outputVars.size());

    this.inputVarNums = new int[] { inputVar.getOnlyVariableNum() };
    this.conditionalVars = VariableNumMap.emptyMap();
    this.logWeights = logWeights;
  }

  public LinearClassifierFactor(VariableNumMap inputVar, VariableNumMap outputVars, 
      VariableNumMap conditionalVars, DiscreteVariable featureDictionary, Tensor logWeights) {
    super(inputVar, outputVars, featureDictionary);
    Preconditions.checkArgument(inputVar.union(outputVars).containsAll(
        Ints.asList(logWeights.getDimensionNumbers())));
    Preconditions.checkArgument(outputVars.containsAll(conditionalVars));

    this.inputVarNums = new int[] { inputVar.getOnlyVariableNum() };
    this.conditionalVars = Preconditions.checkNotNull(conditionalVars);
    this.logWeights = logWeights;
  }

  /**
   * Gets the weights used by this linear classifier as a matrix. The dimensions
   * of the returned tensor match the dimension numbers of the input and output
   * variable. The dimensions corresponding to the output variables contain the
   * class labels, and the other dimension contains the feature indices.
   * 
   * @return
   */
  public Tensor getFeatureWeights() {
    return logWeights;
  }

  /**
   * Returns a vector (1-dimensional tensor) containing the feature weights used
   * to predict {@code outputClass}. {@code outputClass} must contain a value
   * for every output variable of this factor.
   * 
   * @param outputClass
   * @return
   */
  public Tensor getFeatureWeightsForClass(Assignment outputClass) {
    int[] classIndexes = getOutputVariables().assignmentToIntArray(outputClass);
    int[] dimensionNums = Ints.toArray(getOutputVariables().getVariableNums());

    return logWeights.slice(dimensionNums, classIndexes);
  }

  @Override
  protected Tensor getOutputLogProbTensor(Tensor inputFeatureVector) {
    Tensor logProbs = logWeights.innerProduct(inputFeatureVector.relabelDimensions(inputVarNums));

    if (conditionalVars.size() > 0) {
      Tensor probs = logProbs.elementwiseExp();
      Tensor normalizingConstants = probs.sumOutDimensions(conditionalVars.getVariableNums());
      logProbs = probs.elementwiseProduct(normalizingConstants.elementwiseInverse())
          .elementwiseLog();
    }
    return logProbs;
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNumsArray()));
    Tensor inputFeatureVector = (Tensor) assignment.getValue(getInputVariable()
        .getOnlyVariableNum());

    if (conditionalVars.size() == 0) {
      // No normalization for any conditioned-on variables. This case 
      // allows a more efficient implementation than the default
      // in ClassifierFactor.
      VariableNumMap outputVars = getOutputVariables();
      Tensor outputTensor = SparseTensor.singleElement(outputVars.getVariableNumsArray(),
          outputVars.getVariableSizes(), outputVars.assignmentToIntArray(assignment), 1.0);

      Tensor featureIndicator = outputTensor.outerProduct(inputFeatureVector);
      return logWeights.innerProduct(featureIndicator).getByDimKey();
    } else {
      // Default to looking up the answer in the output log probabilities
      int[] outputIndexes = getOutputVariables().assignmentToIntArray(assignment);
      Tensor logProbs = getOutputLogProbTensor(inputFeatureVector);
      return logProbs.getByDimKey(outputIndexes);
    }
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    return new LinearClassifierFactor(relabeling.apply(getInputVariable()), relabeling.apply(getOutputVariables()),
        relabeling.apply(conditionalVars), getFeatureVariableType(), logWeights.relabelDimensions(
            relabeling.getVariableIndexReplacementMap()));
  }
}