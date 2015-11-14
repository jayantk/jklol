package com.jayantkrish.jklol.models.bayesnet;

import java.util.Arrays;
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
import com.jayantkrish.jklol.training.FactorLoglikelihoodOracle;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Wrapper for a loglinear factor that allows it to be used
 * in a Bayes Net. This approach is similar to the one used in
 * the paper:
 * <p>
 * Painless Unsupervised Learning with Features, NAACL 2010
 * <p>
 * The current implementation does not work for conditional
 * distributions, only joint distributions over a set of
 * variables.
 * 
 * @author jayant
 *
 */
public class LogLinearCptFactor extends AbstractParametricFactor {
  private static final long serialVersionUID = 1L;

  private final ParametricFactor factor;
  private final VariableNumMap conditionalVars;

  private final GradientOptimizer optimizer;
  
  public LogLinearCptFactor(ParametricFactor factor, VariableNumMap conditionalVars,
      GradientOptimizer optimizer) {
    super(factor.getVars());
    this.factor = Preconditions.checkNotNull(factor);
    this.conditionalVars = Preconditions.checkNotNull(conditionalVars);
    this.optimizer = Preconditions.checkNotNull(optimizer);
  }

  @Override
  public Factor getModelFromParameters(SufficientStatistics parameters) {
    Factor target = ((TensorSufficientStatistics) parameters).getFactor();
    FactorLoglikelihoodOracle oracle = new FactorLoglikelihoodOracle(factor, target, conditionalVars);
    SufficientStatistics loglinearParameters = factor.getNewSufficientStatistics();

    List<Void> trainingData = Arrays.asList((Void) null);
    loglinearParameters = optimizer.train(oracle, loglinearParameters, trainingData);
    
    Factor predicted = factor.getModelFromParameters(loglinearParameters);
    return predicted.product(predicted.marginalize(predicted.getVars().removeAll(conditionalVars)).inverse());
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Assignment assignment, double count) {
    Preconditions.checkArgument(assignment.containsAll(getVars().getVariableNumsArray()));
    TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) statistics;
    tensorStats.incrementFeature(assignment, count);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Factor marginal, Assignment conditionalAssignment,
      double count, double partitionFunction) {
    VariableNumMap assignmentVars = getVars().intersection(conditionalAssignment.getVariableNumsArray());
    if (assignmentVars.size() > 0) {
      TableFactor assignmentDist = TableFactor.pointDistribution(assignmentVars, conditionalAssignment);
      marginal = marginal.outerProduct(assignmentDist);
    }

    ((TensorSufficientStatistics) statistics).increment(marginal.coerceToDiscrete().getWeights(),
        count / partitionFunction);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return TensorSufficientStatistics.createDense(getVars(),
        new DenseTensorBuilder(getVars().getVariableNumsArray(), getVars().getVariableSizes()));
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    DiscreteFactor factor = getModelFromParameters(parameters).coerceToDiscrete();
    return factor.describeAssignments(factor.getMostLikelyAssignments(numFeatures));
  }
}
