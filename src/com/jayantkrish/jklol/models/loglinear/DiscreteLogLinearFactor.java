package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Factors;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.DiscreteLogLinearFactorProto;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphProtos.ParametricFactorProto.Type;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A {@link ParametricFactor} whose parameters are weights of log-linear feature
 * functions.
 * <p>
 * {@code DiscreteLogLinearFactor} can represent sparse factors (with 0
 * probability outcomes) through a set of initial weights for the returned
 * factor. Each initial weight should be set to either 0 or 1, and outcomes with
 * 0 weight will retain that weight regardless of their feature values.
 */
public class DiscreteLogLinearFactor extends AbstractParametricFactor {

  // This factor has one weight for each assignment to featureVariables.
  private final VariableNumMap featureVariables;
  // A features x assignments tensor.
  private final DiscreteFactor featureValues;
  // Contains a sparsity pattern which must be maintained by this tensor. (i.e.,
  // a set of 0 probability assignments). If null, then no sparsity pattern is
  // enforced.
  private final DiscreteFactor initialWeights;

  /**
   * Creates a {@code DiscreteLogLinearFactor} over {@code variables},
   * parameterized by features with the given {@code featureValues}. The
   * returned factor is dense, and constructs factors that assign positive
   * probability to all possible assignments.
   * 
   * @param variables
   * @param featureVariables
   * @param featureValues
   */
  public DiscreteLogLinearFactor(VariableNumMap variables,
      VariableNumMap featureVariables, DiscreteFactor featureValues) {
    super(variables);
    this.featureVariables = featureVariables;
    this.featureValues = Preconditions.checkNotNull(featureValues);
    Preconditions.checkArgument(featureValues.getVars().equals(
        variables.union(featureVariables)));
    this.initialWeights = null;
  }

  /**
   * Creates a {@code DiscreteLogLinearFactor} over {@code variables},
   * parameterized by {@code features}. If {@code initialWeights} is non-null,
   * the returned factor is sparse and assignments with a 0 weight in
   * {@code initialWeights} will be assigned 0 weight in all constructed
   * factors.
   * 
   * @param variables
   * @param featureVariables
   * @param featureValues
   * @param initialWeights
   */
  public DiscreteLogLinearFactor(VariableNumMap variables, VariableNumMap featureVariables, 
      DiscreteFactor featureValues, DiscreteFactor initialWeights) {
    super(variables);
    this.featureVariables = featureVariables;
    this.featureValues = Preconditions.checkNotNull(featureValues);
    Preconditions.checkArgument(featureValues.getVars().equals(
        variables.union(featureVariables)));
    this.initialWeights = initialWeights;
  }

  public DiscreteFactor getFeatureValues() {
	    return featureValues;
	  }
  
  public VariableNumMap getFeatureVariables() {
	    return featureVariables;
  }

  // ///////////////////////////////////////////////////////////
  // Required methods for ParametricFactor
  // ///////////////////////////////////////////////////////////

  @Override
  public DiscreteFactor getFactorFromParameters(SufficientStatistics parameters) {
    Tensor featureWeights = getFeatureWeights(parameters).build();
    Tensor logProbs = featureValues.getWeights().elementwiseProduct(featureWeights)
        .sumOutDimensions(featureVariables.getVariableNums());

    return exponentiateLogProbs(logProbs); 
  }
  
