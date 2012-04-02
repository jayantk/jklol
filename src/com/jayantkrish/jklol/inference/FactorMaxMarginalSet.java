package com.jayantkrish.jklol.inference;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Max-marginals computed from a list of max-marginal {@code Factor}s.
 * 
 * @author jayant
 */
public class FactorMaxMarginalSet implements MaxMarginalSet {

  private final FactorGraph factorGraph;

  private final Assignment conditionedValues;

  public FactorMaxMarginalSet(FactorGraph factorGraph, Assignment conditionedValues) {
    this.factorGraph = Preconditions.checkNotNull(factorGraph);
    this.conditionedValues = Preconditions.checkNotNull(conditionedValues);
  }

  @Override
  public int beamSize() {
    return 1;
  }

  @Override
  public Assignment getNthBestAssignment(int n) {
    // At the moment, only the best assignment is supported.
    Preconditions.checkArgument(n == 0);
    if (factorGraph.getFactors().size() == 0) {
      // Special case where the factor graph has no factors in it.
      return conditionedValues;
    } else {
      // General case
      SortedSet<Integer> unvisited = Sets.newTreeSet();
      for (int i = 0; i < factorGraph.getFactors().size(); i++) {
        unvisited.add(i);
      }
      
      Set<Integer> visited = Sets.newHashSet();      
      Assignment current = Assignment.EMPTY;
      while (unvisited.size() > 0) {
        current = getBestAssignmentGiven(factorGraph, unvisited.first(), visited, current);
        unvisited.removeAll(visited);
      }
      
      if (factorGraph.getUnnormalizedLogProbability(current) == Double.NEGATIVE_INFINITY) {
        throw new ZeroProbabilityError();
      }

      return current.union(conditionedValues);
    }
  }

  /**
   * Performs a depth-first search of {@code factorGraph}, starting at
   * {@code factorNum}, to find an assignment with maximal probability. If
   * multiple maximal probability assignments exist, this method returns an
   * arbitrary one.
   * 
   * @param factorGraph factor graph which is searched.
   * @param factorNum current factor to visit.
   * @param visitedFactors list of factors already visited by the depth-first
   * search.
   * @param a the maximal probability assignment for the already visited
   * factors.
   * @return
   */
  private static Assignment getBestAssignmentGiven(FactorGraph factorGraph, int factorNum,
      Set<Integer> visitedFactors, Assignment a) {
    Factor curFactor = factorGraph.getFactor(factorNum);
    List<Assignment> bestAssignments = curFactor.conditional(a).getMostLikelyAssignments(1);
    if (bestAssignments.size() == 0) {
      // This condition implies that the factor graph does not have a positive
      // probability assignment.
      throw new ZeroProbabilityError();
    }
    Assignment best = bestAssignments.get(0).union(a);
    visitedFactors.add(factorNum);

    for (int adjacentFactorNum : factorGraph.getAdjacentFactors(factorNum)) {
      if (!visitedFactors.contains(adjacentFactorNum)) {
        Assignment bestChild = getBestAssignmentGiven(factorGraph, adjacentFactorNum,
            visitedFactors, best).removeAll(best.getVariableNums());
        best = best.union(bestChild);
      }
    }
    return best;
  }
}
