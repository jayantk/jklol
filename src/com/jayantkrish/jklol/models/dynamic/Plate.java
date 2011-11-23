package com.jayantkrish.jklol.models.dynamic;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
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
    Preconditions.checkArgument(replicationCountVariable.getIntegerVariables().size() == 1);

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
    return instantiateVariables((Integer) assignment.getValue(replicationCountVariableNum));
  }
}
