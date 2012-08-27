package com.jayantkrish.jklol.models;

import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A factor representing a deterministic (functional) relationship between two
 * variables. As with {@link DiscreteObjectFactor}, this factor should only be
 * used when the values of this factor cannot be explicitly enumerated. If the
 * values can be enumerated, then a {@link TableFactor} will be more efficient.
 * 
 * This factor wraps a {@code Function} f. Assignments to this factor (x, y)
 * consistent with the function (i.e., f(x) == y) have probability 1.0, and all
 * other assignments have 0 probability.
 * 
 * @author jayant
 */
public class FunctionFactor extends WeightedRelationFactor {

  private static final long serialVersionUID = -5882067103650944714L;
  
  private final VariableNumMap domainVariable;
  private final VariableNumMap rangeVariable;

  private final Function<Object, Object> function;

  /**
   * Both/either of {@code domainFactor} and {@code rangeFactor} may be null, in
   * which case this factor represents a uniform probability distribution over
   * their possible values.
   * 
   * @param domainVariable
   * @param rangeVariable
   * @param function
   * @param domainFactor
   * @param rangeFactor
   */
  public FunctionFactor(VariableNumMap domainVariable, VariableNumMap rangeVariable,
      Function<Object, Object> function, Factor domainFactor, Factor rangeFactor) {
    super(domainVariable, rangeVariable, VariableNumMap.emptyMap(), domainFactor, rangeFactor);
    this.domainVariable = domainVariable;
    this.rangeVariable = rangeVariable;
    this.function = Preconditions.checkNotNull(function);
    Preconditions.checkArgument(domainVariable.size() == 1);
    Preconditions.checkArgument(rangeVariable.size() == 1);
  }

  @Override
  protected double getAssignmentRelationProbability(Assignment assignment) {
    Object rangeValue = function.apply(assignment.getValue(domainVariable.getOnlyVariableNum()));
    if (rangeValue.equals(assignment.getValue(rangeVariable.getOnlyVariableNum()))) {
      return 1.0;
    }
    return 0.0;
  }

  @Override
  protected DiscreteObjectFactor constructJointDistribution(Factor domainFactor,
      Factor rangeFactor) {
    DiscreteObjectFactor domainAsObjectFactor = (DiscreteObjectFactor) domainFactor;
    Map<Assignment, Double> resultProbs = Maps.newHashMap();
    for (Assignment domainValue : domainAsObjectFactor.assignments()) {
      Object rangeValue = function.apply(domainValue.getOnlyValue());
      if (rangeFactor == null) {
        resultProbs.put(rangeVariable.outcomeArrayToAssignment(rangeValue).union(domainValue),
            domainAsObjectFactor.getUnnormalizedProbability(domainValue));
      } else {
        double rangeProb = rangeFactor.getUnnormalizedProbability(rangeValue);
        if (rangeProb > 0) {
          resultProbs.put(rangeVariable.outcomeArrayToAssignment(rangeValue).union(domainValue),
              domainAsObjectFactor.getUnnormalizedProbability(domainValue) * rangeProb);
        }
      }
    }

    return new DiscreteObjectFactor(domainVariable.union(rangeVariable), resultProbs);
  }

  @Override
  protected Factor replaceFactors(VariableRelabeling variableRelabeling,
      Factor newDomainFactor, Factor newRangeFactor) {
    return new FunctionFactor(variableRelabeling.apply(domainVariable),
        variableRelabeling.apply(rangeVariable), function, newDomainFactor, newRangeFactor);
  }
}
