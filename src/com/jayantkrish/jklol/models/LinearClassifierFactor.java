package com.jayantkrish.jklol.models;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.models.FactorGraphProtos.LinearClassifierProto;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

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
public class LinearClassifierFactor extends AbstractConditionalFactor {

  private final VariableNumMap inputVar;
  private final int[] inputVarNums;
  private final VariableNumMap outputVars;
  private final VariableNumMap conditionalVars;

  private final Tensor logWeights;

  public LinearClassifierFactor(VariableNumMap inputVar, VariableNumMap outputVars,
      Tensor logWeights) {
    super(inputVar.union(outputVars));
    Preconditions.checkArgument(inputVar.size() == 1);
    Preconditions.checkArgument(inputVar.union(outputVars).containsAll(
        Ints.asList(logWeights.getDimensionNumbers())));
    Preconditions.checkArgument(outputVars.getDiscreteVariables().size() == outputVars.size());

    this.inputVar = inputVar;
    this.inputVarNums = new int[] { inputVar.getOnlyVariableNum() };
    this.outputVars = outputVars;
    this.conditionalVars = VariableNumMap.emptyMap();
    this.logWeights = logWeights;
  }
  
  public LinearClassifierFactor(VariableNumMap inputVar, VariableNumMap outputVars, 
      VariableNumMap conditionalVars, Tensor logWeights) {
    super(inputVar.union(outputVars));
    Preconditions.checkArgument(inputVar.size() == 1);
    Preconditions.checkArgument(inputVar.union(outputVars).containsAll(
        Ints.asList(logWeights.getDimensionNumbers())));
    Preconditions.checkArgument(outputVars.getDiscreteVariables().size() == outputVars.size());
    Preconditions.checkArgument(outputVars.containsAll(conditionalVars));

    this.inputVar = Preconditions.checkNotNull(inputVar);
    this.inputVarNums = new int[] { inputVar.getOnlyVariableNum() };
    this.outputVars = Preconditions.checkNotNull(outputVars);
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
    int[] classIndexes = outputVars.assignmentToIntArray(outputClass);
    int[] dimensionNums = Ints.toArray(outputVars.getVariableNums());

    return logWeights.slice(dimensionNums, classIndexes);
  }

  public VariableNumMap getInputVariable() {
    return inputVar;
  }

  public VariableNumMap getOutputVariables() {
    return outputVars;
  }
  
  private Tensor getOutputLogProbTensor(Tensor inputFeatureVector) {
    Tensor multiplied = logWeights.elementwiseProduct(inputFeatureVector.relabelDimensions(inputVarNums));
    Tensor logProbs = multiplied.sumOutDimensions(Sets.newHashSet(Ints.asList(inputVarNums)));

    if (conditionalVars.size() > 0) {
      Tensor probs = logProbs.elementwiseExp();
      Tensor normalizingConstants = probs.sumOutDimensions(conditionalVars.getVariableNums());
      logProbs = probs.elementwiseProduct(normalizingConstants.elementwiseInverse())
          .elementwiseLog();
    }
    return logProbs;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    return Math.exp(getUnnormalizedLogProbability(assignment));
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    Tensor inputFeatureVector = (Tensor) assignment.getValue(inputVar.getOnlyVariableNum());
    int[] outputIndexes = outputVars.assignmentToIntArray(assignment);
    
    Tensor logProbs = getOutputLogProbTensor(inputFeatureVector);
    return logProbs.getByDimKey(outputIndexes);
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    return new LinearClassifierFactor(relabeling.apply(inputVar), relabeling.apply(outputVars),
        relabeling.apply(conditionalVars), logWeights.relabelDimensions(relabeling.getVariableIndexReplacementMap()));
  }

  @Override
  public Factor conditional(Assignment assignment) {
    int inputVarNum = inputVar.getOnlyVariableNum();
    List<Integer> outputVarNums = outputVars.getVariableNums();
    // We can only condition on outputVars if we also condition on
    // inputVar.
    Preconditions.checkArgument(!assignment.containsAny(outputVarNums)
        || assignment.contains(inputVarNum));

    if (!assignment.contains(inputVarNum)) {
      return this;
    }

    // Build a TableFactor over the outputVars based on the inputVar feature
    // vector.
    Tensor inputFeatureVector = (Tensor) assignment.getValue(inputVar.getOnlyVariableNum());
    Tensor logProbs = getOutputLogProbTensor(inputFeatureVector);
    TableFactor outputFactor = new TableFactor(outputVars,
        new LogSpaceTensorAdapter(DenseTensor.copyOf(logProbs)));
    
    // Note that the assignment may contain more than just the input variable, hence
    // the additional call to condition.
    return outputFactor.conditional(assignment);
  }

  @Override
  public FactorProto toProto(IndexedList<Variable> variableTypeIndex) {
    FactorProto.Builder builder = getProtoBuilder(variableTypeIndex);
    builder.setType(FactorProto.FactorType.LINEAR_CLASSIFIER);

    LinearClassifierProto.Builder linearBuilder = builder.getLinearClassifierFactorBuilder();
    linearBuilder.setInputVariableNum(inputVar.getOnlyVariableNum());
    linearBuilder.setOutputVariableNum(outputVars.getOnlyVariableNum());
    linearBuilder.setWeights(logWeights.toProto());

    return builder.build();
  }
}