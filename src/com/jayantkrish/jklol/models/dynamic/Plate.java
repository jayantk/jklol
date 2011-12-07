package com.jayantkrish.jklol.models.dynamic;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A {@code Plate} represents a replicated set of variables in a
 * {@code FactorGraph}. {@code Plate}s are replicated in a data-dependent
 * fashion, using a special integer-valued replication count variable.
 * 
 * @author jayantk
 */
public class Plate {

  private VariableNumMap replicationCountVariable;
  private VariablePattern replicatedVariables;

  public Plate(VariableNumMap replicationCountVariable, VariablePattern replicatedVariables) {
    Preconditions.checkArgument(replicationCountVariable.size() == 1);
    Preconditions.checkArgument(replicationCountVariable.getObjectVariables().size() == 1);

    this.replicationCountVariable = replicationCountVariable;
    this.replicatedVariables = replicatedVariables;
  }

  /**
   * Gets the set of variables which must be assigned a value before
   * {@code this} plate can be instantiated. The values of the returned
   * variables determine how many times this plate gets instantiated.
   * 
   * @return
   */
  public VariableNumMap getReplicationVariables() {
    return replicationCountVariable;
  }
  
  /**
   * Gets the variable pattern which this plate instantiates.
   * 
   * @return
   */
  public VariablePattern getPattern() {
    return replicatedVariables;
  }

  /**
   * Instantiates this plate {@code numReplications} times.
   * 
   * @param numReplications
   * @return
   */
  public Map<String, Variable> instantiateVariables(int numReplications) {
    Map<String, Variable> instantiatedVariables = Maps.newHashMap();
    for (int i = 0; i < numReplications; i++) {
      instantiatedVariables.putAll(
          replicatedVariables.instantiateWithArgument(i));
    }
    return instantiatedVariables;
  }

  /**
   * Instantiates this plate. The number of replications depends on the values
   * that {@code assignment} assigns to {@code this.getReplicationVariables()}.
   * This method returns a name->(variable type) map containing the variables
   * which are created by the instantiation.
   * 
   * {@code assignment} must contain a value for each variable in
   * {@code this.getReplicationVariables()}.
   * 
   * @param numReplications
   * @return
   */
  public Map<String, Variable> instantiateVariables(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(
        replicationCountVariable.getVariableNums()));
    int replicationCountVariableNum = Iterables
        .getOnlyElement(replicationCountVariable.getVariableNums());

    // The value should be a list of assignments.
    List<?> value = (List<?>) assignment.getValue(replicationCountVariableNum);
    return instantiateVariables(value.size());
  }

  /**
   * Gets an assignment containing values for variables which are instantiated
   * by this plate. These values are to be conditioned on by the instantiating
   * factor graph.
   * 
   * @param assignment
   * @return
   */
  public Assignment instantiateVariableAssignments(VariableNumMap createdVariables,
      Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(
        replicationCountVariable.getVariableNums()));
    int replicationCountVariableNum = Iterables
        .getOnlyElement(replicationCountVariable.getVariableNums());

    List<VariableMatch> matchingVariables = replicatedVariables.matchVariables(createdVariables);
    // The value should be a list of assignments.
    List<?> assignments = (List<?>) assignment.getValue(replicationCountVariableNum);
    Preconditions.checkArgument(matchingVariables.size() == assignments.size());
    Assignment combinedAssignment = Assignment.EMPTY;
    for (VariableMatch match : matchingVariables) {
      // Each element of assignments is an assignment to the variables in replicatedVariables.
      // Construct a mapping from replicatedVariables to each instantiation of the template,
      // then map the assignment to the template to its instantiation.
      Assignment templateAssignment = (Assignment) assignments.get(match.getReplicationIndex());
      Assignment mappedAssignment = templateAssignment.mapVariables(
          match.getMappingToTemplate().inverse().getVariableIndexReplacementMap());
      combinedAssignment = combinedAssignment.union(mappedAssignment);
    }
    return combinedAssignment;
  }
}
