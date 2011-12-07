package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@code LinearClassifierFactor} represents a conditional distribution over a
 * single variable, given an inputVar feature vector. This factor is an efficiency
 * hack that improves the speed of inference for linear classifiers with sparse,
 * high-dimensional feature vectors. One could represent such a classifier by a
 * factor graph with a single variable for each feature. However, inference in
 * such graphs will consider every inputVar feature, even those with no influence
 * on the conditional probability. This class converts the inference step into a
 * fast sparse vector operation, dramatically improving performance.
 * 
 * This factor represents a conditional distribution, and hence does not support
 * most of {@code Factor}'s math operations.
 * 
 * @author jayantk
 */
public class LinearClassifierFactor extends AbstractFactor {

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
    this.inputVarNums = new int[] {inputVar.getVariableNums().get(0)};
    this.outputVar = outputVar;
    this.outputVariableType = (DiscreteVariable) outputVar.getVariables().get(0);
    
    this.logWeights = logWeights;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));

    Tensor inputFeatureVector = (Tensor) assignment.getValue(inputVar.getVariableNums().get(0));
    int outputIndex = outputVariableType.getValueIndex(assignment.getValue(outputVar.getVariableNums().get(0)));

    Tensor multiplied = logWeights.elementwiseProduct(inputFeatureVector.relabelDimensions(inputVarNums));
    Tensor logProbs = multiplied.sumOutDimensions(Sets.newHashSet(Ints.asList(inputVarNums)));
    return Math.exp(logProbs.get(outputIndex)); 
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    return new LinearClassifierFactor(relabeling.apply(inputVar), relabeling.apply(outputVar),
        logWeights.relabelDimensions(relabeling.getVariableIndexReplacementMap()));
  }

  @Override
  public Factor conditional(Assignment assignment) {
    int inputVarNum = inputVar.getVariableNums().get(0);
    int outputVarNum = outputVar.getVariableNums().get(0);
    // We can only condition on the outputVar variable if we also condition on the
    // inputVar variable.
    Preconditions.checkArgument(!assignment.contains(outputVarNum)
        || assignment.contains(inputVarNum));
    
    if (!assignment.contains(inputVarNum)) {
      return this;
    }
    
    // Build a TableFactor over the outputVar based on the inputVar feature vector. 
    Tensor inputFeatureVector = (Tensor) assignment.getValue(inputVar.getVariableNums().get(0));
    // Get the log probabilities of each outputVar value.
    Tensor multiplied = logWeights.elementwiseProduct(inputFeatureVector.relabelDimensions(inputVarNums));
    Tensor logProbs = multiplied.sumOutDimensions(Sets.newHashSet(Ints.asList(inputVarNums)));
    
    // Construct a table factor with the unnormalized probabilities of each outputVar value.
    TableFactorBuilder outputBuilder = new TableFactorBuilder(outputVar);
    for (int i = 0; i < outputVariableType.numValues(); i++) {
      outputBuilder.setWeight(Math.exp(logProbs.get(new int[] {i})), 
          outputVariableType.getValue(i));
    }
    TableFactor output = outputBuilder.build();
    return output.conditional(assignment);
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor add(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor maximum(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor product(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor product(double constant) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor inverse() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double size() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    throw new UnsupportedOperationException();
  }
}
