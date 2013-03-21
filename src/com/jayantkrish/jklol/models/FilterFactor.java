package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

public class FilterFactor extends AbstractFactor {

  private static final long serialVersionUID = -6253160804905309329L;
  
  private final VariableNumMap domainVar;
  private final VariableNumMap auxiliaryVars;
  private final Factor relationFactor;
  private final Factor rangeFactor;
  private boolean isMaxMarginal;
  
  private boolean isInverse;

  public FilterFactor(VariableNumMap domainVar, VariableNumMap auxiliaryVars, 
      Factor relationFactor, Factor rangeFactor, boolean isMaxMarginal, boolean isInverse) {
    super(domainVar.union(auxiliaryVars));
    Preconditions.checkArgument(domainVar.size() == 1);
    this.domainVar = domainVar;
    this.auxiliaryVars = auxiliaryVars;
    this.relationFactor = relationFactor;
    this.rangeFactor = rangeFactor;
    this.isMaxMarginal = isMaxMarginal;
    this.isInverse = false;
  }
  
  public Factor getRangeFactor() {
    return rangeFactor;
  }
  
  public Factor getRelationFactor() {
    return relationFactor;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Factor rangeDistribution = relationFactor.conditional(assignment.intersection(getVars()));
    if (rangeFactor != null) {
      rangeDistribution = rangeDistribution.product(rangeFactor);
    }
    if (isInverse) {
      rangeDistribution = rangeDistribution.inverse();
    }
    if (isMaxMarginal) {
      return rangeDistribution.maxMarginalize(rangeDistribution.getVars())
          .getUnnormalizedProbability(Assignment.EMPTY);
    } else {
      return rangeDistribution.marginalize(rangeDistribution.getVars())
          .getUnnormalizedProbability(Assignment.EMPTY);
    }
  }

  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    return Math.log(getUnnormalizedProbability(assignment));
  }

  @Override
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    return Collections.emptySet();
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    VariableRelabeling extendedRelabeling = relabeling;
    if (!relabeling.isInDomain(rangeFactor.getVars())) {
      extendedRelabeling = relabeling.union(VariableRelabeling.identity(rangeFactor.getVars()));
    }
    
    return new FilterFactor(extendedRelabeling.apply(domainVar), extendedRelabeling.apply(auxiliaryVars), 
        relationFactor.relabelVariables(extendedRelabeling), rangeFactor.relabelVariables(extendedRelabeling), 
        isMaxMarginal, false);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    // Not actually supported.
    Preconditions.checkArgument(!getVars().containsAny(assignment.getVariableNums()));
    return this;
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException();
 }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException();
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
    Preconditions.checkArgument(other.getVars().equals(domainVar));
    Factor productFactor = relationFactor.product(other);
    if (rangeFactor != null) {
      productFactor = productFactor.product(rangeFactor);
    }
    if (isInverse) {
      productFactor = productFactor.inverse();
    }
    if (isMaxMarginal) {
      return productFactor.maxMarginalize(rangeFactor.getVars());
    } else {
      return productFactor.marginalize(rangeFactor.getVars());
    }
  }

  @Override
  public Factor product(double constant) {
    throw new UnsupportedOperationException();
  }
      
  @Override
  public Factor outerProduct(Factor other) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Factor inverse() {
    return new FilterFactor(domainVar, auxiliaryVars, relationFactor, 
        rangeFactor, isMaxMarginal, !isInverse);
  }

  @Override
  public double size() {
    return Double.POSITIVE_INFINITY;
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    throw new UnsupportedOperationException();
  }
}
