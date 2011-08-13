package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.HashMultimap;
import com.jayantkrish.jklol.util.SparseOutcomeTable;

/**
 * A TableFactor is a representation of a factor where each weight is set
 * beforehand. The internal representation is sparse, making it appropriate for
 * factors where many weight settings are 0.
 */
public class TableFactor extends DiscreteFactor {

  // I'm assuming factors are going to be sparse, otherwise a double[] would be
  // more efficient.
  private SparseOutcomeTable<Double> weights;

  // An index storing assignments containing particular variable values.
  private List<HashMultimap<Object, Assignment>> varValueAssignmentIndex;

  /**
   * Construct a TableFactor involving the specified variable numbers (whose
   * possible values are in variables). Note that vars can only contain
   * DiscreteVariables.
   */
  public TableFactor(VariableNumMap vars) {
    super(vars);

    weights = new SparseOutcomeTable<Double>(vars.getVariableNums());
    varValueAssignmentIndex = new ArrayList<HashMultimap<Object, Assignment>>(vars
        .getVariableNums().size());
    for (int i = 0; i < vars.getVariableNums().size(); i++) {
      varValueAssignmentIndex.add(new HashMultimap<Object, Assignment>());
    }
  }

  // //////////////////////////////////////////////////////////////////////////////
  // Factor overrides.
  // //////////////////////////////////////////////////////////////////////////////

  public Iterator<Assignment> outcomeIterator() {
    return weights.assignmentIterator();
  }

  public double getUnnormalizedProbability(Assignment a) {
    Preconditions.checkArgument(a.containsVars(weights.getVarNums()));

    // This is an optimization to avoid creating new Assignments
    // in this extremely common optimization.
    Assignment toCheck = null;
    if (a.size() == weights.getVarNums().size()) {
      toCheck = a;
    } else {
      toCheck = a.subAssignment(weights.getVarNums());
    }

    if (weights.containsKey(toCheck)) {
      return weights.get(toCheck);
    }
    return 0.0;
  }

  public Set<Assignment> getAssignmentsWithEntry(int varNum, Set<Object> varValues) {
    int varIndex = -1;
    List<Integer> varNums = getVars().getVariableNums();
    for (int i = 0; i < varNums.size(); i++) {
      if (varNums.get(i) == varNum) {
        varIndex = i;
        break;
      }
    }
    assert varIndex != -1;

    Set<Assignment> possibleAssignments = new HashSet<Assignment>();
    for (Object varValue : varValues) {
      possibleAssignments.addAll(varValueAssignmentIndex.get(varIndex).get(varValue));
    }
    return possibleAssignments;
  }

  // ///////////////////////////////////////////////////////////////////////////
  // TableFactor-specific methods
  // //////////////////////////////////////////////////////////////////////////

  /**
   * General purpose method for setting a factor weight.
   */
  public void setWeightList(List<? extends Object> varValues, double weight) {
    Preconditions.checkNotNull(varValues);
    Preconditions.checkArgument(getVars().size() == varValues.size());
    setWeight(getVars().outcomeToAssignment(varValues), weight);
  }

  public void setWeight(Assignment a, double weight) {
    Preconditions.checkArgument(weight >= 0.0);
    weights.put(a, weight);

    Assignment copy = new Assignment(a);
    List<Integer> varNums = getVars().getVariableNums();
    for (int i = 0; i < varNums.size(); i++) {
      varValueAssignmentIndex.get(i).put(copy.getVarValue(varNums.get(i)), copy);
    }
  }

  public String toString() {
    return weights.toString();
  }

  // /////////////////////////////////////////////////////////////////////////////////
  // Static methods, mostly for computing sums / products of factors
  // /////////////////////////////////////////////////////////////////////////////////

  /**
   * Alternate version of {@link #sumFactor(List)}.
   */
  public static TableFactor sumFactor(DiscreteFactor... factors) {
    return sumFactor(Arrays.asList(factors));
  }

