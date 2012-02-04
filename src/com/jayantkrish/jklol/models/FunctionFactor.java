package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
public class FunctionFactor extends AbstractFactor {

  private final VariableNumMap domainVariable;
  private final VariableNumMap rangeVariable;

  private final DiscreteObjectFactor domainFactor;

  private final Function<Object, Object> function;

  /**
   * {@code domainFactor} may be null, in which case this factor represents a
   * uniform probability distribution over the domain variable's values.
   * 
   * @param domainVariable
   * @param rangeVariable
   * @param function
   * @param domainFactor
   */
  public FunctionFactor(VariableNumMap domainVariable, VariableNumMap rangeVariable,
      Function<Object, Object> function, DiscreteObjectFactor domainFactor) {
    super(domainVariable.union(rangeVariable));
    this.domainVariable = Preconditions.checkNotNull(domainVariable);
    this.rangeVariable = Preconditions.checkNotNull(rangeVariable);
    this.domainFactor = domainFactor;
    this.function = Preconditions.checkNotNull(function);
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Object inputValue = assignment.getValue(domainVariable.getOnlyVariableNum());
    Object outputValue = assignment.getValue(rangeVariable.getOnlyVariableNum());
    if (outputValue.equals(function.apply(inputValue))) {
      if (domainFactor == null) {
        return 1.0;
      } else {
        return domainFactor.getUnnormalizedProbability(inputValue);
      }
    } else {
      return 0.0;
    }
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    Preconditions.checkNotNull(inboundMessages);

    Set<SeparatorSet> possibleOutbound = Sets.newHashSet();
    for (Map.Entry<SeparatorSet, Factor> inboundMessage : inboundMessages.entrySet()) {
      if (inboundMessage.getValue() == null) {
        possibleOutbound.add(inboundMessage.getKey());
      }
    }

    if (possibleOutbound.size() == 1) {
      return possibleOutbound;
    } else if (possibleOutbound.size() == 0) {
      return inboundMessages.keySet();
    } else {
      return Collections.emptySet();
    }
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    return new FunctionFactor(relabeling.apply(domainVariable),
        relabeling.apply(rangeVariable), function, domainFactor);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    if (!getVars().containsAny(assignment.getVariableNums())) {
      return this;
    }

    VariableNumMap toEliminate = getVars().intersection(assignment.getVariableNums());
    Assignment subAssignment = assignment.intersection(getVars());
    Preconditions.checkArgument(toEliminate.size() == 1);
    if (toEliminate.containsAny(domainVariable)) {
      DiscreteObjectFactor newDomainFactor = DiscreteObjectFactor.pointDistribution(domainVariable, subAssignment);
      return product(newDomainFactor).marginalize(toEliminate);
    } else {
      TableFactor newRangeFactor = TableFactor.pointDistribution(toEliminate, subAssignment);
      Factor intermediate = product(newRangeFactor);
      return intermediate.marginalize(toEliminate);
    }
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    Preconditions.checkState(domainFactor != null);

    if (varNumsToEliminate.contains(domainVariable.getOnlyVariableNum())) {
      if (!varNumsToEliminate.contains(rangeVariable.getOnlyVariableNum())) {
        return marginalizeToRangeVariable(true);
      } else {
        return marginalizeAllVariables(true);
      }
    } else {
      if (varNumsToEliminate.contains(rangeVariable.getOnlyVariableNum())) {
        return domainFactor;
      } else {
        return this;
      }
    }
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    Preconditions.checkState(domainFactor != null);

    if (varNumsToEliminate.contains(domainVariable.getOnlyVariableNum())) {
      if (!varNumsToEliminate.contains(rangeVariable.getOnlyVariableNum())) {
        return marginalizeToRangeVariable(false);
      } else {
        return marginalizeAllVariables(false);
      }
    } else {
      if (varNumsToEliminate.contains(rangeVariable.getOnlyVariableNum())) {
        return domainFactor;
      } else {
        return this;
      }
    }
  }

  private Factor marginalizeToRangeVariable(boolean useSum) {
    TableFactorBuilder rangeFactor = new TableFactorBuilder(rangeVariable);
    for (Assignment domainAssignment : domainFactor.assignments()) {
      Object rangeValue = function.apply(domainAssignment.getOnlyValue());
      if (rangeValue != null) {
        if (useSum) {
          rangeFactor.incrementWeight(rangeVariable.outcomeArrayToAssignment(rangeValue),
              domainFactor.getUnnormalizedProbability(domainAssignment));
        } else {
          rangeFactor.maxWeight(rangeVariable.outcomeArrayToAssignment(rangeValue),
              domainFactor.getUnnormalizedProbability(domainAssignment));
        }
      }
    }
    return rangeFactor.build();
  }

  private Factor marginalizeAllVariables(boolean useSum) {
    double totalProbability = 0.0;
    for (Assignment domainAssignment : domainFactor.assignments()) {
      Object rangeValue = function.apply(domainAssignment.getOnlyValue());
      if (rangeValue != null) {
        if (useSum) {
          totalProbability += domainFactor.getUnnormalizedProbability(domainAssignment);
        } else {
          totalProbability = Math.max(totalProbability, domainFactor.getUnnormalizedProbability(domainAssignment));
        }
      }
    }
    return TableFactor.pointDistribution(VariableNumMap.emptyMap(), Assignment.EMPTY)
        .product(totalProbability);
  }

  @Override
  public Factor add(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor maximum(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor product(Factor other) {
    Preconditions.checkArgument(other.getVars().size() == 1);
    if (other.getVars().containsAll(domainVariable)) {
      if (this.domainFactor == null) {
        return new FunctionFactor(domainVariable, rangeVariable, function,
            (DiscreteObjectFactor) other);
      } else {
        return new FunctionFactor(domainVariable, rangeVariable, function,
            domainFactor.product(other));
      }
    } else {
      Preconditions.checkState(domainFactor != null);
      Map<Assignment, Double> probabilities = Maps.newHashMap();
      for (Assignment domainAssignment : domainFactor.assignments()) {
        Object rangeObject = function.apply(domainAssignment.getOnlyValue());

        double newProb = domainFactor.getUnnormalizedProbability(domainAssignment) *
            other.getUnnormalizedProbability(rangeObject);
        if (newProb != 0.0) {
          probabilities.put(domainAssignment, newProb);
        }
      }

      return new FunctionFactor(domainVariable, rangeVariable, function,
          new DiscreteObjectFactor(domainVariable, probabilities));
    }
  }

  @Override
  public Factor product(double constant) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor inverse() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double size() {
    return domainFactor != null ? domainFactor.size() : 0;
  }

  @Override
  public Assignment sample() {
    return mapDomainAssignmentToFactorAssignment(domainFactor.sample());
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    List<Assignment> domainAssignments = domainFactor.getMostLikelyAssignments(numAssignments);
    List<Assignment> mostLikely = Lists.newArrayListWithCapacity(domainAssignments.size());
    for (Assignment domainAssignment : domainAssignments) {
      mostLikely.add(mapDomainAssignmentToFactorAssignment(domainAssignment));
    }
    return mostLikely;
  }

  private Assignment mapDomainAssignmentToFactorAssignment(Assignment domainAssignment) {
    Object domainValue = domainAssignment.getValues().get(0);
    Object rangeValue = function.apply(domainValue);
    return domainAssignment.union(rangeVariable.outcomeArrayToAssignment(rangeValue));
  }
  
  @Override
  public String toString() {
    return "FunctionFactor(" + domainVariable.toString() + ", " + rangeVariable.toString() + "):"
        + ((domainFactor != null) ? domainFactor.toString() : "uniform");
  }
}
