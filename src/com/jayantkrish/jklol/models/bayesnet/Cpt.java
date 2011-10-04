package com.jayantkrish.jklol.models.bayesnet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.CoercionError;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.FeatureSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

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

  protected final TableFactorBuilder childStatistics;
  protected final TableFactorBuilder parentStatistics;

  public Cpt(VariableNumMap parents, VariableNumMap children) {
    this.parents = parents;
    this.children = children;
    allVars = parents.union(children);

    childStatistics = new TableFactorBuilder(allVars);
    parentStatistics = new TableFactorBuilder(parents);
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
  
  @Override
  public void multiply(double amount) {
    // It's highly unlikely that anyone should call this operation on a Cpt.
    throw new UnsupportedOperationException("Not supported");
  }
  
  @Override
  public double getL2Norm() {
    // It's highly unlikely that anyone should call this operation on a Cpt.
    throw new UnsupportedOperationException("Not supported");
  }
  
  
  @Override
  public Cpt coerceToCpt() {
    return this;
  }
  
  @Override
  public FeatureSufficientStatistics coerceToFeature() {
    // N.B. This is possible, but is not currently implemented.
    throw new CoercionError("Cannot coerce Cpt instance into a FeatureSufficientStatistics.");
  }
  
  @Override
  public ListSufficientStatistics coerceToList() {
    throw new CoercionError("Cannot coerce Cpt instance into a ListSufficientStatistics.");
  }

  /**
   * Gets a {@code DiscreteFactor} representing the conditional probabilities in
   * {@code this}. The weight of an assignment {@code a} in the returned factor
   * is equal to the conditional probability of the child variables given the
   * parents. The returned factor is suitable for performing inference with in a
   * {@code FactorGraph}.
   * 
   * @return
   */
  public DiscreteFactor convertToFactor() {
    return childStatistics.build().product(parentStatistics.build().inverse());
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
    return childStatistics.getWeight(a)
        / parentStatistics.getWeight(subAssignment);
  }

  /**
   * Adds {@code count} occurrences to outcome {@code a}.
   */
  public void incrementOutcomeCount(Assignment a, double count) {
    double oldCount = childStatistics.getWeight(a);
    childStatistics.setWeight(a, count + oldCount);

    Assignment subAssignment = a.subAssignment(parents.getVariableNums());
    parentStatistics.setWeight(subAssignment, parentStatistics.getWeight(subAssignment) + count);
  }

  /**
   * Gets an iterator over all assignments to the child variables of
   * {@code this} with non-zero probability.
   */
  public Iterator<Assignment> assignmentIterator() {
    return childStatistics.assignmentIterator();
  }

  /**
   * Gets the number of assignments with nonzero probability in {@code this}.
   */
  public double size() {
    return childStatistics.size();
  }

  /**
   * Gets the parent variables of this CPT.
   */
  public VariableNumMap getParents() {
    return parents;
  }

  /**
   * Gets the children variables of this CPT.
   */
  public VariableNumMap getChildren() {
    return children;
  }

  /**
   * Gets all of the variables this CPT is defined over.
   */
  public VariableNumMap getVars() {
    return allVars;
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
  private void incrementOutcomeCounts(TableFactorBuilder toIncrement,
      TableFactorBuilder incrementAmount, double incrementMultiplier) {
    Iterator<Assignment> iter = incrementAmount.assignmentIterator();
    while (iter.hasNext()) {
      Assignment a = iter.next();
      if (toIncrement.containsKey(a)) {
        toIncrement.setWeight(a, toIncrement.getWeight(a) + incrementAmount.getWeight(a) * incrementMultiplier);
      } else {
        toIncrement.setWeight(a, incrementAmount.getWeight(a) * incrementMultiplier);
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