  /**
   * Returns a {@code TableFactor} which sums over {@code factors}. The
   * probability of an assignment in the returned factor is the sum of the
   * probabilities returned for that assignment by all of the factors in {@code
   * factors}.
   * 
   * @param toAdd
   * @return
   */
  public static TableFactor sumFactor(List<DiscreteFactor> toAdd) {
    Preconditions.checkNotNull(toAdd);
    if (toAdd.size() == 0) {
      return new TableFactor(VariableNumMap.emptyMap());
    }
    
    VariableNumMap vars = toAdd.get(0).getVars();
    for (DiscreteFactor f : toAdd) {
      Preconditions.checkArgument(f.getVars().equals(vars));
    }

    TableFactor returnFactor = new TableFactor(vars);
    for (DiscreteFactor f : toAdd) {
    Iterator<Assignment> assignmentIter = f.outcomeIterator();
      while (assignmentIter.hasNext()) {
        Assignment a = assignmentIter.next();
        returnFactor.setWeight(a, returnFactor.getUnnormalizedProbability(a) + 
            f.getUnnormalizedProbability(a));
      }
    }

    // TODO(jayantk): Consider using the recursive algorithm (like product) when
    // the factors have lots of zero entries.
    return returnFactor;
  }

  /**
   * Same as {@link #maxFactor(List)}.
   * 
   * @param factors
   * @return
   */
  public static TableFactor maxFactor(DiscreteFactor... factors) {
    return maxFactor(factors);
  }

  /**
   * Returns a {@code TableFactor} which maximizes over {@code factors}. The
   * probability of an assignment in the returned factor is the maximum
   * probability returned for that assignment by any factor in {@code factors}.
   * 
   * @param factors
   * @return
   */
  public static TableFactor maxFactor(List<DiscreteFactor> factors) {
    Preconditions.checkNotNull(factors);
    if (factors.size() == 0) {
      return new TableFactor(VariableNumMap.emptyMap());
    }

    VariableNumMap vars = factors.get(0).getVars();
    for (DiscreteFactor factor : factors) {
      Preconditions.checkArgument(factor.getVars().equals(vars));
    }

    TableFactor returnFactor = new TableFactor(vars);
    for (DiscreteFactor factor : factors) {
      Iterator<Assignment> assignmentIter = factor.outcomeIterator();
      while (assignmentIter.hasNext()) {
        Assignment a = assignmentIter.next();
        if (factor.getUnnormalizedProbability(a) > returnFactor.getUnnormalizedProbability(a)) {
          returnFactor.setWeight(a, factor.getUnnormalizedProbability(a));
        }
      }
    }
    return returnFactor;
  }

  /**
   * Multiplies the unnormalized probabilities in {@code factor} by {@code
   * constant} and returns the result.
   * 
   * @param factor
   * @param constant
   * @return
   */
  public static TableFactor productFactor(DiscreteFactor factor, double constant) {
    Preconditions.checkNotNull(factor);
    Preconditions.checkArgument(constant >= 0.0);
    TableFactor returnFactor = new TableFactor(factor.getVars());
    Iterator<Assignment> iter = factor.outcomeIterator();
    while (iter.hasNext()) {
      Assignment a = iter.next();
      returnFactor.setWeight(a, factor.getUnnormalizedProbability(a) * constant);
    }
    return returnFactor;
  }

  public static TableFactor productFactor(DiscreteFactor... factors) {
    return productFactor(Arrays.asList(factors));
  }

  public static TableFactor productFactor(List<DiscreteFactor> toMultiply) {
    VariableNumMap allVars = VariableNumMap.emptyMap();
    for (DiscreteFactor f : toMultiply) {
      allVars = allVars.union(f.getVars());
    }

    // Check if we can use a faster multiplication algorithm that doesn't
    // enumerate all plausible assignments
    DiscreteFactor whole = null;
    List<DiscreteFactor> others = new ArrayList<DiscreteFactor>();
    for (DiscreteFactor f : toMultiply) {
      if (whole == null && f.getVars().equals(allVars)) {
        whole = f;
      } else {
        others.add(f);
      }
    }
    if (whole != null) {
      return subsetProductFactor(whole, others);
    }

    // Can't use the faster algorithm. Find all possible value assignments to
    // each variable,
    // then try each possible combination and calculate its probability.
    List<Object> varValues = new ArrayList<Object>(allVars.size());
    for (int i = 0; i < allVars.size(); i++) {
      varValues.add(null);
    }

    TableFactor returnFactor = new TableFactor(allVars);
    Iterator<Assignment> assignmentIter = new AllAssignmentIterator(allVars);
    while (assignmentIter.hasNext()) {
      Assignment a = assignmentIter.next();
      double weight = 1.0;
      for (DiscreteFactor f : toMultiply) {
        weight *= f.getUnnormalizedProbability(a.subAssignment(f.getVars()));
      }
      if (weight > 0) {
        returnFactor.setWeight(a, weight);
      }
    }

    // TODO(jayantk): this algorithm is faster when variables have lots of 0
    // probability entries.
    // Map<Integer, Set<Integer>> varValueMap =
    // getPossibleVariableValues(toMultiply);
    // recursiveFactorInitialization(varNums, vars, 0, varValues, toMultiply,
    // returnFactor, varValueMap);
    return returnFactor;
  }

