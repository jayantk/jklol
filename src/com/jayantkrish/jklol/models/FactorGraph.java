package com.jayantkrish.jklol.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A graphical model represented as a set of factors over a set of variables.
 * Both {@code FactorGraph}s and the {@link Factor}s they contain are immutable.
 * 
 * <p>
 * This class may represent a conditional probability distribution, where some
 * variables' values are provided as an {@code Assignment}. Conversely, factor
 * graphs may require some variables to be conditioned on before they represent
 * a legitimate probability distribution.
 * 
 * <p>
 * {@code FactorGraph}s can be constructed incrementally using methods such as
 * {@link #addVariable(String, Variable)}. These construction methods return new
 * instances of this class with certain fields modified.
 * 
 * @author jayantk
 */
public class FactorGraph implements Serializable {

  private VariableNumMap variables;

  private HashMultimap<Integer, Integer> variableFactorMap;
  private HashMultimap<Integer, Integer> factorVariableMap;
  private List<Factor> factors;
  private IndexedList<String> factorNames;

  // Store any conditioning information that lead to this particular
  // distribution.
  private VariableNumMap conditionedVariables;
  private Assignment conditionedValues;

  private InferenceHint inferenceHint;

  /**
   * Create an empty factor graph, without any variables or factors. The factor
   * graph can be incrementally constructed from this point using methods like
   * {@link #addVariable(String, Variable)}.
   */
  public FactorGraph() {
    variables = VariableNumMap.emptyMap();
    variableFactorMap = HashMultimap.create();
    factorVariableMap = HashMultimap.create();
    factors = Lists.newArrayList();
    factorNames = IndexedList.create();
    conditionedVariables = VariableNumMap.emptyMap();
    conditionedValues = Assignment.EMPTY;
    inferenceHint = null;
  }

  public FactorGraph(VariableNumMap variables, List<Factor> factors, List<String> factorNames,
      VariableNumMap conditionedVariables, Assignment conditionedAssignment) {
    this.variables = variables;
    this.factors = Lists.newArrayList(factors);
    this.factorNames = new IndexedList<String>(factorNames);
    Preconditions.checkArgument(this.factors.size() == this.factorNames.size(),
        "%s names for %s", this.factorNames, this.factors);

    // Initialize variable -> factor mapping
    variableFactorMap = HashMultimap.create();
    factorVariableMap = HashMultimap.create();
    for (int i = 0; i < factors.size(); i++) {
      VariableNumMap factorVars = factors.get(i).getVars();
      for (Integer j : factorVars.getVariableNums()) {
        variableFactorMap.put(j, i);
        factorVariableMap.put(i, j);
      }
    }

    this.conditionedVariables = conditionedVariables;
    this.conditionedValues = conditionedAssignment;
    this.inferenceHint = null;
  }

  /**
   * Copy constructor. This method is private because {@code FactorGraph}s are
   * immutable.
   * 
   * @param factorGraph
   */
  private FactorGraph(FactorGraph factorGraph) {
    this.variables = factorGraph.variables;
    this.variableFactorMap = HashMultimap.create(factorGraph.variableFactorMap);
    this.factorVariableMap = HashMultimap.create(factorGraph.factorVariableMap);
    this.factors = Lists.newArrayList(factorGraph.factors);
    this.factorNames = new IndexedList<String>(factorGraph.factorNames);
    this.conditionedVariables = factorGraph.conditionedVariables;
    this.conditionedValues = factorGraph.conditionedValues;
    this.inferenceHint = factorGraph.inferenceHint;
  }

  /**
   * Constructs a {@code FactorGraph} directly from a list of factors. The
   * variables and variable numbers in the graph are determined by the factors,
   * and their names are unspecified.
   * 
   * @param factors
   */
  public static FactorGraph createFromFactors(List<Factor> factors) {
    VariableNumMap allVars = VariableNumMap.emptyMap();
    for (int i = 0; i < factors.size(); i++) {
      VariableNumMap factorVars = factors.get(i).getVars();
      allVars = allVars.union(factorVars);
    }

    List<String> factorNames = Lists.newArrayList();
    for (int i = 0; i < factors.size(); i++) {
      factorNames.add("factor-" + i);
    }
    return new FactorGraph(allVars, factors, factorNames, VariableNumMap.emptyMap(),
        Assignment.EMPTY);
  }

  /**
   * Get the number of factors in the graph.
   */
  public int numFactors() {
    return factors.size();
  }

  /**
   * Get a factor using its index number.
   */
  public Factor getFactor(int factorNum) {
    return factors.get(factorNum);
  }

  /**
   * Gets the indices of all factors which are adjacent (that is, share at least
   * one variable) with {@code factorNum}.
   * 
   * @param factorNum
   * @return
   */
  public Set<Integer> getAdjacentFactors(int factorNum) {
    Set<Integer> adjacentFactors = Sets.newHashSet();
    for (Integer variableNum : factorVariableMap.get(factorNum)) {
      adjacentFactors.addAll(variableFactorMap.get(variableNum));
    }
    return adjacentFactors;
  }

  /**
   * Get all factors.
   */
  public List<Factor> getFactors() {
    return factors;
  }

  /**
   * Gets the factor in this named {@code name}. Returns {@code null} if no such
   * factor exists.
   * 
   * @param name
   * @return
   */
  public Factor getFactorByName(String name) {
    if (!factorNames.contains(name)) {
      return null;
    }
    int index = factorNames.getIndex(name);
    return factors.get(index);
  }

  /**
   * Gets a list of factors in this. This method is similar to
   * {@link #getFactors()}, except that it merges together factors defined over
   * the same variables. Hence, no factor in the returned list will be defined
   * over a subset of the variables in another factor. The returned factors
   * define the same probability distribution as this.
   * 
   * @return
   */
  public List<Factor> getMinimalFactors() {
    // Sort factors in descending order of size.
    List<Factor> sortedFactors = Lists.newArrayList(factors);
    Collections.sort(sortedFactors, new Comparator<Factor>() {
      public int compare(Factor f1, Factor f2) {
        return f2.getVars().size() - f1.getVars().size();
      }
    });

    List<List<Factor>> factorsToMerge = Lists.newArrayList();
    Set<Integer> factorNums = Sets.newHashSet();
    Multimap<Integer, Integer> varFactorIndex = HashMultimap.create();
    for (Factor f : sortedFactors) {
      Set<Integer> mergeableFactors = Sets.newHashSet(factorNums);
      for (Integer varNum : f.getVars().getVariableNums()) {
        mergeableFactors.retainAll(varFactorIndex.get(varNum));
      }

      if (mergeableFactors.size() > 0) {
        int factorIndex = Iterables.getFirst(mergeableFactors, -1);
        factorsToMerge.get(factorIndex).add(f);
      } else {
        for (Integer varNum : f.getVars().getVariableNums()) {
          varFactorIndex.put(varNum, factorsToMerge.size());
        }
        factorNums.add(factorsToMerge.size());
        factorsToMerge.add(Lists.newArrayList(f));
      }
    }

    // Merge factors using size as a guideline
    List<Factor> finalFactors = Lists.newArrayListWithCapacity(factorsToMerge.size());
    for (List<Factor> toMerge : factorsToMerge) {
      // Sort the factors by their .size() attribute, sparsest factors first.
      Collections.sort(toMerge, new Comparator<Factor>() {
        public int compare(Factor f1, Factor f2) {
          return (int) (f1.size() - f2.size());
        }
      });
      finalFactors.add(Factors.product(toMerge));
    }
    return finalFactors;
  }

  /**
   * Gets the names of the factors in {@code this}. The ith returned entry is
   * the name of the ith factor returned by {@link #getFactors()}.
   * 
   * @return
   */
  public List<String> getFactorNames() {
    return factorNames.items();
  }

  /**
   * Get the variables that this factor graph is defined over. This method only
   * returns variables with probability distributions over them, i.e., variables
   * that have not been conditioned on.
   * 
   * @return
   */
  public VariableNumMap getVariables() {
    return variables;
  }

  /**
   * Gets any variables whose values have been conditioned on. The returned set
   * of variables matches the variables that {@code this.getConditionedValues()}
   * is defined over.
   * 
   * @return
   */
  public VariableNumMap getConditionedVariables() {
    return conditionedVariables;
  }

  /**
   * Gets all of the variables contained in this factor graph, including
   * variables with probability distributions as well as variables with fixed
   * values.
   * 
   * @return
   */
  public VariableNumMap getAllVariables() {
    return variables.union(conditionedVariables);
  }

  /**
   * Gets the assignment to variables whose values have been conditioned on.
   * 
   * @return
   */
  public Assignment getConditionedValues() {
    return conditionedValues;
  }

  /**
   * Gets an assignment for the named set of variables.
   */
  public Assignment outcomeToAssignment(List<String> factorVariables, List<? extends Object> outcome) {
    assert factorVariables.size() == outcome.size();

    List<Integer> varNums = new ArrayList<Integer>(factorVariables.size());
    List<Object> outcomeValueInds = new ArrayList<Object>(outcome.size());
    for (int i = 0; i < factorVariables.size(); i++) {
      int varInd = getVariables().getVariableByName(factorVariables.get(i));
      varNums.add(varInd);
      outcomeValueInds.add(outcome.get(i));
    }
    return new Assignment(varNums, outcomeValueInds);
  }

  /**
   * Identical to {@link #outcomeToAssignment(List, List)}, but with arrays.
   * 
   * @param factorVariables
   * @param outcome
   * @return
   */
  public Assignment outcomeToAssignment(String[] factorVariables, Object[] outcome) {
    return outcomeToAssignment(Arrays.asList(factorVariables), Arrays.asList(outcome));
  }

  public Map<String, Object> assignmentToObject(Assignment a) {
    Map<String, Object> objectVals = new HashMap<String, Object>();
    for (String varName : getVariables().getVariableNames()) {
      int varNum = getVariables().getVariableByName(varName);
      if (a.contains(varNum)) {
        objectVals.put(varName, a.getValue(varNum));
      }
    }
    return objectVals;
  }

  /**
   * Get all of the factors which contain the passed-in varNum
   */
  public Set<Integer> getFactorsWithVariable(int varNum) {
    return variableFactorMap.get(varNum);
  }

  /**
   * Get all of the variables that the two factors have in common.
   */
  public Set<Integer> getSharedVariables(int factor1, int factor2) {
    Set<Integer> varNums = new HashSet<Integer>(factorVariableMap.get(factor1));
    varNums.retainAll(factorVariableMap.get(factor2));
    return varNums;
  }

  /**
   * Gets a hint about how to efficiently perform inference with this model. May
   * return {@code null}, in which case the hint should be ignored.
   */
  public InferenceHint getInferenceHint() {
    return inferenceHint;
  }

  /**
   * Gets the unnormalized probability of {@code assignment}. This method only
   * supports assignments which do not require inference, so {@code assignment}
   * must contain a value for every variable in {@code this}. To calculate
   * marginal probabilities or max-marginals, see {@link InferenceEngine}.
   * 
   * @param assignment
   * @return
   */
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(variables.getVariableNums()),
        "Invalid assignment %s to factor graph on variables %s", assignment, variables);
    double probability = 1.0;
    for (Factor factor : factors) {
      double factorProb = factor.getUnnormalizedProbability(assignment);
      probability *= factorProb;
    }
    return probability;
  }

  /**
   * Gets the unnormalized log probability of {@code assignment}. This method
   * only supports assignments which do not require inference, so
   * {@code assignment} must contain a value for every variable in {@code this}.
   * To calculate marginal probabilities or max-marginals, see
   * {@link InferenceEngine}.
   * 
   * @param assignment
   * @return
   */
  public double getUnnormalizedLogProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(variables.getVariableNums()),
        "Invalid assignment %s to factor graph on variables %s", assignment, variables);
    double logProbability = 0.0;
    for (Factor factor : factors) {
      logProbability += factor.getUnnormalizedLogProbability(assignment);
    }
    return logProbability;
  }

  public String getParameterDescription() {
    StringBuilder sb = new StringBuilder();
    for (Factor factor : factors) {
      sb.append(factor.getParameterDescription());
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return "FactorGraph: (" + factors.size() + " factors) " + factors.toString();
  }

  // /////////////////////////////////////////////////////////////////
  // Methods for incrementally building FactorGraphs
  // /////////////////////////////////////////////////////////////////

  /**
   * Gets a new {@code FactorGraph} identical to this one, except with an
   * additional variable. The new variable is named {@code variableName} and has
   * type {@code variable}. Each variable in a factor graph must have a unique
   * name; hence {@code this} must not already contain a variable named
   * {@code variableName}.
   */
  public FactorGraph addVariable(String variableName, Variable variable) {
    Preconditions.checkArgument(!getVariables().contains(variableName));
    int varNum = getAllVariables().size() > 0 ? Collections.max(getAllVariables().getVariableNums()) + 1 : 0;
    return addVariableWithIndex(variableName, variable, varNum);
  }

  private FactorGraph addVariableWithIndex(String variableName, Variable variable, int varNum) {
    FactorGraph factorGraph = new FactorGraph(this);
    factorGraph.variables = factorGraph.variables.addMapping(varNum, variableName, variable);
    return factorGraph;
  }

  /**
   * Gets a new {@code FactorGraph} identical to this one, except with an
   * additional factor. {@code factor} must be defined over variables which are
   * already in {@code this} graph.
   */
  public FactorGraph addFactor(String factorName, Factor factor) {
    Preconditions.checkArgument(getVariables().containsAll(factor.getVars()));

    FactorGraph factorGraph = new FactorGraph(this);
    int factorNum = factorGraph.factors.size();
    factorGraph.factors.add(factor);
    factorGraph.factorNames.add(factorName);

    for (Integer i : factor.getVars().getVariableNums()) {
      factorGraph.variableFactorMap.put(i, factorNum);
      factorGraph.factorVariableMap.put(factorNum, i);
    }
    return factorGraph;
  }

  /**
   * Gets a new {@code FactorGraph} identical to this one, except with every
   * variable in {@code varNumsToEliminate} marginalized out. The returned
   * {@code FactorGraph} is defined on the variables in {@code this}, minus any
   * of the passed-in variables. This procedure performs variable elimination on
   * each variable in the order returned by the iterator over
   * {@code varNumsToEliminate}. Choosing a good order (i.e., one with low
   * treewidth) can dramatically improve the performance of this method. This
   * method is preferred if you wish to actively manipulate the returned factor
   * graph. If you simply want marginals, see {@link MarginalCalculator}.
   * 
   * @param factor
   * @return
   */
  public FactorGraph marginalize(Collection<Integer> varNumsToEliminate) {
    FactorGraph currentFactorGraph = this;
    for (Integer eliminatedVariableIndex : varNumsToEliminate) {
      // Each iteration marginalizes out a single variable from
      // currentFactorGraph,
      // aggregating intermediate results in nextFactorGraph.
      FactorGraph nextFactorGraph = new FactorGraph();

      // Copy the variables in currentFactorGraph to nextFactorGraph
      for (String variableName : currentFactorGraph.getVariables().getVariableNames()) {
        int varIndex = currentFactorGraph.getVariables().getVariableByName(variableName);
        if (varIndex != eliminatedVariableIndex) {
          nextFactorGraph = nextFactorGraph.addVariableWithIndex(variableName,
              currentFactorGraph.variables.getVariable(varIndex), varIndex);
        }
      }

      // Identify the factors which contain the variable, which must be
      // multiplied together. All other factors can be immediately copied into
      // the next factor graph.
      List<Factor> factorsToMultiply = Lists.newArrayList();
      String mulName = null;
      List<Factor> currentFactors = currentFactorGraph.getFactors();
      List<String> currentFactorNames = currentFactorGraph.getFactorNames();
      for (int i = 0; i < currentFactors.size(); i++) {
        Factor factor = currentFactors.get(i);
        if (factor.getVars().contains(eliminatedVariableIndex)) {
          factorsToMultiply.add(factor);
          mulName = currentFactorNames.get(i);
        } else {
          // No variable in factor is being eliminated, so we don't have to
          // modify it.
          nextFactorGraph = nextFactorGraph.addFactor(currentFactorNames.get(i), factor);
        }
      }

      if (factorsToMultiply.size() > 0) {
        // If the variable is present, eliminate it!
        Factor productFactor = Factors.product(factorsToMultiply);
        nextFactorGraph = nextFactorGraph.addFactor(mulName,
            productFactor.marginalize(eliminatedVariableIndex));
      }

      currentFactorGraph = nextFactorGraph;
    }
    return currentFactorGraph;
  }

  /**
   * Convert this factor graph into a conditional probability distribution given
   * {@code assignment}. {@code assignment} may contain variables which this
   * graph is not defined over; these extra variables are ignored by this
   * method. The returned factor graph contains at least the variables in this,
   * minus any variables with values in {@code assignment}. The names and
   * indices of these variables are preserved by this method.
   * 
   * @param assignment
   * @return
   */
  public FactorGraph conditional(Assignment assignment) {
    Preconditions.checkArgument(variables.containsAll(assignment.getVariableNums()));

    // Short-circuit when nothing is conditioned on. Also the base case when
    // instantiating assignments from plates.
    if (assignment.equals(Assignment.EMPTY)) {
      return this;
    }

    Assignment newConditionedValues = this.conditionedValues.union(assignment);
    VariableNumMap newConditionedVariables = this.conditionedVariables.union(
        this.getVariables().intersection(assignment.getVariableNums()));

    VariableNumMap newVariables = getVariables().removeAll(assignment.getVariableNums());

    // Condition each factor on assignment.
    List<Factor> newFactors = Lists.newArrayListWithCapacity(getFactors().size());
    for (Factor factor : getFactors()) {
      newFactors.add(factor.conditional(assignment));
    }

    return new FactorGraph(newVariables, newFactors, factorNames.items(), newConditionedVariables,
        newConditionedValues);
  }

  /**
   * Gets a new {@code FactorGraph} identical to this one, with an added
   * inference hint. {@code inferenceHint} is a suggestion for performing
   * efficient inference with {@code this}. {@code inferenceHint} can be
   * {@code null}, in which case the hint is ignored during inference.
   */
  public FactorGraph addInferenceHint(InferenceHint inferenceHint) {
    FactorGraph factorGraph = new FactorGraph(this);
    factorGraph.inferenceHint = inferenceHint;
    return factorGraph;
  }
}
