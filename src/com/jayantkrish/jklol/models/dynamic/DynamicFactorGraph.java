package com.jayantkrish.jklol.models.dynamic;

import java.io.Serializable;
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
public class DynamicFactorGraph implements Serializable {

  private static final long serialVersionUID = 1L;

  private final DynamicVariableSet variables;

  // Plates represent graphical model structure which is replicated in a
  // data-dependent fashion.
  private final ImmutableList<PlateFactor> plateFactors;
  private final ImmutableList<String> factorNames;

  public DynamicFactorGraph(DynamicVariableSet variables, List<PlateFactor> plateFactors,
      List<String> factorNames) {
    this.variables = variables;
    this.plateFactors = ImmutableList.copyOf(plateFactors);
    this.factorNames = ImmutableList.copyOf(factorNames);
  }

  public DynamicVariableSet getVariables() {
    return variables;
  }
  
  public List<PlateFactor> getPlateFactors() {
    return plateFactors;
  }

  public List<String> getFactorNames() {
    return factorNames;
  }

  public static DynamicFactorGraph fromFactorGraph(FactorGraph factorGraph) {
    DynamicVariableSet variables = DynamicVariableSet.fromVariables(factorGraph.getVariables());

    List<PlateFactor> plateFactors = Lists.newArrayList();
    for (Factor factor : factorGraph.getFactors()) {
      plateFactors.add(ReplicatedFactor.fromFactor(factor));
    }
    return new DynamicFactorGraph(variables, plateFactors, factorGraph.getFactorNames());
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
    List<String> instantiatedNames = Lists.newArrayList();
    for (int i = 0; i < plateFactors.size(); i++) {
      PlateFactor plateFactor = plateFactors.get(i);
      List<Factor> replications = plateFactor.instantiateFactors(factorGraphVariables);
      factors.addAll(replications);
      
      for (int j = 0; j < replications.size(); j++) {
        instantiatedNames.add(factorNames.get(i) + "-" + j);
      }
    }

    return new FactorGraph(factorGraphVariables, factors, instantiatedNames,
        VariableNumMap.EMPTY, Assignment.EMPTY);
  }

  public DynamicFactorGraph addPlateFactors(List<PlateFactor> factors, List<String> newFactorNames) {
    List<PlateFactor> allFactors = Lists.newArrayList(plateFactors);
    allFactors.addAll(factors);
    List<String> allNames = Lists.newArrayList(factorNames);
    allNames.addAll(newFactorNames);
    return new DynamicFactorGraph(getVariables(), allFactors, allNames);
  }

  /**
   * Gets the factor in this factor graph with the given {@code name}.
   * Returns {@code null} if no factor is called {@code name}.
   *  
   * @param name
   * @return
   */
  public PlateFactor getFactorByName(String name) {
    int index = factorNames.indexOf(name);
    if (index == -1) {
      return null;
    } else {
      return plateFactors.get(index);
    }
  }
}