package com.jayantkrish.jklol.models.loglinear;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.ClassifierFactor;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.LinearClassifierFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A log-linear factor where one of the variables is always conditioned on. The
 * conditioned-on variable is a feature vector, which determines the conditional
 * distribution over the other variable.
 * 
 * @author jayantk
 */
public class ConditionalLogLinearFactor extends AbstractParametricFactor {

  private static final long serialVersionUID = 2L;
  
  private final VariableNumMap inputVar;
  private final VariableNumMap outputVars;
  private final VariableNumMap conditionalVars;
  
  private final int[] inputVarNums;
  private final int[] outputVarNums;
  private final int[] varNums;
  
  // Names of the features. Also defines the expected dimensionality 
  // of the input feature vector.
  private final DiscreteVariable featureDictionary;

  // Size parameters for the sufficient statistics tensor. 
  private final int[] dimensionNums;
  private final int[] dimensionSizes;
  private final VariableNumMap sufficientStatisticVars;

  /**
   * Create a factor which represents a conditional distribution over outputVars
   * given inputVar. {@code featureVectorDimensionality} is the dimension of the
   * feature vector which will be assigned to {@code inputVar}.
   * 
   * @param inputVar
   * @param outputVars
   * @param conditionalVars
   * @param featureDictionary
   */
  public ConditionalLogLinearFactor(VariableNumMap inputVar, VariableNumMap outputVars, 
      VariableNumMap conditionalVars, DiscreteVariable featureDictionary) {
    super(inputVar.union(outputVars));
    Preconditions.checkArgument(inputVar.size() == 1);
    Preconditions.checkArgument(outputVars.getDiscreteVariables().size() == outputVars.size());
    this.inputVar = inputVar;
    this.outputVars = outputVars;
    this.conditionalVars = conditionalVars;
    
    this.inputVarNums = inputVar.getVariableNumsArray();
    this.outputVarNums = outputVars.getVariableNumsArray();
    this.varNums = getVars().getVariableNumsArray();

    this.featureDictionary = featureDictionary;
    
    this.dimensionNums = inputVar.union(outputVars).getVariableNumsArray();
    Preconditions.checkArgument(dimensionNums[0] == inputVar.getOnlyVariableNum());
        
    this.dimensionSizes = new int[outputVars.size() + 1];
    dimensionSizes[0] = featureDictionary.numValues();
    int[] outputSizes = outputVars.getVariableSizes();
    for (int i = 0; i < outputSizes.length; i++) {
      dimensionSizes[i + 1] = outputSizes[i];
    }
    
    VariableNumMap featureVar = VariableNumMap.singleton(inputVar.getOnlyVariableNum(), 
        inputVar.getOnlyVariableName(), featureDictionary);
    this.sufficientStatisticVars = featureVar.union(outputVars);
  }

  public DiscreteVariable getFeatureDictionary() {
    return featureDictionary;
  }

  @Override
  public ClassifierFactor getModelFromParameters(SufficientStatistics parameters) {
    return new LinearClassifierFactor(inputVar, outputVars, conditionalVars, featureDictionary, 
        getWeightTensorFromStatistics(parameters));
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) { 
    Tensor weightTensor = getWeightTensorFromStatistics(parameters);
    VariableNumMap featureVariable = VariableNumMap.singleton(inputVar.getOnlyVariableNum(), 
        inputVar.getVariableNames().get(0) + "_features", featureDictionary);
    TableFactor parameterFactor = new TableFactor(featureVariable.union(outputVars), 
        weightTensor);
    
    List<Assignment> assignments = parameterFactor.product(parameterFactor)
        .getMostLikelyAssignments(numFeatures);
    return parameterFactor.describeAssignments(assignments);
  }
  
  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
      /*
    return TensorSufficientStatistics.createSparse(sufficientStatisticVars,
        SparseTensor.empty(dimensionNums, dimensionSizes));
      */

    return TensorSufficientStatistics.createDense(sufficientStatisticVars,
        new DenseTensorBuilder(dimensionNums, dimensionSizes));
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics, 
      Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNumsArray()));

    Tensor inputValueFeatures = ((Tensor) assignment.getValue(inputVarNums[0]))
        .relabelDimensions(inputVarNums);
    Tensor outputDistribution = SparseTensor.singleElement(outputVarNums, 
        outputVars.getVariableSizes(), outputVars.assignmentToIntArray(assignment), 1.0);
    
    // The expected feature counts are equal to the outer product of
    // inputTensor and outputMarginal.
    ((TensorSufficientStatistics) statistics).incrementOuterProduct(inputValueFeatures, outputDistribution, count);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, Factor marginal, 
      Assignment conditionalAssignment, double count, double partitionFunction) {
    Preconditions.checkArgument(conditionalAssignment.containsAll(inputVarNums));

    if (conditionalAssignment.containsAll(varNums)) {
      Preconditions.checkState(marginal.getVars().size() == 0);
      // Easy case where all variables' values are known.
      double multiplier = marginal.getTotalUnnormalizedProbability() * count / partitionFunction;
      incrementSufficientStatisticsFromAssignment(statistics, conditionalAssignment, multiplier);
    } else {
      // Construct a factor representing the unnormalized probability distribution over all
      // of the output variables.
      DiscreteFactor outputMarginal = marginal.coerceToDiscrete();
      if (conditionalAssignment.containsAny(outputVarNums)) {
        Assignment conditionedAssignment = conditionalAssignment.intersection(outputVarNums);
        DiscreteFactor conditionedFactor = TableFactor.pointDistribution(
            outputVars.intersection(conditionedAssignment.getVariableNumsArray()), conditionedAssignment);
        outputMarginal = conditionedFactor.outerProduct(marginal);
      }

      Tensor inputTensor = ((Tensor) conditionalAssignment.getValue(inputVarNums[0]))
          .relabelDimensions(inputVarNums);

      // The expected feature counts are equal to the outer product of
      // inputTensor and outputMarginal.
      ((TensorSufficientStatistics) statistics).incrementOuterProduct(inputTensor,
          outputMarginal.getWeights(), count / partitionFunction);
    }
  }

  private Tensor getWeightTensorFromStatistics(SufficientStatistics stats) {
    return ((TensorSufficientStatistics) stats).get();
  }
}