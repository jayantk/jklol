package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@link ParametricFactor} whose parameters are weights of log-linear feature
 * functions.
 * 
 * {@code DiscreteLogLinearFactor} can represent sparse factors (with 0
 * probability outcomes) through a set of initial weights for the returned
 * factor. Each initial weight should be set to either 0 or 1, and outcomes with
 * 0 weight will retain that weight regardless of their feature values.
 */
public class DiscreteLogLinearFactor extends AbstractParametricFactor<SufficientStatistics> {

  private final VariableNumMap featureVariable;
  // A features x assignments tensor.
  private final TableFactor featureValues;
  private final TableFactor initialWeights;

  /**
   * Creates a {@code DiscreteLogLinearFactor} over {@code variables},
   * parameterized by features with the given {@code featureValues}. The
   * returned factor is dense, and constructs factors that assign positive
   * probability to all possible assignments.
   * 
   * @param vars
   * @param features
   */
  public DiscreteLogLinearFactor(VariableNumMap variables, VariableNumMap featureVariable, 
      TableFactor featureValues) {
    super(variables);
    this.featureVariable = featureVariable;
    Preconditions.checkArgument(featureVariable.size() == 1);
    this.featureValues = Preconditions.checkNotNull(featureValues);
    Preconditions.checkArgument(featureValues.getVars().equals(
        variables.union(featureVariable)));
    this.initialWeights = TableFactorBuilder.ones(variables).build();
  }

  /**
   * Creates a {@code DiscreteLogLinearFactor} over {@code variables},
   * parameterized by {@code features}. The returned factor is sparse, and
   * assignments with a 0 weight in {@code initialWeights} will be assigned 0
   * weight in all constructed factors.
   * 
   * @param vars
   * @param features
   */
  public DiscreteLogLinearFactor(VariableNumMap variables, VariableNumMap featureVariable, 
      TableFactor featureValues, TableFactor initialWeights) {
    super(variables);
    this.featureVariable = featureVariable;
    Preconditions.checkArgument(featureVariable.size() == 1);
    this.featureValues = Preconditions.checkNotNull(featureValues);
    Preconditions.checkArgument(featureValues.getVars().equals(
        variables.union(featureVariable)));
    this.initialWeights = initialWeights;
  }
  
  public TableFactor getFeatureValues() {
    return featureValues;
  }

  // ///////////////////////////////////////////////////////////
  // Required methods for ParametricFactor
  // ///////////////////////////////////////////////////////////

  @Override
  public TableFactor getFactorFromParameters(SufficientStatistics parameters) {
    // TODO(jayantk): This is probably not the most efficient way to build this
    // factor.
    Tensor featureWeights = getFeatureWeights(parameters).build();
    Tensor logProbs = featureValues.getWeights().elementwiseProduct(featureWeights)
        .sumOutDimensions(Ints.asList(featureVariable.getOnlyVariableNum()));
    
    StringBuilder foo = new StringBuilder();
    // Maintain the sparsity pattern, but fast. The result is equivalent
    // to computing initialWeights.elementwiseProduct(logProbs.elementwiseExp());
    SparseTensorBuilder builder = SparseTensorBuilder.copyOf(initialWeights.getWeights());
    Iterator<KeyValue> logProbIter = logProbs.keyValueIterator();
    while (logProbIter.hasNext()) {
      KeyValue keyValue = logProbIter.next();
      foo.append(Arrays.toString(keyValue.getKey()) + " : " + keyValue.getValue());
      builder.multiplyEntry(Math.exp(keyValue.getValue()), keyValue.getKey());
    }
    
    SparseTensor factorWeights = builder.build();
    return new TableFactor(getVars(), factorWeights);
  }

