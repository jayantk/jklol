package com.jayantkrish.jklol.models.bayesnet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.SparseOutcomeTable;

/**
 * A conditional probability table (for a BN). Stores a conditional probability
 * over a set of children conditioned on a set of parents. This CPT is stored
 * sparsely.
 * 
 * Also stores sufficient statistics for estimating a CPT.
 */
public class Cpt implements SufficientStatistics {

  // Child / parent variables define possible outcomes.
  private final VariableNumMap parents;
  private final VariableNumMap children;
  private final VariableNumMap allVars;

  // TODO: Maybe these should be dense? It's unclear...
  protected final SparseOutcomeTable<Double> childStatistics;
  protected final SparseOutcomeTable<Double> parentStatistics;

  public Cpt(VariableNumMap parents, VariableNumMap children) {
    this.parents = parents;
    this.children = children;
    allVars = parents.union(children);

    childStatistics = new SparseOutcomeTable<Double>(allVars.getVariableNums());
    parentStatistics = new SparseOutcomeTable<Double>(parents.getVariableNums());
  }

  @Override
  public void increment(SufficientStatistics other, double multiplier) {
    Cpt otherCpt = castToCpt(other);
    incrementOutcomeCounts(childStatistics, otherCpt.childStatistics, multiplier);
    incrementOutcomeCounts(parentStatistics, otherCpt.parentStatistics, multiplier);
  }

  @Override
  public void increment(double amount) {
    Iterator<Assignment> assignmentIter = new AllAssignmentIterator(allVars);
    while (assignmentIter.hasNext()) {
      incrementOutcomeCount(assignmentIter.next(), amount);
    }
  }

  /**
   * Gets the probability of assignment {@code a}.
   */
  public double getProbability(Assignment a) {
    Assignment subAssignment = a.subAssignment(parents.getVariableNums());
    if (!parentStatistics.containsKey(subAssignment)) {
      throw new ArithmeticException("Cannot get conditional probability for unobserved parents: "
          + subAssignment);
    }
    if (!childStatistics.containsKey(a)) {
      return 0.0;
    }
    return childStatistics.get(a) / parentStatistics.get(subAssignment);
  }
  
  /**
   * Add some number of occurrences to a particular outcome.
   */
  public void incrementOutcomeCount(Assignment a, double count) {
    double oldCount = 0.0;
    if (childStatistics.containsKey(a)) {
      oldCount = childStatistics.get(a);
    }
    childStatistics.put(a, count + oldCount);

    Assignment subAssignment = a.subAssignment(parents.getVariableNums());
    if (!parentStatistics.containsKey(subAssignment)) {
      parentStatistics.put(subAssignment, 0.0);
    }

    parentStatistics.put(subAssignment,
        parentStatistics.get(subAssignment) + count);
  }
  
  public VariableNumMap getParents() {
    return parents;
  }
  
  public VariableNumMap getChildren() {
    return children;
  }
  
  public VariableNumMap getVars() {
    return allVars;
  }

  /**
   * Gets an iterator over all assignments to the child variables of
   * {@code this} with non-zero probability.
   * 
   * @return
   */
  public Iterator<Assignment> assignmentIterator() {
    return childStatistics.assignmentIterator();
  }

  /**
   * Increments the values in {@code toIncrement} by {@code incrementAmount}.
   * This is loosely equivalent to performing
   * {@code toIncrement = toIncrement + incrementAmount * multiplier} for all
   * assignments in {@code toIncrement}.
   * 
   * @param toIncrement
   * @param incrementAmount
   * @param incrementMultiplier
   */
  private void incrementOutcomeCounts(SparseOutcomeTable<Double> toIncrement,
      SparseOutcomeTable<Double> incrementAmount, double incrementMultiplier) {
    Iterator<Assignment> iter = incrementAmount.assignmentIterator();
    while (iter.hasNext()) {
      Assignment a = iter.next();
      if (toIncrement.containsKey(a)) {
        toIncrement.put(a, toIncrement.get(a) + incrementAmount.get(a) * incrementMultiplier);
      } else {
        toIncrement.put(a, incrementAmount.get(a) * incrementMultiplier);
      }
    }
  }

  /**
   * Checks that {@code other} is compatible with {@code this} and casts
   * {@code other} to a {@code Cpt}.
   * 
   * @param other
   * @return
   */
  private Cpt castToCpt(SufficientStatistics other) {
    Preconditions.checkArgument(other instanceof Cpt);
    Cpt otherCpt = (Cpt) other;
    Preconditions.checkArgument(otherCpt.parents.equals(parents));
    Preconditions.checkArgument(otherCpt.children.equals(children));
    return otherCpt;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    Iterator<Assignment> iter = assignmentIterator();
    List<Object> parentValues = new ArrayList<Object>();
    List<Object> childValues = new ArrayList<Object>();
    while (iter.hasNext()) {
      Assignment a = iter.next();
      parentValues.clear();
      childValues.clear();
      for (int i = 0; i < parents.size(); i++) {
        parentValues.add(a.getVarValue(parents.getVariableNums().get(i)));
      }
      for (int i = 0; i < children.size(); i++) {
        childValues.add(a.getVarValue(children.getVariableNums().get(i)));
      }

      sb.append(parentValues);
      sb.append("-->");
      sb.append(childValues);
      sb.append(":");
      sb.append(getProbability(a));
      sb.append("\n");
    }
    return sb.toString();
  }
}