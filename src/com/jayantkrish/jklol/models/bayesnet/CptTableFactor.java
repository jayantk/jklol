package com.jayantkrish.jklol.models.bayesnet;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
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

  private VariableNumMap parentVars;
  private VariableNumMap childVars;

  private BiMap<Integer, Integer> cptVarNumMap;

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
      BiMap<Integer, Integer> cptVarNumMap) {
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
    Preconditions.checkArgument(cpt.getVars().containsAll(cptVarNumMap.values()));
    Preconditions.checkArgument(cptVarNumMap.values().containsAll(cpt.getVars().getVariableNums()));

    return new TableFactor(getVars(), cpt.convertToFactor()
        .getWeights().relabelDimensions(cptVarNumMap.inverse()));
  }

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