package com.jayantkrish.jklol.models.bayesnet;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.MappingAssignmentIterator;

/**
 * A CptTableFactor is the typical factor you'd expect in a Bayesian Network.
 * Its unnormalized probabilities are simply child variable probabilities
 * conditioned on a parent.
 */
public class CptTableFactor extends DiscreteFactor implements CptFactor {

  private VariableNumMap parentVars;
  private VariableNumMap childVars;

  private Cpt cpt;
  private BiMap<Integer, Integer> cptVarNumMap;

  /**
   * childrenNums are the variable numbers of the "child" nodes. The CptFactor
   * defines a probability distribution P(children | parents) over *sets* of
   * child variables. (In the Bayes Net DAG, there is an edge from every parent
   * to every child, and internally the children are a directed clique.)
   *
   * {@code cptVarNumMap} is a mapping from the variables of {@code this} factor to
   * the variables of the underlying cpt. This will be used in the future to allow
   * factors to share CPTs.
   */
  public CptTableFactor(VariableNumMap parentVars, VariableNumMap childVars,
      BiMap<Integer, Integer> cptVarNumMap) {
    super(parentVars.union(childVars));

    this.parentVars = parentVars;
    this.childVars = childVars;

    this.cptVarNumMap = cptVarNumMap;
    cpt = getNewSufficientStatistics();
  }

  // ///////////////////////////////////////////////////////////
  // Required methods for DiscreteFactor
  // ///////////////////////////////////////////////////////////

  @Override
  public Iterator<Assignment> outcomeIterator() {
    // Need to reverse the mapping from the variables of this to the CPT.
    return new MappingAssignmentIterator(cpt.assignmentIterator(), cptVarNumMap.inverse());
  }

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    return cpt.getProbability(assignment.mappedAssignment(cptVarNumMap));
  }
  
  @Override
  public double size() {
    return cpt.size();
  }
  
  @Override
  public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Object> varValues) {
    Set<Assignment> assignments = Sets.newHashSet();
    Map<Integer, Integer> cptToFactorVars = cptVarNumMap.inverse(); 
    for (Assignment cptAssignment : cpt.getAssignmentsWithEntry(cptVarNumMap.get(varNum), varValues)) {
      assignments.add(cptAssignment.mappedAssignment(cptToFactorVars));
    }
    return assignments;
  }

  // ////////////////////////////////////////////////////////////////
  // CPT Factor methods
  // ///////////////////////////////////////////////////////////////

  @Override
  public Cpt getNewSufficientStatistics() {
    return new Cpt(parentVars.mapVariables(cptVarNumMap), childVars.mapVariables(cptVarNumMap));
  }

  @Override
  public Cpt getSufficientStatisticsFromAssignment(Assignment a, double count) {
    Preconditions.checkArgument(a.containsVars(getVars().getVariableNums()));
    Cpt newCpt = getNewSufficientStatistics();
    newCpt.incrementOutcomeCount(a.mappedAssignment(cptVarNumMap), count);
    return newCpt;
  }

  @Override
  public Cpt getSufficientStatisticsFromMarginal(Factor marginal, 
      double count, double partitionFunction) {
    Cpt newCpt = getNewSufficientStatistics();
    Iterator<Assignment> assignmentIter = marginal.coerceToDiscrete().outcomeIterator();
    while (assignmentIter.hasNext()) {
      Assignment a = assignmentIter.next();
      newCpt.incrementOutcomeCount(a.mappedAssignment(cptVarNumMap),
          count * marginal.getUnnormalizedProbability(a) / partitionFunction);
    }
    return newCpt;
  }

  @Override
  public Cpt getCurrentParameters() {
    return cpt;
  }

  @Override
  public void setCurrentParameters(SufficientStatistics statistics) {
    this.cpt = getNewSufficientStatistics();
    this.cpt.increment(statistics, 1.0);
  }

  // ///////////////////////////////////////////////////////////////////
  // CPTTableFactor methods
  // ///////////////////////////////////////////////////////////////////

  /**
   * Get an iterator over all possible assignments to the parent variables
   */
  public Iterator<Assignment> parentAssignmentIterator() {
    return new AllAssignmentIterator(parentVars);
  }

  /**
   * Get an iterator over all possible assignments to the child variables
   */
  public Iterator<Assignment> childAssignmentIterator() {
    return new AllAssignmentIterator(childVars);
  }

  public String toString() {
    return cpt.toString();
  }
}