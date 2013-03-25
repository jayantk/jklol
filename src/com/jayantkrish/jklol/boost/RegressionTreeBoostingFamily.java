package com.jayantkrish.jklol.boost;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.dtree.RegressionTree;
import com.jayantkrish.jklol.dtree.RegressionTreeTrainer;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.RegressionTreeFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.AppendOnlySparseTensorBuilder;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class RegressionTreeBoostingFamily extends AbstractBoostingFactorFamily {

  private final RegressionTreeTrainer trainer;
  
  private final DiscreteVariable featureDictionary;
  private final Tensor outputOutcomes;

  public RegressionTreeBoostingFamily(VariableNumMap conditionalVars,
      VariableNumMap unconditionalVars, RegressionTreeTrainer trainer,
      DiscreteVariable featureDictionary, Tensor outputOutcomes) {
    super(conditionalVars, unconditionalVars);
    this.trainer = Preconditions.checkNotNull(trainer);
    
    this.featureDictionary = Preconditions.checkNotNull(featureDictionary);
    this.outputOutcomes = Preconditions.checkNotNull(outputOutcomes);
    
    Preconditions.checkArgument(conditionalVars.size() == 1);
  }

  @Override
  public FunctionalGradient getNewFunctionalGradient() {
    return FactorFunctionalGradient.empty();
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    RegressionTree[] trees = new RegressionTree[outputOutcomes.size()];
    for (int i = 0; i < trees.length; i++) {
      trees[i] = RegressionTree.createLeaf(0.0);
    }
    return new RegressionTreeSufficientStatistics(trees);
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics statistics) {
    RegressionTreeSufficientStatistics stats = (RegressionTreeSufficientStatistics) statistics;
    return new RegressionTreeFactor(getConditionalVariables(), getUnconditionalVariables(), 
        featureDictionary, stats.getTrees(), outputOutcomes); 
  }

  @Override
  public void incrementGradient(FunctionalGradient gradient, Factor marginal,
      Assignment assignment) {
    ((FactorFunctionalGradient) gradient).addExample(marginal, assignment);
  }

  @Override
  public SufficientStatistics projectGradient(FunctionalGradient gradient) {
    FactorFunctionalGradient grad = (FactorFunctionalGradient) gradient;
    int[] outputDims = outputOutcomes.getDimensionNumbers();
    int exampleDim = outputDims[outputDims.length - 1] + 1;
    int featureDim = exampleDim + 1;
        
    // Build the feature matrix from the feature vectors in the assignments.
    List<Assignment> assignments = grad.getRegressionAssignments();
    TensorBuilder featureMatrixBuilder = new AppendOnlySparseTensorBuilder(new int[] {exampleDim, featureDim},
        new int[] {assignments.size(), featureDictionary.numValues()});
    for (int i = 0; i < assignments.size(); i++) {
      Assignment assignment = assignments.get(i);
      Tensor featureVector = (Tensor) assignment.getOnlyValue();
      int numValues = featureVector.size();
      int[] dimKey = new int[1];
      for (int j = 0; j < numValues; j++) {
        featureVector.keyNumToDimKey(featureVector.indexToKeyNum(j), dimKey);
        featureMatrixBuilder.put(new int[] {i, dimKey[0]}, featureVector.getByIndex(j));
      }
    }
    Tensor featureMatrix = featureMatrixBuilder.build();

    // Build a tensor of the regression targets for each outcome.
    int[] newDims = Arrays.copyOf(outputDims, outputDims.length + 1);
    newDims[newDims.length - 1] = exampleDim;
    int[] newDimSizes = Arrays.copyOf(outputOutcomes.getDimensionSizes(), outputDims.length + 1);
    newDimSizes[newDimSizes.length - 1] = assignments.size();
    TensorBuilder targetBuilder = new DenseTensorBuilder(newDims, newDimSizes);
    
    int[] exampleDims = new int[] {exampleDim};
    int[] exampleDimSizes = new int[] {assignments.size()};
    
    List<Factor> targets = grad.getRegressionTargets();
    for (int i = 0; i < assignments.size(); i++) {
      DiscreteFactor target = targets.get(i).coerceToDiscrete();
      targetBuilder.increment(target.getWeights().outerProduct(
          SparseTensor.singleElement(exampleDims, exampleDimSizes, new int[] {i}, 1.0)));
    }
    Tensor targetTensor = targetBuilder.build();
    
    // Train a regression tree for each outcome.
    RegressionTree[] trees = new RegressionTree[outputOutcomes.size()];
    for (int i = 0; i < outputOutcomes.size(); i++) {
      int[] dimKey = outputOutcomes.keyNumToDimKey(outputOutcomes.indexToKeyNum(i));
      
      Tensor outcomeTargets = targetTensor.slice(outputDims, dimKey);
      RegressionTree outcomeTree = trainer.train(featureMatrix, outcomeTargets);
      trees[i] = outcomeTree;
    }
    
    return new RegressionTreeSufficientStatistics(trees);
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    RegressionTree[] trees = ((RegressionTreeSufficientStatistics) parameters).getTrees();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < trees.length; i++) {
      sb.append(trees[i].toString());
      sb.append("\n");
    }
    return sb.toString();
  }
}
