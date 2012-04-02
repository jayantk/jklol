package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.LinearClassifierFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.tensor.TensorFactory;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A log-linear factor where one of the variables is always conditioned on. The
 * conditioned-on variable is a feature vector, which determines the conditional
 * distribution over the other variable.
 * 
 * @author jayantk
 */
public class ConditionalLogLinearFactor extends AbstractParametricFactor<SufficientStatistics> {

  private final VariableNumMap inputVar;
  private final VariableNumMap outputVar;

  // Size parameters for the sufficient statistics tensor. 
  private final int[] dimensionNums;
  private final int[] dimensionSizes;
  
  // Constructs the type of tensor to use.
  private final TensorFactory tensorFactory;

  /**
   * Create a factor which represents a conditional distribution over outputVar
   * given inputVar. {@code featureVectorDimensionality} is the dimension of the
   * feature vector which will be assigned to {@code inputVar}.
   * 
   * @param inputVar
   * @param outputVar
   * @param featureVectorDimensionality
   */
  public ConditionalLogLinearFactor(VariableNumMap inputVar, VariableNumMap outputVar,
      int featureVectorDimensionality, TensorFactory tensorFactory) {
    super(inputVar.union(outputVar));
    Preconditions.checkArgument(inputVar.size() == 1);
    Preconditions.checkArgument(outputVar.size() == 1);
    Preconditions.checkArgument(outputVar.getDiscreteVariables().size() == 1);
    this.inputVar = inputVar;
    this.outputVar = outputVar;
    
    this.dimensionNums = new int[] {inputVar.getOnlyVariableNum(), 
        outputVar.getOnlyVariableNum()};
    this.dimensionSizes = new int[] {featureVectorDimensionality, 
        outputVar.getDiscreteVariables().get(0).numValues()};
    this.tensorFactory = Preconditions.checkNotNull(tensorFactory);
  }

  @Override
  public Factor getFactorFromParameters(SufficientStatistics parameters) {
    return new LinearClassifierFactor(inputVar, outputVar, 
        getWeightTensorFromStatistics(parameters).build());
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters) { 
    throw new UnsupportedOperationException();
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {    
    return new TensorSufficientStatistics(Arrays.<TensorBuilder>asList(
        tensorFactory.getBuilder(dimensionNums, dimensionSizes)));
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics, 
      Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    
    TensorBuilder weightTensor = getWeightTensorFromStatistics(statistics);
    Tensor inputValueFeatures = (Tensor) assignment.getValue(inputVar.getVariableNums().get(0));
    Iterator<KeyValue> keyValueIter = inputValueFeatures.keyValueIterator();
    int[] weightKey = new int[2];
    weightKey[1] = outputVar.assignmentToIntArray(assignment.intersection(outputVar))[0];
    while (keyValueIter.hasNext()) {
      KeyValue featureKeyValue = keyValueIter.next();
      weightKey[0] = featureKeyValue.getKey()[0];
      weightTensor.incrementEntry(count * featureKeyValue.getValue(), weightKey);
    }
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, Factor marginal, 
      Assignment conditionalAssignment, double count, double partitionFunction) {
    Preconditions.checkArgument(conditionalAssignment.containsAll(inputVar.getVariableNums()));
    
    if (conditionalAssignment.containsAll(outputVar.getVariableNums())) {
      incrementSufficientStatisticsFromAssignment(statistics, conditionalAssignment, count);
    } else {
      Iterator<Assignment> outputIterator = new AllAssignmentIterator(outputVar);
      while (outputIterator.hasNext()) {
        Assignment outputValue = outputIterator.next();
        Assignment jointAssignment = conditionalAssignment.union(outputValue);
        double amount = count * marginal.getUnnormalizedProbability(outputValue) / partitionFunction;
        incrementSufficientStatisticsFromAssignment(statistics, jointAssignment, amount);
      }
    }
  }
  
  private TensorBuilder getWeightTensorFromStatistics(SufficientStatistics stats) {
    TensorBuilder weightTensor = ((TensorSufficientStatistics) stats).get(0);
    Preconditions.checkArgument(Arrays.equals(dimensionNums, weightTensor.getDimensionNumbers()),
        "Wrong parameter dimensions. Expected %s, got %s", Arrays.toString(dimensionNums), 
        Arrays.toString(weightTensor.getDimensionNumbers()));
    Preconditions.checkArgument(Arrays.equals(dimensionSizes, weightTensor.getDimensionSizes()));
    return weightTensor;
  }
}
