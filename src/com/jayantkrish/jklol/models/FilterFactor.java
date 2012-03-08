package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

public class FilterFactor extends AbstractFactor {
  
  private final Factor relationFactor;
  private final Factor rangeFactor;
  private boolean isMaxMarginal;

  public FilterFactor(VariableNumMap vars, Factor relationFactor, Factor rangeFactor,
      boolean isMaxMarginal) {
    super(vars);
    Preconditions.checkArgument(vars.size() == 1);
    this.relationFactor = relationFactor;
    this.rangeFactor = Preconditions.checkNotNull(rangeFactor);
    this.isMaxMarginal = isMaxMarginal;
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    Factor rangeDistribution = relationFactor.conditional(assignment).product(rangeFactor);
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
    return new FilterFactor(relabeling.apply(getVars()), relationFactor.relabelVariables(relabeling), 
        rangeFactor, isMaxMarginal);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    throw new UnsupportedOperationException();
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
    Preconditions.checkArgument(other.getVars().equals(getVars()));
    Factor productFactor = relationFactor.product(other).product(rangeFactor);
    if (isMaxMarginal) {
      return productFactor.maxMarginalize(rangeFactor.getVars());
    } else {
      return productFactor.marginalize(rangeFactor.getVars());
    }
  }

  @Override
  public Factor product(double constant) {
    return new FilterFactor(getVars(), relationFactor, rangeFactor.product(constant), 
        isMaxMarginal);
  }

  @Override
  public Factor inverse() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double size() {
    return rangeFactor.size();
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FactorProto toProto() {
    throw new UnsupportedOperationException();
  }
}
