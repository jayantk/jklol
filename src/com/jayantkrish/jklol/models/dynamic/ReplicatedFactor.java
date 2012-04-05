package com.jayantkrish.jklol.models.dynamic;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Factors;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraphProtos.PlateFactorProto;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraphProtos.PlateFactorProto.Type;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraphProtos.ReplicatedFactorProto;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * {@code ReplicatedFactor} uses a {@code VariableNamePattern} to identify
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
    return new ReplicatedFactor(factor, new WrapperVariablePattern(factor.getVars()));
  }

  @Override
  public List<Factor> instantiateFactors(VariableNumMap factorGraphVariables) {
    List<Factor> instantiatedFactors = Lists.newArrayList();
    for (VariableMatch match : variablePattern.matchVariables(factorGraphVariables)) {
      instantiatedFactors.add(factorToReplicate.relabelVariables(match.getMappingToTemplate().inverse()));
    }
    return instantiatedFactors;
  }
  
  @Override
  public PlateFactorProto toProto(IndexedList<Variable> variableTypeIndex) {
    PlateFactorProto.Builder builder = PlateFactorProto.newBuilder();
    builder.setType(PlateFactorProto.Type.REPLICATED);
    
    builder.getReplicatedFactorBuilder().setFactor(factorToReplicate.toProto(variableTypeIndex))
      .setPattern(variablePattern.toProto(variableTypeIndex));
    
    return builder.build();
  }
  
  public static ReplicatedFactor fromProto(PlateFactorProto proto,
      IndexedList<Variable> variableTypeIndex) {
    Preconditions.checkArgument(proto.getType().equals(Type.REPLICATED));
    ReplicatedFactorProto replicatedProto = proto.getReplicatedFactor();
    Preconditions.checkArgument(replicatedProto.hasFactor());
    Preconditions.checkArgument(replicatedProto.hasPattern());
    return new ReplicatedFactor(Factors.fromProto(replicatedProto.getFactor(), variableTypeIndex),
        VariablePatterns.fromProto(replicatedProto.getPattern(), variableTypeIndex));
  }
}
