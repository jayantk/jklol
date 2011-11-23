package com.jayantkrish.jklol.models.bayesnet;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CptTableFactor is the typical factor you'd expect in a Bayesian Network.
 * Its unnormalized probabilities are simply child variable probabilities
 * conditioned on a parent.
 */
public class CptTableFactor extends AbstractParametricFactor<SufficientStatistics> {

  private final VariableNumMap parentVars;
  private final VariableNumMap childVars;

  private final VariableRelabeling cptVarNumMap;

  /**
   * childrenNums are the variable numbers of the "child" nodes. The CptFactor
   * defines a probability distribution P(children | parents) over *sets* of
   * child variables. (In the Bayes Net DAG, there is an edge from every parent
   * to every child, and internally the children are a directed clique.)
   * 
   * {@code cptVarNumMap} is a mapping from the variables of {@code this} factor
   * to the variables of the underlying cpt. This will be used in the future to
   * allow factors to share CPTs.
   */
  public CptTableFactor(VariableNumMap parentVars, VariableNumMap childVars,
      VariableRelabeling cptVarNumMap) {
    super(parentVars.union(childVars));
    this.parentVars = parentVars;
    this.childVars = childVars;
    this.cptVarNumMap = cptVarNumMap;
  }
  
  // ////////////////////////////////////////////////////////////////
  // ParametricFactor / CptFactor methods
  // ///////////////////////////////////////////////////////////////
  
  @Override
  public TableFactor getFactorFromParameters(SufficientStatistics parameters) {
    Cpt cpt = parameters.coerceToCpt();
    Preconditions.checkArgument(cptVarNumMap.isInRange(cpt.getVars()));

    return new TableFactor(getVars(), cpt.convertToFactor()
        .getWeights().relabelDimensions(cptVarNumMap.getVariableIndexReplacementMap().inverse()));
  }

  @Override
  public Cpt getNewSufficientStatistics() {
    return new Cpt(cptVarNumMap.apply(parentVars), cptVarNumMap.apply(childVars));
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      Assignment a, double count) {
    Preconditions.checkArgument(a.containsAll(getVars().getVariableNums()));
    Cpt cptStatistics = statistics.coerceToCpt();
    cptStatistics.incrementOutcomeCount(
        a.mapVariables(cptVarNumMap.getVariableIndexReplacementMap()), count);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, 
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    Assignment conditionalSubAssignment = conditionalAssignment.intersection(getVars());
    
    Cpt cptStatistics = statistics.coerceToCpt();
    Iterator<Assignment> assignmentIter = marginal.coerceToDiscrete().outcomeIterator();
    while (assignmentIter.hasNext()) {
      Assignment a = assignmentIter.next().union(conditionalSubAssignment);
      cptStatistics.incrementOutcomeCount(a.mapVariables(cptVarNumMap.getVariableIndexReplacementMap()),
          count * marginal.getUnnormalizedProbability(a) / partitionFunction);
    }
  }

  // ///////////////////////////////////////////////////////////////////
  // CPTTableFactor methods
  // ///////////////////////////////////////////////////////////////////

  /**
   * Gets the parent variables of this factor.
   * @return
   */
  public VariableNumMap getParents() {
    return parentVars;
  }
  
  /**
   * Gets the child variables of this factor.
   * @return
   */
  public VariableNumMap getChildren() {
    return childVars;
  }
  
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

  @Override
  public String toString() {
    return "[CptTableFactor Parents: " + parentVars.toString() 
        + " Children: " + childVars.toString() + "]";
  }
}