  /**
   * Multiples one factor with a set of factors.
   */
  private static TableFactor subsetProductFactor(DiscreteFactor whole, List<DiscreteFactor> subsets) {
    Map<Integer, Set<Object>> varValueMap = getPossibleVariableValues(subsets);
    Set<Assignment> possibleAssignments = null;
    for (Integer varNum : varValueMap.keySet()) {
      if (possibleAssignments == null) {
        possibleAssignments = whole.getAssignmentsWithEntry(varNum, varValueMap.get(varNum));
      } else {
        possibleAssignments.retainAll(whole
            .getAssignmentsWithEntry(varNum, varValueMap.get(varNum)));
      }
    }

    Iterator<Assignment> iter = null;
    if (possibleAssignments != null) {
      iter = possibleAssignments.iterator();
    } else {
      iter = whole.outcomeIterator();
    }
    TableFactor returnFactor = new TableFactor(whole.getVars());
    while (iter.hasNext()) {
      Assignment a = iter.next();
      double prob = whole.getUnnormalizedProbability(a);
      for (DiscreteFactor subset : subsets) {
        Assignment sub = a.subAssignment(subset.getVars().getVariableNums());
        prob *= subset.getUnnormalizedProbability(sub);
      }
      if (prob > 0) {
        returnFactor.setWeight(a, prob);
      }
    }
    return returnFactor;
  }

  /*
   * private static void recursiveFactorInitialization(List<Integer> varNums,
   * List<Variable> vars, int curInd, List<Object> varAssignments,
   * List<DiscreteFactor> factors, TableFactor returnFactor, Map<Integer,
   * Set<Integer>> varValueMap) {
   * 
   * if (curInd == varNums.size()) { // Base case: varAssignments has a unique
   * assignment to all variables, so // we now must initialize the factor
   * weight. double weight = 1.0; for (DiscreteFactor f : factors) { weight *=
   * f.getUnnormalizedProbability(varNums, varAssignments); } if (weight > 0) {
   * returnFactor.setWeightList(varAssignments, weight); } } else { Variable
   * curVar = vars.get(curInd); Set<Integer> values =
   * varValueMap.get(varNums.get(curInd)); for (Integer i : values) {
   * varAssignments.set(curInd, curVar.getValue(i));
   * recursiveFactorInitialization(varNums, vars, curInd + 1, varAssignments,
   * factors, returnFactor, varValueMap); } } }
   */

  /*
   * Helper method for deciding all possible assignments to each variable in the
   * provided list of factors.
   */
  private static Map<Integer, Set<Object>> getPossibleVariableValues(List<DiscreteFactor> factors) {
    Map<Integer, Set<Object>> varValueMap = new HashMap<Integer, Set<Object>>();
    for (DiscreteFactor f : factors) {
      Iterator<Assignment> assignmentIterator = f.outcomeIterator();
      HashMultimap<Integer, Object> factorValueMap = new HashMultimap<Integer, Object>();
      while (assignmentIterator.hasNext()) {
        Assignment a = assignmentIterator.next();
        List<Integer> varNumsSorted = a.getVarNumsSorted();
        List<Object> varValuesSorted = a.getVarValuesInKeyOrder();
        for (int i = 0; i < varNumsSorted.size(); i++) {
          factorValueMap.put(varNumsSorted.get(i), varValuesSorted.get(i));
        }
      }

      for (Integer varNum : f.getVars().getVariableNums()) {
        if (!varValueMap.containsKey(varNum)) {
          varValueMap.put(varNum, new HashSet<Object>(factorValueMap.get(varNum)));
        } else {
          varValueMap.get(varNum).retainAll(factorValueMap.get(varNum));
        }
      }
    }
    return varValueMap;
  }
}