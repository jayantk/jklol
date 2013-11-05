package com.jayantkrish.jklol.models;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A factor that generates weights on a set of outcomes using a  
 * feature vector as input. Different subclasses use different classifiers.
 * 
 * @author jayant
 */
public abstract class ClassifierFactor extends AbstractConditionalFactor {

  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap inputVar;
  private final VariableNumMap outputVars;
  
  private final DiscreteVariable featureDictionary;
  
  public ClassifierFactor(VariableNumMap inputVar, VariableNumMap outputVars, 
      DiscreteVariable featureDictionary) {
    super(inputVar.union(outputVars));
    Preconditions.checkArgument(inputVar.size() == 1);
    Preconditions.checkArgument(outputVars.getDiscreteVariables().size() == outputVars.size());

    this.inputVar = Preconditions.checkNotNull(inputVar);
    this.outputVars = Preconditions.checkNotNull(outputVars);
    this.featureDictionary = featureDictionary;
  }

  public VariableNumMap getInputVariable() {
    return inputVar;
  }

  public VariableNumMap getOutputVariables() {
    return outputVars;
  }
  
  public DiscreteVariable getFeatureVariableType() {
    return featureDictionary;
  }
  
  protected abstract Tensor getOutputLogProbTensor(Tensor inputFeatureVector);
  
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
  public Factor conditional(Assignment assignment) {
    int inputVarNum = inputVar.getOnlyVariableNum();
    int[] outputVarNums = outputVars.getVariableNumsArray();
    // We can only condition on outputVars if we also condition on
    // inputVar.
    Preconditions.checkArgument(!assignment.containsAny(outputVarNums)
        || assignment.contains(inputVarNum));

    if (!assignment.contains(inputVarNum)) {
      return this;
    }

    // Build a TableFactor over the outputVars based on the inputVar feature
    // vector.
    Tensor inputFeatureVector = (Tensor) assignment.getValue(inputVarNum);
    Tensor logProbs = getOutputLogProbTensor(inputFeatureVector);
    TableFactor outputFactor = new TableFactor(outputVars,
        new LogSpaceTensorAdapter(DenseTensor.copyOf(logProbs)));
    
    // Note that the assignment may contain more than just the input variable, hence
    // the additional call to condition.
    return outputFactor.conditional(assignment);
  }
}
