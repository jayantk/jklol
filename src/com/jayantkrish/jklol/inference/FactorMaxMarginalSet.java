package com.jayantkrish.jklol.inference;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.inference.JunctionTree.CliqueTree;
import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Max-marginals computed from a list of max-marginal {@code Factor}s.
 * 
 * @author jayant
 */
public class FactorMaxMarginalSet implements MaxMarginalSet {

  private final CliqueTree cliqueTree;

  private final Assignment conditionedValues;

  public FactorMaxMarginalSet(CliqueTree cliqueTree, Assignment conditionedValues) {
    this.cliqueTree = Preconditions.checkNotNull(cliqueTree);
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
    return getBestAssignment(Assignment.EMPTY, cliqueTree, 0);
  }

  @Override
  public Assignment getNthBestAssignment(int n, Assignment portion) {
    Assignment conditionalPortion = portion.intersection(conditionedValues.getVariableNumsArray());
    if (!conditionalPortion.equals(
        conditionedValues.intersection(conditionalPortion.getVariableNumsArray()))) {
      // If portion disagrees with values that are conditioned on,
      // then all assignments containing portion have zero probability.
      throw new ZeroProbabilityError();
    }

    // Check that computing such an assignment is possible given the factors.
    Assignment factorPortion = portion.removeAll(conditionedValues.getVariableNumsArray());
    List<Factor> factorGraphFactors = cliqueTree.getMarginals();
    for (int i = 0; i < factorGraphFactors.size(); i++) {
      Factor factor = factorGraphFactors.get(i);
      if (factor.getVars().containsAll(factorPortion.getVariableNumsArray())) {
        return getBestAssignment(portion, cliqueTree, i);
      }
    }

    throw new UnsupportedOperationException("factor graph does not contain a factor with all variables: "
        + portion.getVariableNums());
  }

  /**
   * Searches for the best assignment to {@code cliqueTree} consistent with
   * {@code portion}. This method merges together assignments from possibly
   * disjoint subgraphs of {@code cliqueTree}, using
   * {@link #getBestAssignment(Assignment, CliqueTree, int)} for each subgraph.
   * 
   * @param portion
   * @param cliqueTree
   * @param initialFactor
   * @return
   */
  private Assignment getBestAssignment(Assignment portion, CliqueTree cliqueTree, int initialFactor) {
    if (cliqueTree.getMarginals().size() == 0) {
      // Special case where the factor graph has no factors in it.
      return conditionedValues;
    } else {
      // General case
      SortedSet<Integer> unvisited = Sets.newTreeSet();
      for (int i = 0; i < cliqueTree.numFactors(); i++) {
        unvisited.add(i);
      }

      Set<Integer> visited = Sets.newHashSet();
      Assignment current = portion;
      int nextFactor = initialFactor;
      while (unvisited.size() > 0) {
        current = getBestAssignmentGiven(cliqueTree, nextFactor, visited, current);
        unvisited.removeAll(visited);
        if (unvisited.size() > 0) {
          nextFactor = unvisited.first();
        }
      }

      return current.union(conditionedValues);
    }
  }

  /**
   * Performs a depth-first search of {@code cliqueTree}, starting at
   * {@code factorNum}, to find an assignment with maximal probability. If
   * multiple maximal probability assignments exist, this method returns an
   * arbitrary one.
   * 
   * @param cliqueTree factor graph which is searched.
   * @param factorNum current factor to visit.
   * @param visitedFactors list of factors already visited by the depth-first
   * search.
   * @param a the maximal probability assignment for the already visited
   * factors.
   * @return
   */
  private static Assignment getBestAssignmentGiven(CliqueTree cliqueTree, int factorNum,
      Set<Integer> visitedFactors, Assignment a) {
    Factor curFactor = cliqueTree.getMarginal(factorNum);
    List<Assignment> bestAssignments = curFactor.conditional(a).getMostLikelyAssignments(1);
    if (bestAssignments.size() == 0) {
      // This condition implies that the factor graph does not have a positive
      // probability assignment.
      throw new ZeroProbabilityError();
    }

    Assignment best = bestAssignments.get(0).union(a);
    if (curFactor.getUnnormalizedLogProbability(best) == Double.NEGATIVE_INFINITY) {
      throw new ZeroProbabilityError();
    }

    visitedFactors.add(factorNum);

    for (int adjacentFactorNum : cliqueTree.getNeighboringFactors(factorNum)) {
      if (!visitedFactors.contains(adjacentFactorNum)) {
        Assignment bestChild = getBestAssignmentGiven(cliqueTree, adjacentFactorNum,
            visitedFactors, best).removeAll(best.getVariableNumsArray());
        best = best.union(bestChild);
      }
    }
    return best;
  }

  @Override
  public Factor getMaxMarginal(VariableNumMap variables) {
    for (Factor factor : cliqueTree.getMarginals()) {
      // Find a factor which contains all of the variables.
      if (factor.getVars().containsAll(variables)) {
        return factor.maxMarginalize(factor.getVars().removeAll(variables));
      }
    }

    if (variables.size() == 0 && cliqueTree.getMarginals().size() == 0) {
      return TableFactor.unity(VariableNumMap.EMPTY);
    }

    // No factor was found.
    throw new IllegalArgumentException(
        "Graph does not contain a factor with all variables: " + variables);
  }
}
