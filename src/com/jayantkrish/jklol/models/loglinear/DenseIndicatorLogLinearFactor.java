package com.jayantkrish.jklol.models.loglinear;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.SparseLogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link ParametricFactor} whose parameters are weights of log-linear
 * indicator features. This class is a less flexible version of
 * {@code IndicatorLogLinearFactor} that defines one indicator feature for every
 * outcome. However, its feature encoding is more efficient, allowing models
 * with potentially billions of parameters.
 * 
 * @author jayantk
 */
public class DenseIndicatorLogLinearFactor extends AbstractParametricFactor {

  private static final long serialVersionUID = 1L;
  private final boolean isSparse;
  
  // If non-null, only features with nonzero values in
  // featureIndicator are used.
  private final DiscreteFactor featureRescaling;
  private final Tensor featureRescalingTensor;

  public DenseIndicatorLogLinearFactor(VariableNumMap variables, boolean isSparse,
      DiscreteFactor featureRescaling) {
    super(variables);
    this.isSparse = isSparse;
    this.featureRescaling = featureRescaling;
    Preconditions.checkArgument(featureRescaling == null || featureRescaling.getVars().equals(variables));
    this.featureRescalingTensor = featureRescaling == null ? null : featureRescaling.getWeights();
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics parameters) {
    if (isSparse) {
      return new TableFactor(getVars(), new SparseLogSpaceTensorAdapter(getFeatureWeights(parameters)));
    } else {
      return new TableFactor(getVars(), new LogSpaceTensorAdapter(getFeatureWeights(parameters)));
    }
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    TableFactor featureValues = getFeatureWeightFactor(parameters);
    
    List<Assignment> biggestAssignments = featureValues.product(featureValues)
        .getMostLikelyAssignments(numFeatures);
    
    return featureValues.describeAssignments(biggestAssignments);
  }

  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    if (isSparse) {
      return TensorSufficientStatistics.createSparse(getVars(), 
          SparseTensor.empty(getVars().getVariableNumsArray(), getVars().getVariableSizes()));
    } else {
      return TensorSufficientStatistics.createDense(getVars(), 
          new DenseTensorBuilder(getVars().getVariableNumsArray(), getVars().getVariableSizes()));
    }
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics, 
      Assignment assignment, double count) {
    double rescaling = (featureRescaling == null) ? 1.0 : 
      featureRescaling.getUnnormalizedProbability(assignment);
    ((TensorSufficientStatistics) statistics).incrementFeature(assignment, count * rescaling);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, 
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    if (marginal.getVars().size() == 0) {
      incrementSufficientStatisticsFromAssignment(statistics, conditionalAssignment,
          marginal.getTotalUnnormalizedProbability() * count / partitionFunction);
    } else {
      Tensor expectedFeatureCounts = marginal.coerceToDiscrete().getWeights();

      if (conditionalAssignment.size() > 0) {
        VariableNumMap vars = getVars().intersection(conditionalAssignment.getVariableNumsArray());
        SparseTensor pointDistribution = SparseTensor.singleElement(vars.getVariableNumsArray(),
            vars.getVariableSizes(), vars.assignmentToIntArray(conditionalAssignment), 1.0);
        if (featureRescalingTensor == null) {
          // This implementation is faster than the default increment
          // operation using the feature restrictions.
          ((TensorSufficientStatistics) statistics).incrementOuterProduct(pointDistribution,
              expectedFeatureCounts, count / partitionFunction);
        } else {
          Tensor increment = featureRescalingTensor.elementwiseProduct(
              pointDistribution.outerProduct(expectedFeatureCounts));
          ((TensorSufficientStatistics) statistics).increment(increment, count / partitionFunction);
        }
      } else {
        if (featureRescalingTensor == null) {
          ((TensorSufficientStatistics) statistics).increment(expectedFeatureCounts,
              count / partitionFunction);
        } else {
          ((TensorSufficientStatistics) statistics).increment(
              featureRescalingTensor.elementwiseProduct(expectedFeatureCounts), count / partitionFunction);
        }
      }
    }
  }

  private Tensor getFeatureWeights(SufficientStatistics parameters) {
    TensorSufficientStatistics featureParameters = (TensorSufficientStatistics) parameters;
    Tensor featureWeights = featureParameters.get();
    if (featureRescalingTensor == null) {
      return featureWeights;
    } else {
      return featureRescalingTensor.elementwiseProduct(featureWeights);
    }
  }


  private TableFactor getFeatureWeightFactor(SufficientStatistics parameters) {
    return new TableFactor(getVars(), getFeatureWeights(parameters));
  }
}
