package com.jayantkrish.jklol.models.loglinear;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Loglinear factor containing indicator features that
 * are a cross product of various tensor maps applied to
 * each dimension of the input factor weights.
 * 
 * @author jayantk
 *
 */
public class BackoffLogLinearFactor extends AbstractParametricFactor {
  private static final long serialVersionUID = 1L;

  private final ParametricFactor family;
  private final List<Tensor> dimensionMaps;
  private final List<Tensor> inverseDimensionMaps;
  
  /**
   * TODO: Certain constraints exist on the dimension number pattern
   * between variables, family, and dimensionMaps.
   * See {@link BackoffLogLinearFactorTest} for example usage.
   * 
   * @param variables
   * @param family
   * @param dimensionMaps
   */
  public BackoffLogLinearFactor(VariableNumMap variables, ParametricFactor family,
      List<Tensor> dimensionMaps) {
    super(variables);
    this.family = Preconditions.checkNotNull(family);
    this.dimensionMaps = ImmutableList.copyOf(dimensionMaps);
    
    this.inverseDimensionMaps = Lists.newArrayList();
    for (Tensor dimMap : dimensionMaps) {
      int[] originalKeys = dimMap.getDimensionNumbers();
      Preconditions.checkArgument(originalKeys.length == 2);
      int[] newKeys = new int[] { originalKeys[1] + 1, originalKeys[1] };
      this.inverseDimensionMaps.add(dimMap.relabelDimensions(newKeys));
    }
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return family.getNewSufficientStatistics();
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics parameters) {
    DiscreteFactor f = family.getModelFromParameters(parameters).coerceToDiscrete();
    Tensor originalWeights = f.getWeights();
    Tensor weights = originalWeights.elementwiseLogSparse();

    for (Tensor dimMap : inverseDimensionMaps) {
      if (dimMap != null) {
        weights = weights.matrixInnerProduct(dimMap);
      }
    }
    return new TableFactor(getVars(), weights.relabelDimensions(
        getVars().getVariableNumsArray()).elementwiseExp());
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Assignment assignment, double count) {
    Factor marginal = TableFactor.pointDistribution(getVars(), assignment);
    incrementSufficientStatisticsFromMarginal(statistics, currentParameters, marginal,
        Assignment.EMPTY, count, 1.0);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Factor marginal, Assignment conditionalAssignment,
      double count, double partitionFunction) {
    VariableNumMap vars = getVars();
    Assignment a = conditionalAssignment.intersection(vars); 
    if (a.size() > 0) {
      VariableNumMap conditionalVars = vars.intersection(a.getVariableNumsArray());
      marginal = marginal.outerProduct(TableFactor.pointDistribution(conditionalVars, a));
    }
    
    Tensor weights = marginal.coerceToDiscrete().getWeights();
    for (Tensor dimMap : dimensionMaps) {
      if (dimMap != null) {
        weights = weights.matrixInnerProduct(dimMap);
      }
    }
    TableFactor familyMarginal = new TableFactor(family.getVars(), weights);
    family.incrementSufficientStatisticsFromMarginal(statistics, currentParameters,
        familyMarginal, Assignment.EMPTY, count, partitionFunction);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return family.getParameterDescription(parameters, numFeatures);
  }
}
