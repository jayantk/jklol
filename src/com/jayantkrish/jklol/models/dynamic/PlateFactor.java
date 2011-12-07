package com.jayantkrish.jklol.models.dynamic;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.util.Assignment;

/**
 * {@code PlateFactor} is a generalization of a {@code Factor} that represents a
 * factor replicated across multiple instantiations of a {@link Plate}. This
 * interface behaves like a factory for {@code Factor}s, using the set of
 * variables in a {@code FactorGraph} as inputVar.
 * 
 * Note that this class does not support inference, as it represents a set of
 * factors. Inference can only be performed over factors instantiated by
 * {@code this}.
 * 
 * @author jayantk
 */
public class PlateFactor {

  private final Factor factorToReplicate;
  private final VariablePattern variablePattern;

  // Plates which replicate variables matched by variablePattern. These plates
  // must be instantiated (i.e., replicated) before the replications of this
  // factor can be created.
  private final ImmutableList<Plate> dependentPlates;

  public PlateFactor(Factor factorToReplicate, VariablePattern variablePattern,
      List<Plate> dependentPlates) {
    this.factorToReplicate = Preconditions.checkNotNull(factorToReplicate);
    this.variablePattern = Preconditions.checkNotNull(variablePattern);
    this.dependentPlates = ImmutableList.copyOf(Preconditions.checkNotNull(dependentPlates));
  }

  /**
   * Gets a {@code PlateFactor} which simply adds {@code factor} to a
   * {@code FactorGraph}. The returned {@code PlateFactor} does not depend on
   * any {@code Plate}s.
   * 
   * @param factor
   * @return
   */
  public static PlateFactor fromFactor(Factor factor) {
    return new PlateFactor(factor, VariablePattern.fromVariableNumMap(factor.getVars()),
        Collections.<Plate>emptyList());
  }

  /**
   * Returns {@code true} if {@code assignment} contains values for all of the
   * variables necessary to instantiate the replications of this factor.
   * 
   * @param assignment
   * @return
   */
  public boolean canInstantiate(Assignment assignment) {
    for (Plate plate : dependentPlates) {
      if (!assignment.containsAll(plate.getReplicationVariables().getVariableNums())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns a list of {@code Factor}s created by replicating this
   * {@code PlateFactor} for each set of matching variables in
   * {@code factorGraphVariables}.
   * 
   * @param factorGraphVariables
   * @return
   */
  public List<Factor> instantiateFactors(VariableNumMap factorGraphVariables) {
    List<Factor> instantiatedFactors = Lists.newArrayList();
    for (VariableMatch match : variablePattern.matchVariables(factorGraphVariables)) {
      instantiatedFactors.add(factorToReplicate.relabelVariables(match.getMappingToTemplate().inverse()));
    }
    return instantiatedFactors;
  }
}
