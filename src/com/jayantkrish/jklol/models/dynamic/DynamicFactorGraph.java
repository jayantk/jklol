package com.jayantkrish.jklol.models.dynamic;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Represents a family of conditional {@link FactorGraph}s whose structure
 * depends on the input values. Examples of {@code DynamicFactorGraph}s include
 * sequence models, where the number of nodes in the graphical model depends on
 * the length of the input sequence.
 * 
 * The input to a {@code DynamicFactorGraph}s is a {@code DynamicAssignment},
 * which is a generalization of {@code Assignment}.
 * 
 * @author jayantk
 */
public class DynamicFactorGraph {

  private final DynamicVariableSet variables;

  // Plates represent graphical model structure which is replicated in a
  // data-dependent fashion.
  private final ImmutableList<PlateFactor> plateFactors;

  public DynamicFactorGraph(DynamicVariableSet variables, List<PlateFactor> plateFactors) {
    this.variables = variables;
    this.plateFactors = ImmutableList.copyOf(plateFactors);
  }

  public DynamicVariableSet getVariables() {
    return variables;
  }

  public static DynamicFactorGraph fromFactorGraph(FactorGraph factorGraph) {
    DynamicVariableSet variables = DynamicVariableSet.fromVariables(factorGraph.getVariables());

    List<PlateFactor> plateFactors = Lists.newArrayList();
    for (Factor factor : factorGraph.getFactors()) {
      plateFactors.add(ReplicatedFactor.fromFactor(factor));
    }
    return new DynamicFactorGraph(variables, plateFactors);
  }

  public FactorGraph conditional(DynamicAssignment assignment) {
    FactorGraph factorGraph = getFactorGraph(assignment);
    Assignment factorGraphAssignment = variables.toAssignment(assignment);
    return factorGraph.conditional(factorGraphAssignment);
  }

  public FactorGraph getFactorGraph(DynamicAssignment assignment) {
    VariableNumMap factorGraphVariables = variables.instantiateVariables(assignment);

    // Instantiate factors.
    List<Factor> factors = Lists.newArrayList();
    for (PlateFactor plateFactor : plateFactors) {
      factors.addAll(plateFactor.instantiateFactors(factorGraphVariables));
    }

    return new FactorGraph(factorGraphVariables, factors,
        VariableNumMap.emptyMap(), Assignment.EMPTY);
  }

  public DynamicFactorGraph addPlateFactors(List<PlateFactor> factors) {
    List<PlateFactor> allFactors = Lists.newArrayList(plateFactors);
    allFactors.addAll(factors);
    return new DynamicFactorGraph(getVariables(), allFactors);
  }
}
