package com.jayantkrish.jklol.boost;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.AbstractConditionalFactor;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Factors;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.Assignment;

public class EnsembleConditionalFactor extends AbstractConditionalFactor {

  private static final long serialVersionUID = 1L;
  
  private final List<Factor> factors;
  private final double[] weights;

  public EnsembleConditionalFactor(VariableNumMap vars, List<Factor> factors, double[] weights) {
    super(vars);
    this.factors = ImmutableList.copyOf(factors);
    this.weights = ArrayUtils.copyOf(weights, weights.length);
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    return Math.exp(getUnnormalizedLogProbability(assignment));
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    double logProb = 0.0;
    for (int i = 0; i < factors.size(); i++) {
      logProb += factors.get(i).getUnnormalizedLogProbability(assignment) * weights[i];
    }
    return logProb;
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    List<Factor> newFactors = Lists.newArrayList();
    for (Factor factor : factors) {
      newFactors.add(factor.relabelVariables(relabeling));
    }
    
    return new EnsembleConditionalFactor(relabeling.apply(getVars()), newFactors, weights);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    List<Factor> results = Lists.newArrayList();
    for (int i = 0; i < factors.size(); i++) {
      DiscreteFactor result = factors.get(i).conditional(assignment).coerceToDiscrete();
      Tensor logWeights = result.getWeights().elementwiseLog().elementwiseProduct(weights[i]);
      results.add(new TableFactor(result.getVars(), logWeights.elementwiseExp()));
    }
    return Factors.product(results);
  }
}
