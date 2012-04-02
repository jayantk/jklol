package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A factor representing a relationship between an {@code ObjectVariable} and
 * some other variables. As with {@link DiscreteObjectFactor}, this factor
 * should only be used when the values of this factor cannot be explicitly
 * enumerated. (If the values can be enumerated, then a {@link TableFactor} will
 * be more efficient.)
 * 
 * The weighted relationship between the variables is represented implicitly by
 * an abstract method. This method must be overriden to implement any given
 * relationship. (For an example, see {@link FunctionFactor}.)
 * 
 * @author jayant
 */
public abstract class WeightedRelationFactor extends AbstractFactor {

  private final VariableNumMap domainVariable;
  private final VariableNumMap rangeVariable;
  private final VariableNumMap auxiliaryVariables;

  private final Factor domainFactor;
  private final Factor rangeFactor;

  /**
   * {@code domainFactor} may be null, in which case this factor represents a
   * uniform probability distribution over the domain variable's values.
   * 
   * @param domainVariable
   * @param rangeVariable
   * @param function
   * @param domainFactor
   * @param rangeVariableFactory determines the type of factor constructed over
   * the range variable.
   */
  public WeightedRelationFactor(VariableNumMap domainVariable, VariableNumMap rangeVariable,
      VariableNumMap auxiliaryVariables, Factor domainFactor, Factor rangeFactor) {
    super(VariableNumMap.unionAll(domainVariable, rangeVariable, auxiliaryVariables));
    this.domainVariable = Preconditions.checkNotNull(domainVariable);
    this.rangeVariable = Preconditions.checkNotNull(rangeVariable);
    this.auxiliaryVariables = Preconditions.checkNotNull(auxiliaryVariables);

    this.domainFactor = domainFactor;
    this.rangeFactor = rangeFactor;
    Preconditions.checkArgument(domainFactor == null || domainFactor.getVars().equals(domainVariable));
    Preconditions.checkArgument(rangeFactor == null || rangeFactor.getVars().equals(rangeVariable));
  }

  /**
   * Gets the unnormalized probability of {@code assignment}, ignoring any
   * factors which may have been multiplied into {@code this}.
   * 
   * @param assignment
   * @return
   */
  protected abstract double getAssignmentRelationProbability(Assignment assignment);

  /**
   * Constructs a joint distribution over all of the variables in this factor.
   * {@code domainFactor} is guaranteed to be non-null. If {@code rangeFactor}
   * is null, it should be treated as if it were the uniform distribution over
   * possible outcomes (i.e., as if it assigned weight 1.0 to all outcomes).
   * 
   * @param domainFactor
   * @param rangeFactor
   * @return
   */
  protected abstract DiscreteObjectFactor constructJointDistribution(Factor domainFactor,
      Factor rangeFactor);

  
  protected abstract Factor replaceFactors(VariableRelabeling variableRelabeling,
      Factor newDomainFactor, Factor newRangeFactor);

  public VariableNumMap getDomainVariable() {
    return domainVariable;
  }

  public VariableNumMap getRangeVariable() {
    return rangeVariable;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    double prob = 1.0;
    if (domainFactor != null) {
      prob *= domainFactor.getUnnormalizedProbability(assignment);
    }
    if (rangeFactor != null) {
      prob *= rangeFactor.getUnnormalizedProbability(assignment);
    }
    if (prob > 0) {
      prob *= getAssignmentRelationProbability(assignment);
    }
    return prob;
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    return Math.log(getUnnormalizedProbability(assignment));
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
    Factor newDomainFactor = null;
    Factor newRangeFactor = null;
    if (domainFactor != null) {
      newDomainFactor = domainFactor.relabelVariables(relabeling);
    }
    if (rangeFactor != null) {
      newRangeFactor = rangeFactor.relabelVariables(relabeling);
    }

    return replaceFactors(relabeling, newDomainFactor, newRangeFactor);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    if (!getVars().containsAny(assignment.getVariableNums())) {
      return this;
    }

    Factor toReturn = this;
    VariableNumMap toEliminate = getVars().intersection(assignment.getVariableNums());
    if (toEliminate.containsAny(domainVariable)) {
      toReturn = toReturn.product(DiscreteObjectFactor.pointDistribution(domainVariable,
          assignment.intersection(domainVariable)));
    } else {
      toReturn = toReturn.product(DiscreteObjectFactor.pointDistribution(rangeVariable,
          assignment.intersection(rangeVariable)));
    }
    return toReturn.marginalize(toEliminate);
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    if (domainFactor == null) {
      // If domainFactor is null, some marginals cannot be computed.
      // Other marginals can only be computed lazily.
      Preconditions.checkArgument(!varNumsToEliminate.contains(domainVariable.getOnlyVariableNum()));

      if (getVars().intersection(varNumsToEliminate).size() == 0) {
        return this;
      } else if (varNumsToEliminate.contains(rangeVariable.getOnlyVariableNum())) {
        return new FilterFactor(domainVariable, auxiliaryVariables, this, rangeFactor, false, false); 
      } else {
        throw new UnsupportedOperationException();
      }
    } else {
      // Easy case: we've received a message in the domain, so we can construct
      // the joint distribution and everything.
      DiscreteObjectFactor thisAsDiscrete = constructJointDistribution(domainFactor, rangeFactor);
      return thisAsDiscrete.marginalize(varNumsToEliminate);
    }
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    if (domainFactor == null) {
      // If domainFactor is null, some marginals cannot be computed.
      // Other marginals can only be computed lazily.
      Preconditions.checkArgument(!varNumsToEliminate.contains(domainVariable.getOnlyVariableNum()));

      if (getVars().intersection(varNumsToEliminate).size() == 0) {
        return this;
      } else if (varNumsToEliminate.contains(rangeVariable.getOnlyVariableNum())) {
        return new FilterFactor(domainVariable, auxiliaryVariables, this, rangeFactor, true, false);
      } else {
        // We shouldn't get to this case, based on the Preconditions check above.
        throw new UnsupportedOperationException();
      }
    } else {
      // Easy case: we've received a message in the domain, so we can construct
      // the joint distribution and everything
      DiscreteObjectFactor thisAsDiscrete = constructJointDistribution(domainFactor, rangeFactor);
      return thisAsDiscrete.maxMarginalize(varNumsToEliminate);
    }
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
        return replaceFactors(VariableRelabeling.identity(getVars()), other, rangeFactor);
      } else {
        return replaceFactors(VariableRelabeling.identity(getVars()),
            domainFactor.product(other), rangeFactor);
      }
    } else {
      if (this.rangeFactor == null) {
        Factor toReturn = replaceFactors(VariableRelabeling.identity(getVars()), domainFactor, other);
        return toReturn;
      } else {
        return replaceFactors(VariableRelabeling.identity(getVars()),
            domainFactor, rangeFactor.product(other));
      }
    }
  }
  
  // TODO: override product with a list.

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
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    Preconditions.checkState(domainFactor != null);
    return constructJointDistribution(domainFactor, rangeFactor)
        .getMostLikelyAssignments(numAssignments);
  }

  @Override
  public FactorProto toProto() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public String toString() {
    return "WeightedRelationFactor(" + domainVariable.toString() + ", " + rangeVariable.toString() + "):"
        + ((domainFactor != null) ? domainFactor.toString() : "uniform") + ", "
        + ((rangeFactor != null) ? rangeFactor.toString() : "uniform");
  }
}
