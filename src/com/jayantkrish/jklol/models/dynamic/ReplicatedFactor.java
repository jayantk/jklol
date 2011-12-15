package com.jayantkrish.jklol.models.dynamic;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;

/**
 * {@code ReplicatedFactor} uses a {@code VariablePattern} to identify
 * replicated sets of variables, then duplicates a given {@code Factor} across
 * each replication.
 * 
 * @author jayantk
 */
public class ReplicatedFactor implements PlateFactor {

  private final Factor factorToReplicate;
  private final VariablePattern variablePattern;

  public ReplicatedFactor(Factor factorToReplicate, VariablePattern variablePattern) {
    this.factorToReplicate = Preconditions.checkNotNull(factorToReplicate);
    this.variablePattern = Preconditions.checkNotNull(variablePattern);
  }

  /**
   * Gets a {@code ReplicatedFactor} which simply adds {@code factor} to a
   * {@code FactorGraph}. The returned {@code ReplicatedFactor} does not depend
   * on any {@code Plate}s.
   * 
   * @param factor
   * @return
   */
  public static ReplicatedFactor fromFactor(Factor factor) {
    return new ReplicatedFactor(factor, VariablePattern.fromVariableNumMap(factor.getVars()));
  }

  @Override
  public List<Factor> instantiateFactors(VariableNumMap factorGraphVariables) {
    List<Factor> instantiatedFactors = Lists.newArrayList();
    for (VariableMatch match : variablePattern.matchVariables(factorGraphVariables)) {
      instantiatedFactors.add(factorToReplicate.relabelVariables(match.getMappingToTemplate().inverse()));
    }
    return instantiatedFactors;
  }
}
