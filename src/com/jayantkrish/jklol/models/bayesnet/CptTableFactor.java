package com.jayantkrish.jklol.models.bayesnet;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A CptTableFactor is the typical factor you expect in a Bayesian Network.
 * Its unnormalized probabilities are simply child variable probabilities
 * conditioned on a parent.
 */
public class CptTableFactor extends AbstractParametricFactor<SufficientStatistics> {

  // These are the parent and child variables in the CPT.
  private final VariableNumMap parentVars;
  private final VariableNumMap childVars;

  /**
   * childrenNums are the variable numbers of the "child" nodes. The CptFactor
   * defines a probability distribution P(children | parents) over *sets* of
   * child variables. (In the Bayes Net DAG, there is an edge from every parent
   * to every child, and internally the children are a directed clique.)
   */
  public CptTableFactor(VariableNumMap parentVars, VariableNumMap childVars) {
    super(parentVars.union(childVars));
    this.parentVars = parentVars;
    this.childVars = childVars;
  }
  
  // ////////////////////////////////////////////////////////////////
  // ParametricFactor / CptFactor methods
  // ///////////////////////////////////////////////////////////////
  
  @Override
  public DiscreteFactor getFactorFromParameters(SufficientStatistics parameters) {
    Cpt cpt = parameters.coerceToCpt();
    Preconditions.checkArgument(cpt.getVars().equals(getVars()));

    return cpt.convertToFactor();
  }

  @Override
  public Cpt getNewSufficientStatistics() {
    return new Cpt(parentVars, childVars);
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      Assignment a, double count) {
    Preconditions.checkArgument(a.containsAll(getVars().getVariableNums()));
    Cpt cptStatistics = statistics.coerceToCpt();
    cptStatistics.incrementOutcomeCount(a, count);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics, 
      Factor marginal, Assignment conditionalAssignment, double count, double partitionFunction) {
    Assignment conditionalSubAssignment = conditionalAssignment.intersection(getVars());
    
    Cpt cptStatistics = statistics.coerceToCpt();
    Iterator<Assignment> assignmentIter = marginal.coerceToDiscrete().outcomeIterator();
    while (assignmentIter.hasNext()) {
      Assignment a = assignmentIter.next().union(conditionalSubAssignment);
      cptStatistics.incrementOutcomeCount(a, 
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