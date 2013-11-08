package com.jayantkrish.jklol.models;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A conditional probability distribution created by normalizing
 * a collection of factors.
 *   
 * @author jayantk
 */
public class NormalizingFactor extends AbstractConditionalFactor {
  private static final long serialVersionUID = 2L;
  
  private final VariableNumMap inputVars;
  private final VariableNumMap conditionalVars;
  private final VariableNumMap outputVars;
  private final List<Factor> factors;
  
  private final VariableNumMap conditionalAndInputVars;

  /**
   * 
   * @param inputVars Variables whose values must be provided
   * before normalization occurs.
   * @param conditionalVars This factor defines a conditional
   * distribution over {@code outputVars} given these variables.
   * @param outputVars Variables which this factor defines a
   * normalized probability distribution over, given the values
   * of {@code conditionalVars}.
   * @param factors Factors to be normalized.
   */
  public NormalizingFactor(VariableNumMap inputVars, VariableNumMap conditionalVars,
      VariableNumMap outputVars, List<Factor> factors) {
    super(VariableNumMap.unionAll(inputVars, conditionalVars, outputVars));
    this.inputVars = Preconditions.checkNotNull(inputVars);
    this.conditionalVars = Preconditions.checkNotNull(conditionalVars);
    this.outputVars = Preconditions.checkNotNull(outputVars);
    this.factors = Preconditions.checkNotNull(factors);
    
    this.conditionalAndInputVars = conditionalVars.union(inputVars);
  }
  
  public List<Factor> getFactors() {
    return factors;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    return conditional(assignment).getUnnormalizedProbability(Assignment.EMPTY);
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    return Math.log(getUnnormalizedProbability(assignment));
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    VariableNumMap newInputVars = relabeling.apply(inputVars);
    VariableNumMap newConditionalVars = relabeling.apply(conditionalVars);
    VariableNumMap newOutputVars = relabeling.apply(outputVars);

    List<Factor> relabeledFactors = Lists.newArrayList();
    for (Factor factor : factors) {
      relabeledFactors.add(factor.relabelVariables(relabeling));
    }

    return new NormalizingFactor(newInputVars, newConditionalVars, newOutputVars, relabeledFactors);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    if (!assignment.containsAny(getVars().getVariableNumsArray())) {
      return this;
    }
    // LogFunction log = LogFunctions.getLogFunction();
    // log.startTimer("conditional_normalized");
    Preconditions.checkArgument(assignment.containsAll(inputVars.getVariableNumsArray()));
    Assignment inputAssignment = assignment.intersection(conditionalAndInputVars);

    List<Factor> conditionalFactors = Lists.newArrayList();
    for (Factor factor : factors) {
      conditionalFactors.add(factor.conditional(inputAssignment));
    }
    Factor result = Factors.product(conditionalFactors);

    // All inputs are given. Do normalization and return
    // the resulting factor (conditioning on any other given values).
    Factor normalization = result.marginalize(outputVars);
    Factor finalResult = result.product(normalization.inverse()).conditional(assignment);
    // log.stopTimer("conditional_normalized");
    return finalResult;
  }
}