  private TableFactor exponentiateLogProbs(Tensor logProbs) {
    // Maintain the sparsity pattern, but fast. The result is equivalent
    // to initialWeights.elementwiseProduct(logProbs.elementwiseExp());
    if (initialWeights != null) {
      Tensor initialTensor = initialWeights.getWeights();
      double[] initialWeightValues = initialWeights.getWeights().getValues();
      double[] logProbValues = logProbs.getValues();
      double[] newWeights = Arrays.copyOf(initialWeightValues, initialWeightValues.length);

      int initialWeightIndex = 0, logProbIndex = 0;
      long initialWeightKeyNum, logProbKeyNum;
      while (initialWeightIndex < newWeights.length && logProbIndex < logProbValues.length) {
        initialWeightKeyNum = initialTensor.indexToKeyNum(initialWeightIndex);
        logProbKeyNum = logProbs.indexToKeyNum(logProbIndex);

        if (initialWeightKeyNum > logProbKeyNum) {
          logProbIndex++;
        } else if (initialWeightKeyNum < logProbKeyNum) {
          initialWeightIndex++;
        } else {
          // Equals.
          newWeights[initialWeightIndex] *= Math.exp(logProbValues[logProbIndex]);

          logProbIndex++;
          initialWeightIndex++;
        }
      }

      return new TableFactor(getVars(), initialTensor.replaceValues(newWeights));
    } else {
      return new TableFactor(getVars(), logProbs.elementwiseExp());
    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    TensorBuilder weights = getFeatureWeights(parameters);
    TableFactor weightFactor = new TableFactor(featureVariables, weights.build());
    Iterator<Outcome> outcomeIter = weightFactor.outcomeIterator();
    StringBuilder sb = new StringBuilder();
    while (outcomeIter.hasNext()) {
      sb.append(outcomeIter.next() + "\n");
    }
    return sb.toString();
  }
  
  @Override
  public String getParameterDescriptionXML(SufficientStatistics parameters) {
    TensorBuilder weights = getFeatureWeights(parameters);
    TableFactor weightFactor = new TableFactor(featureVariables, weights.build());
    Iterator<Outcome> outcomeIter = weightFactor.outcomeIterator();
    StringBuilder sb = new StringBuilder();
    while (outcomeIter.hasNext()) {
      sb.append("<outcome>\n"+outcomeIter.next().toXML() + "</outcome>\n");
    }
    return sb.toString();
  }

  @Override
  public TensorSufficientStatistics getNewSufficientStatistics() {
    return new TensorSufficientStatistics(featureVariables, 
        new DenseTensorBuilder(Ints.toArray(featureVariables.getVariableNums()),
            featureVariables.getVariableSizes()));
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
    Preconditions.checkState(expectedFeatureCounts.getVars().equals(featureVariables));

    weights.increment(expectedFeatureCounts.getWeights().elementwiseProduct(
        SparseTensor.getScalarConstant(count / partitionFunction)));
  }

  @Override
  public ParametricFactorProto toProto(IndexedList<Variable> variableTypeIndex) {
    ParametricFactorProto.Builder builder = ParametricFactorProto.newBuilder();
    builder.setType(Type.DISCRETE_LOG_LINEAR);
    builder.setVariables(getVars().toProto(variableTypeIndex));

    DiscreteLogLinearFactorProto.Builder llBuilder = builder.getDiscreteLogLinearFactorBuilder();
    llBuilder.setFeatureVariables(featureVariables.toProto(variableTypeIndex));
    llBuilder.setFeatureValues(featureValues.toProto(variableTypeIndex));
    if (initialWeights != null) {
      llBuilder.setInitialWeights(initialWeights.toProto(variableTypeIndex));
    }

    return builder.build();
  }

  public static DiscreteLogLinearFactor fromProto(ParametricFactorProto proto,
      IndexedList<Variable> variableTypeIndex) {
    Preconditions.checkArgument(proto.getType().equals(Type.DISCRETE_LOG_LINEAR));
    Preconditions.checkArgument(proto.hasDiscreteLogLinearFactor());

    DiscreteLogLinearFactorProto llProto = proto.getDiscreteLogLinearFactor();
    VariableNumMap vars = VariableNumMap.fromProto(proto.getVariables(), variableTypeIndex);
    VariableNumMap featureVars = VariableNumMap.fromProto(llProto.getFeatureVariables(),
        variableTypeIndex);
    DiscreteFactor featureValues = Factors.fromProto(llProto.getFeatureValues(),
        variableTypeIndex).coerceToDiscrete();
    DiscreteFactor initialWeights = null;
    if (llProto.hasInitialWeights()) {
      initialWeights = Factors.fromProto(llProto.getInitialWeights(),
          variableTypeIndex).coerceToDiscrete();
    }
    return new DiscreteLogLinearFactor(vars, featureVars, featureValues, initialWeights);
  }

  private TensorBuilder getFeatureWeights(SufficientStatistics parameters) {
    TensorSufficientStatistics featureParameters = (TensorSufficientStatistics) parameters;
    // Check that the parameters are a vector of the appropriate size.
    Preconditions.checkArgument(Arrays.equals(featureParameters.get().getDimensionSizes(),
        featureVariables.getVariableSizes()));
    return featureParameters.get();
  }

  // ////////////////////////////////////////////////////////////
  // Static methods for constructing DiscreteLogLinearFactors
  // ////////////////////////////////////////////////////////////

  public static DiscreteFactor createIndicatorFeatureTensor(VariableNumMap vars, int featureVarNum,
      TableFactorBuilder initialWeights) {
    // The "name" of an indicator feature is the assignment that it indicates.
    List<List<Object>> features = Lists.newArrayList();
    Iterator<Assignment> assignmentIterator = initialWeights != null ?
        initialWeights.assignmentIterator() : new AllAssignmentIterator(vars);
    while (assignmentIterator.hasNext()) {
      features.add(assignmentIterator.next().getValues());
    }
    DiscreteVariable featureVariable = new DiscreteVariable("indicator features", features);
    VariableNumMap featureVarMap = VariableNumMap.singleton(featureVarNum, "features-" + featureVarNum,
        featureVariable);

    TableFactorBuilder featureValueBuilder = new TableFactorBuilder(vars.union(featureVarMap),
        SparseTensorBuilder.getFactory());
    for (List<Object> featureValues : features) {
      Assignment newAssignment = vars.outcomeToAssignment(featureValues)
          .union(featureVarMap.outcomeArrayToAssignment(featureValues));
      featureValueBuilder.setWeight(newAssignment, 1.0);
    }
    return featureValueBuilder.build();
  }

  /**
   * Creates and returns a {@code DiscreteLogLinearFactor} over {@code vars}
   * which is parameterized by indicator functions. The returned factor has one
   * indicator feature for every possible assignment to {@code vars}.
   * {@code initialWeights} determines the sparsity pattern of the factors
   * created by the returned parametric factor; pass {@code null} to create a
   * dense factor.
   * <p> 
   * {@code vars} must contain only {@link DiscreteVariable}s.
   * 
   * @param vars
   * @param initialWeights
   * @return
   */
  public static DiscreteLogLinearFactor createIndicatorFactor(VariableNumMap vars,
      TableFactorBuilder initialWeights) {
    Preconditions.checkArgument(vars.size() == vars.getDiscreteVariables().size());

    int featureVarNum = Collections.max(vars.getVariableNums()) + 1;

    DiscreteFactor featureValues = createIndicatorFeatureTensor(vars, featureVarNum, initialWeights);
    VariableNumMap featureVarMap = featureValues.getVars().intersection(Arrays.asList(featureVarNum));

    TableFactor initialWeightFactor = (initialWeights != null) ? initialWeights.build() : null;
    return new DiscreteLogLinearFactor(vars, featureVarMap, featureValues, initialWeightFactor);
  }

  /**
   * Same as {@link #createIndicatorFactor(VariableNumMap, TableFactorBuilder)},
   * using a the all-ones factor builder.
   * 
   * @param vars
   * @return
   */
  public static DiscreteLogLinearFactor createIndicatorFactor(VariableNumMap vars) {
    return DiscreteLogLinearFactor.createIndicatorFactor(vars, null);
  }
}