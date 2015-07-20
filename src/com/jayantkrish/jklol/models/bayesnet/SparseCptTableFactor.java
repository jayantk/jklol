package com.jayantkrish.jklol.models.bayesnet;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A factor represented by a sparse conditional probability table,
 * where some outcomes have guaranteed 0 probability.
 * 
 * @author jayant
 *
 */
public class SparseCptTableFactor extends AbstractParametricFactor {

  private static final long serialVersionUID = 1L;
  
  // These are the parent and child variables in the CPT.
  private final VariableNumMap childVars;
  private final VariableNumMap parentVars;

  private final DiscreteFactor sparsityPattern;
  private final DiscreteFactor constantPattern;
  private final VariableNumMap outcomeVar;
  
  public SparseCptTableFactor(VariableNumMap parentVars, VariableNumMap childVars,
      DiscreteFactor sparsityPattern, DiscreteFactor constantPattern) {
    super(parentVars.union(childVars));
    Preconditions.checkArgument(parentVars.getDiscreteVariables().size() == parentVars.size());
    Preconditions.checkArgument(childVars.getDiscreteVariables().size() == childVars.size());
    this.parentVars = parentVars;
    this.childVars = childVars;

    this.sparsityPattern = Preconditions.checkNotNull(sparsityPattern);
    this.constantPattern = Preconditions.checkNotNull(constantPattern);
    Preconditions.checkArgument(sparsityPattern.getVars().equals(parentVars.union(childVars)));
    Preconditions.checkArgument(constantPattern.getVars().equals(parentVars.union(childVars)));
    
    List<Assignment> assignments = sparsityPattern.getNonzeroAssignments();
    DiscreteVariable featureNameDictionary = new DiscreteVariable("indicator features", assignments);
    this.outcomeVar = VariableNumMap.singleton(0, "features", featureNameDictionary);
  }

  @Override
  public DiscreteFactor getModelFromParameters(SufficientStatistics parameters) {
    Tensor outcomeWeights = getOutcomeWeights(parameters);
    Tensor assignmentCounts = sparsityPattern.getWeights()
        .replaceValues(outcomeWeights.getValues());

    Tensor parentCounts = assignmentCounts.sumOutDimensions(childVars.getVariableNumsArray());
    TableFactor learnedDistribution = new TableFactor(getVars(),
        assignmentCounts.elementwiseProduct(parentCounts.elementwiseInverse()));
    return learnedDistribution.add(constantPattern);
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNumsArray()));
    Assignment subAssignment = assignment.intersection(getVars().getVariableNumsArray());

    long keyNum = sparsityPattern.getWeights().dimKeyToKeyNum(
        sparsityPattern.getVars().assignmentToIntArray(subAssignment));
    int index = sparsityPattern.getWeights().keyNumToIndex(keyNum);

    ((TensorSufficientStatistics) statistics).incrementFeatureByIndex(count, index);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Factor marginal, Assignment conditionalAssignment,
      double count, double partitionFunction) {
    
    if (conditionalAssignment.containsAll(getVars().getVariableNumsArray())) {
      // Fast computation if all variables are assigned.
      double multiplier = marginal.getTotalUnnormalizedProbability() * count / partitionFunction;
      incrementSufficientStatisticsFromAssignment(statistics, currentParameters,
          conditionalAssignment, multiplier);
    } else {
      VariableNumMap conditionedVars = sparsityPattern.getVars().intersection(
          conditionalAssignment.getVariableNumsArray());

      TableFactor productFactor = (TableFactor) sparsityPattern.product(
          TableFactor.pointDistribution(conditionedVars, conditionalAssignment.intersection(conditionedVars)))
          .product(marginal);

      Tensor sparsityWeights = sparsityPattern.getWeights();
      Tensor productFactorWeights = productFactor.getWeights();
      double[] productFactorValues = productFactorWeights.getValues();
      int tensorSize = productFactorWeights.size();
      double multiplier = count / partitionFunction;
      TensorSufficientStatistics tensorGradient = (TensorSufficientStatistics) statistics;
      for (int i = 0; i < tensorSize; i++) {
        int builderIndex = sparsityWeights.keyNumToIndex(productFactorWeights.indexToKeyNum(i));
        tensorGradient.incrementFeatureByIndex(productFactorValues[i] * multiplier, builderIndex);
      }
    }
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return new TensorSufficientStatistics(outcomeVar, new DenseTensorBuilder(new int[] { 0 },
        new int[] { sparsityPattern.getWeights().getValues().length }));
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    DiscreteFactor factor = getModelFromParameters(parameters).product(sparsityPattern).add(constantPattern);
    
    VariableRelabeling parentIdentity = VariableRelabeling.identity(parentVars);
    int nextVarNum = Ints.max(parentVars.getVariableNumsArray()) + 1;
    int[] relabeledChildNums = new int[childVars.size()];
    for (int i = 0; i < childVars.size(); i++) {
      relabeledChildNums[i] = nextVarNum + i;
    }
    VariableRelabeling childToEnd = VariableRelabeling.createFromVariables(childVars, 
        childVars.relabelVariableNums(relabeledChildNums));
    factor = (DiscreteFactor) factor.relabelVariables(parentIdentity.union(childToEnd));
    if (numFeatures >= 0) {
      return factor.describeAssignments(factor.getMostLikelyAssignments(numFeatures));
    } else {
      return factor.getParameterDescription();
    }
  }

  private Tensor getOutcomeWeights(SufficientStatistics parameters) {
    TensorSufficientStatistics outcomeParameters = (TensorSufficientStatistics) parameters;
    // Check that the parameters are a vector of the appropriate size.
    Preconditions.checkArgument(outcomeParameters.get().getDimensionNumbers().length == 1);
    Preconditions.checkArgument(outcomeParameters.get().getDimensionSizes()[0] ==
        sparsityPattern.getWeights().getValues().length);
    return outcomeParameters.get();
  }
}