  @Override
  public TensorSufficientStatistics getNewSufficientStatistics() {
    return new TensorSufficientStatistics(Arrays.<TensorBuilder> asList(
        new DenseTensorBuilder(new int[] { featureVariable.getOnlyVariableNum() }, 
            new int[] { featureVariable.getDiscreteVariables().get(0).numValues() })));
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics, 
      Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNums()));
    Assignment subAssignment = assignment.intersection(getVars().getVariableNums());
    
    TensorBuilder weights = getFeatureWeights(statistics);

    // Get a factor containing only the feature variable.
    Tensor assignmentFeatures = featureValues.conditional(subAssignment).getWeights()
        .elementwiseProduct(SparseTensor.getScalarConstant(count));
    weights.increment(assignmentFeatures);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    TensorBuilder weights = getFeatureWeights(statistics);
    
    // Compute expected feature counts based on the input marginal distribution.
    DiscreteFactor expectedFeatureCounts = featureValues.conditional(conditionalAssignment)
        .product(marginal).marginalize(marginal.getVars().getVariableNums());
    Preconditions.checkState(expectedFeatureCounts.getVars().size() == 1 
        && expectedFeatureCounts.getVars().containsAll(featureVariable));

    weights.increment(expectedFeatureCounts.getWeights().elementwiseProduct(
        SparseTensor.getScalarConstant(count / partitionFunction)));
  }

  private TensorBuilder getFeatureWeights(SufficientStatistics parameters) {
    TensorSufficientStatistics featureParameters = (TensorSufficientStatistics) parameters;
    // Check that the parameters are a vector of the appropriate size.
    Preconditions.checkArgument(featureParameters.size() == 1);
    Preconditions.checkArgument(featureParameters.get(0).getDimensionNumbers().length == 1);
    Preconditions.checkArgument(featureParameters.get(0).getDimensionSizes()[0] == 
        featureVariable.getDiscreteVariables().get(0).numValues());
    return featureParameters.get(0);
  }

  // ////////////////////////////////////////////////////////////
  // Other methods
  // ////////////////////////////////////////////////////////////

  /**
   * Creates and returns a {@code DiscreteLogLinearFactor} over {@code vars}
   * which is parameterized by indicator functions. The returned factor has one
   * indicator feature for every possible assignment to {@code vars}.
   * {@code initialWeights} determines the sparsity pattern of the factors
   * created by the returned parametric factor; use
   * {@link TableFactorBuilder#ones} to create a dense factor.
   * 
   * {@code vars} must contain only {@link DiscreteVariable}s.
   * 
   * @param vars
   * @return
   */
  public static DiscreteLogLinearFactor createIndicatorFactor(VariableNumMap vars,
      TableFactorBuilder initialWeights) {
    Preconditions.checkArgument(vars.size() == vars.getDiscreteVariables().size());
    
    int featureVarNum = Collections.max(vars.getVariableNums()) + 1;
    // The "name" of an indicator feature is the assignment that it indicates. 
    List<Assignment> features = Lists.newArrayList();
    Iterators.addAll(features, initialWeights.assignmentIterator());
    DiscreteVariable featureVariable = new DiscreteVariable("indicator features", features);
    VariableNumMap featureVarMap = VariableNumMap.singleton(featureVarNum, "features", featureVariable);
    
    TableFactorBuilder featureValueBuilder = new TableFactorBuilder(vars.union(featureVarMap));
    for (Assignment featureAssignment : features) {
      Assignment newAssignment = featureAssignment.union(featureVarMap
          .outcomeArrayToAssignment(featureAssignment));
      featureValueBuilder.setWeight(newAssignment, 1.0);
    }
    
    return new DiscreteLogLinearFactor(vars, featureVarMap, 
        featureValueBuilder.build(), initialWeights.build());
  }

  /**
   * Same as {@link #createIndicatorFactor(VariableNumMap, TableFactorBuilder)},
   * using a the all-ones factor builder.
   * 
   * @param vars
   * @return
   */
  public static DiscreteLogLinearFactor createIndicatorFactor(VariableNumMap vars) {
    return DiscreteLogLinearFactor.createIndicatorFactor(vars, TableFactorBuilder.ones(vars));
  }
}
