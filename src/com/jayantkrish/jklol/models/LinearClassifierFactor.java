package com.jayantkrish.jklol.models;

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
 * 
 * This factor represents a conditional distribution, and hence does not support
 * most of {@code Factor}'s math operations.
 * 
 * @author jayantk
 */
public class LinearClassifierFactor extends AbstractConditionalFactor {

  private final VariableNumMap inputVar;
  private final int[] inputVarNums;
  private final VariableNumMap outputVar;
  private final DiscreteVariable outputVariableType;

  private final Tensor logWeights;

  public LinearClassifierFactor(VariableNumMap inputVar, VariableNumMap outputVar,
      Tensor logWeights) {
    super(inputVar.union(outputVar));
    Preconditions.checkArgument(inputVar.size() == 1);
    Preconditions.checkArgument(outputVar.size() == 1);
    Preconditions.checkArgument(inputVar.union(outputVar).containsAll(
        Ints.asList(logWeights.getDimensionNumbers())));

    this.inputVar = inputVar;
    this.inputVarNums = new int[] { inputVar.getOnlyVariableNum() };
    this.outputVar = outputVar;
    this.outputVariableType = (DiscreteVariable) outputVar.getOnlyVariable();

    this.logWeights = logWeights;
  }

  /**
   * Gets the weights used by this linear classifier as a matrix. The dimensions
   * of the returned tensor match the dimension numbers of the input and output
   * variable. The dimension corresponding to the output variable contains the
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
   * for this factor's output variable.
   * 
   * @param outputClass
   * @return
   */
  public Tensor getFeatureWeightsForClass(Assignment outputClass) {
    int classIndex = outputVariableType.getValueIndex(
        outputClass.getValue(outputVar.getOnlyVariableNum()));

    Tensor classWeights = logWeights.slice(new int[] { outputVar.getOnlyVariableNum() },
        new int[] { classIndex });

    return classWeights;
  }

  public VariableNumMap getInputVariable() {
    return inputVar;
  }

  public VariableNumMap getOutputVariable() {
    return outputVar;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    return Math.exp(getUnnormalizedLogProbability(assignment));
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    Tensor inputFeatureVector = (Tensor) assignment.getValue(inputVar.getOnlyVariableNum());
    int outputIndex = outputVariableType.getValueIndex(assignment.getValue(outputVar.getOnlyVariableNum()));

    Tensor multiplied = logWeights.elementwiseProduct(inputFeatureVector.relabelDimensions(inputVarNums));
    Tensor logProbs = multiplied.sumOutDimensions(Sets.newHashSet(Ints.asList(inputVarNums)));
    return logProbs.getByDimKey(outputIndex);
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    return new LinearClassifierFactor(relabeling.apply(inputVar), relabeling.apply(outputVar),
        logWeights.relabelDimensions(relabeling.getVariableIndexReplacementMap()));
  }

  @Override
  public Factor conditional(Assignment assignment) {
    int inputVarNum = inputVar.getOnlyVariableNum();
    int outputVarNum = outputVar.getOnlyVariableNum();
    // We can only condition on the outputVar variable if we also condition on
    // the
    // inputVar variable.
    Preconditions.checkArgument(!assignment.contains(outputVarNum)
        || assignment.contains(inputVarNum));

    if (!assignment.contains(inputVarNum)) {
      return this;
    }

    // Build a TableFactor over the outputVar based on the inputVar feature
    // vector.
    Tensor inputFeatureVector = (Tensor) assignment.getValue(inputVar.getOnlyVariableNum());
    // Get the log probabilities of each outputVar value.
    Tensor multiplied = logWeights.elementwiseProduct(inputFeatureVector.relabelDimensions(inputVarNums));
    Tensor logProbs = multiplied.sumOutDimensions(Sets.newHashSet(Ints.asList(inputVarNums)));
    TableFactor outputFactor = new TableFactor(outputVar,
        new LogSpaceTensorAdapter(DenseTensor.copyOf(logProbs)));
    return outputFactor.conditional(assignment);

    // Construct a table factor with the unnormalized probabilities of each
    // outputVar value.
    /*
     * TableFactorBuilder outputBuilder = new TableFactorBuilder(outputVar); for
     * (int i = 0; i < outputVariableType.numValues(); i++) {
     * outputBuilder.setWeight(Math.exp(logProbs.getByDimKey(new int[] {i})),
     * outputVariableType.getValue(i)); } TableFactor output =
     * outputBuilder.build(); return output.conditional(assignment);
     */
  }

  @Override
  public FactorProto toProto() {
    FactorProto.Builder builder = getProtoBuilder();
    builder.setType(FactorProto.FactorType.LINEAR_CLASSIFIER);

    LinearClassifierProto.Builder linearBuilder = builder.getLinearClassifierFactorBuilder();
    linearBuilder.setInputVariableNum(inputVar.getOnlyVariableNum());
    linearBuilder.setOutputVariableNum(outputVar.getOnlyVariableNum());
    linearBuilder.setWeights(logWeights.toProto());

    return builder.build();
  }
}