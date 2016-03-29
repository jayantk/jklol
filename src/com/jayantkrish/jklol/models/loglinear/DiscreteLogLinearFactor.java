package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.tensor.DenseTensorBuilder;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

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

  private static final long serialVersionUID = 7327454945090137844L;

  // This factor has one weight for each assignment to featureVariables.
  private final VariableNumMap featureVariables;
  // A features x assignments tensor.
  private final DiscreteFactor featureValues;
  // Contains a sparsity pattern which must be maintained by this tensor. (i.e.,
  // a set of 0 probability assignments). If null, then no sparsity pattern is
  // enforced.
  private final DiscreteFactor initialWeights;
  
  // Variable name assigned to the feature variable created by static methods
  // of this class.
  public static String FEATURE_VAR_NAME = "features";

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
  public DiscreteFactor getModelFromParameters(SufficientStatistics parameters) {
    Tensor featureWeights = getFeatureWeights(parameters);
    Tensor logProbs = featureValues.getWeights().elementwiseProduct(featureWeights)
        .sumOutDimensions(featureVariables.getVariableNumsArray());

    return exponentiateLogProbs(logProbs); 
  }

  private TableFactor exponentiateLogProbs(Tensor logProbs) {
    // Maintain the sparsity pattern, but fast. The result is equivalent
    // to initialWeights.elementwiseProduct(logProbs.elementwiseExp());
    if (initialWeights != null) {
      Tensor initialTensor = initialWeights.getWeights();
      double[] initialWeightValues = initialWeights.getWeights().getValues();
      double[] logProbValues = logProbs.getValues();
      double[] newWeights = new double[initialWeightValues.length];
      System.arraycopy(initialWeightValues, 0, newWeights, 0, initialWeightValues.length);

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
      return new TableFactor(getVars(), new LogSpaceTensorAdapter(logProbs)); 
    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    Tensor weights = getFeatureWeights(parameters);
    TableFactor weightFactor = new TableFactor(featureVariables, weights);

    List<Assignment> assignments = weightFactor.product(weightFactor).getMostLikelyAssignments(numFeatures);
    return weightFactor.describeAssignments(assignments);
  }

  @Override
  public TensorSufficientStatistics getNewSufficientStatistics() {
    return new TensorSufficientStatistics(featureVariables, 
        new DenseTensorBuilder(featureVariables.getVariableNumsArray(),
            featureVariables.getVariableSizes()));
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNumsArray()));
    Assignment subAssignment = assignment.intersection(getVars().getVariableNumsArray());

    // Get a factor containing only the feature variable.
    Tensor assignmentFeatures = featureValues.conditional(subAssignment).getWeights();
    ((TensorSufficientStatistics) gradient).increment(assignmentFeatures, count);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Factor marginal, Assignment conditionalAssignment,
      double count, double partitionFunction) {
      // Compute expected feature counts based on the input marginal distribution.
      DiscreteFactor assignmentConditional = featureValues.conditional(conditionalAssignment);
      Preconditions.checkState(assignmentConditional.getVars()
          .removeAll(marginal.getVars()).equals(featureVariables),
          "Not all variables are covered in the incrementing assignment.");
      
      Tensor marginalWeights = marginal.coerceToDiscrete().getWeights();

      ((TensorSufficientStatistics) gradient).incrementInnerProduct(assignmentConditional.getWeights(),
          marginalWeights, count / partitionFunction);
  }

  private Tensor getFeatureWeights(SufficientStatistics parameters) {
    TensorSufficientStatistics featureParameters = (TensorSufficientStatistics) parameters;
    // Check that the parameters are a vector of the appropriate size.
    Preconditions.checkArgument(Arrays.equals(featureParameters.get().getDimensionSizes(),
        featureVariables.getVariableSizes()), "DiscreteLogLinearFactor: Parameters don't have the right dimensionality.");
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
    return featureValueBuilder.build().cacheWeightPermutations();
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

    int featureVarNum = Ints.max(vars.getVariableNumsArray()) + 1;

    DiscreteFactor featureValues = createIndicatorFeatureTensor(vars, featureVarNum, initialWeights);
    VariableNumMap featureVarMap = featureValues.getVars().intersection(featureVarNum);

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
  
  /**
   * Creates a {@code DiscreteLogLinearFactor} by applying {@code featureGen}
   * to each assignment in {@code factor} with non-zero weight. Returns a
   * sparse factor where only these assignments are assigned non-zero weight.
   * 
   * @param factor
   * @param featureGen
   * @return
   */
  public static DiscreteLogLinearFactor fromFeatureGeneratorSparse(DiscreteFactor factor,
      FeatureGenerator<Assignment, String> featureGen) {
    Iterator<Outcome> iter = factor.outcomeIterator();
    Set<String> featureNames = Sets.newHashSet();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      for (Map.Entry<String, Double> f : featureGen.generateFeatures(o.getAssignment()).entrySet()) {
        featureNames.add(f.getKey());
      }
    }
    
    List<String> featureNamesSorted = Lists.newArrayList(featureNames);
    Collections.sort(featureNamesSorted);
    
    DiscreteVariable featureType = new DiscreteVariable(FEATURE_VAR_NAME, featureNamesSorted);
    VariableNumMap featureVar = VariableNumMap.singleton(
        Ints.max(factor.getVars().getVariableNumsArray()) + 1, FEATURE_VAR_NAME, featureType);
    TableFactorBuilder builder = new TableFactorBuilder(
        factor.getVars().union(featureVar), SparseTensorBuilder.getFactory());
    
    iter = factor.outcomeIterator();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      for (Map.Entry<String, Double> f : featureGen.generateFeatures(o.getAssignment()).entrySet()) {
        Assignment featureAssignment = featureVar.outcomeArrayToAssignment(f.getKey());
        builder.setWeight(o.getAssignment().union(featureAssignment), f.getValue());
      }
    }

    DiscreteFactor featureFactor = builder.build();
    return new DiscreteLogLinearFactor(factor.getVars(), featureVar, featureFactor, factor);
  }
}