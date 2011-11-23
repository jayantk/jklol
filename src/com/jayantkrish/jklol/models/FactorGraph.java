package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.models.dynamic.Plate;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A {@code FactorGraph} represents a graphical model as a set of variables and
 * a set of factors defined over variable cliques. The graphical model may
 * represent a conditional distribution, which must have some fixed values
 * before it becomes a probability distribution.
 * 
 * {@code FactorGraph}s and the {@link Factor}s they contain are immutable.
 * Therefore, each {@code FactorGraph} instance defines a particular probability
 * distribution which cannot be changed.
 * 
 * {@code FactorGraph}s can be constructed incrementally using methods such as
 * {@link #addVariable(String, Variable)}. These construction methods return new
 * instances of this class with certain fields modified.
 */
public class FactorGraph {

  private VariableNumMap variables;
  private Map<String, Integer> variableNames;

  private HashMultimap<Integer, Integer> variableFactorMap;
  private HashMultimap<Integer, Integer> factorVariableMap;
  private IndexedList<Factor> factors;

  // Plates represent graphical model structure which is replicated in a
  // data-dependent fashion.
  private List<Plate> plates;
  // private IndexedList<PlateFactor> plateFactors;

  // Store any conditioning information that lead to this particular
  // distribution.
  private Assignment conditionedValues;

  private InferenceHint inferenceHint;

  /**
   * Create an empty factor graph, which does not contain any {@code Variable}s
   * or {@code Factor}s. The factor graph can be incrementally constructed from
   * this point using methods like {@link #addVariable(String, Variable)}.
   */
  public FactorGraph() {
    variables = VariableNumMap.emptyMap();
    variableNames = new HashMap<String, Integer>();
    variableFactorMap = HashMultimap.create();
    factorVariableMap = HashMultimap.create();
    factors = new IndexedList<Factor>();
    plates = Lists.newArrayList();
    conditionedValues = Assignment.EMPTY;
    inferenceHint = null;
  }

  /**
   * Copy constructor. This method is private because {@code FactorGraph}s are
   * immutable.
   * 
   * @param factorGraph
   */
  private FactorGraph(FactorGraph factorGraph) {
    this.variables = factorGraph.variables;
    this.variableNames = new HashMap<String, Integer>(factorGraph.variableNames);
    this.variableFactorMap = HashMultimap.create(factorGraph.variableFactorMap);
    this.factorVariableMap = HashMultimap.create(factorGraph.factorVariableMap);
    this.factors = new IndexedList<Factor>(factorGraph.factors);
    this.plates = Lists.newArrayList(factorGraph.plates);
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
    HashMultimap<Integer, Integer> variableFactorMap = HashMultimap.create();
    HashMultimap<Integer, Integer> factorVariableMap = HashMultimap.create();
    for (int i = 0; i < factors.size(); i++) {
      VariableNumMap factorVars = factors.get(i).getVars();
      allVars = allVars.union(factorVars);
      for (Integer j : factorVars.getVariableNums()) {
        variableFactorMap.put(j, i);
        factorVariableMap.put(i, j);
      }
    }

    Map<String, Integer> variableNames = Maps.newHashMap();
    for (Integer variableNum : allVars.getVariableNums()) {
      variableNames.put("Var" + variableNum, variableNum);
    }
    // This is super ghetto.
    FactorGraph factorGraph = new FactorGraph();
    factorGraph.variables = allVars;
    factorGraph.variableNames = variableNames;
    factorGraph.variableFactorMap = variableFactorMap;
    factorGraph.factorVariableMap = factorVariableMap;
    factorGraph.conditionedValues = Assignment.EMPTY;
    factorGraph.factors = new IndexedList<Factor>(factors);
    return factorGraph;
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
    return factors.items();
  }

  /**
   * Gets the names of all variables in {@code this}.
   * 
   * @return
   */
  public Set<String> getVariableNames() {
    return Sets.newHashSet(variableNames.keySet());
  }

  /**
   * Get the variables that this factor graph is defined over.
   * 
   * @return
   */
  public VariableNumMap getVariables() {
    return variables;
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
   * Get the index of a variable from its name.
   */
  public int getVariableIndex(String variableName) {
    return variableNames.get(variableName);
  }

  /**
   * Gets an assignment for the named set of variables.
   */
  public Assignment outcomeToAssignment(List<String> factorVariables, List<? extends Object> outcome) {
    assert factorVariables.size() == outcome.size();

    List<Integer> varNums = new ArrayList<Integer>(factorVariables.size());
    List<Object> outcomeValueInds = new ArrayList<Object>(outcome.size());
    for (int i = 0; i < factorVariables.size(); i++) {
      int varInd = getVariableIndex(factorVariables.get(i));
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
    for (String varName : variableNames.keySet()) {
      int varNum = variableNames.get(varName);
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
   * must contain a value for every variable in {@code this}.
   * 
   * To calculate marginal probabilities or max-marginals, see
   * {@link InferenceEngine}.
   * 
   * @param assignment
   * @return
   */
  public double getUnnormalizedProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(variables.getVariableNums()));
    double probability = 1.0;
    for (Factor factor : factors) {
      probability *= factor.getUnnormalizedProbability(assignment);
    }
    return probability;
  }

  /**
   * Gets the unnormalized log probability of {@code assignment}. This method
   * only supports assignments which do not require inference, so
   * {@code assignment} must contain a value for every variable in {@code this}.
   * 
   * To calculate marginal probabilities or max-marginals, see
   * {@link InferenceEngine}.
   * 
   * @param assignment
   * @return
   */
  public double getUnnormalizedLogProbability(Assignment assignment) {
    Preconditions.checkArgument(assignment.containsAll(variables.getVariableNums()));
    double logProbability = 0.0;
    for (Factor factor : factors) {
      logProbability += Math.log(factor.getUnnormalizedProbability(assignment));
    }
    return logProbability;
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
    Preconditions.checkArgument(!this.variableNames.containsKey(variableName));
    int varNum = variables.size() > 0 ? Collections.max(variables.getVariableNums()) + 1 : 0;
    return addVariableWithIndex(variableName, variable, varNum);
  }

  private FactorGraph addVariableWithIndex(String variableName, Variable variable, int varNum) {
    FactorGraph factorGraph = new FactorGraph(this);
    factorGraph.variableNames.put(variableName, varNum);
    factorGraph.variables = factorGraph.variables.addMapping(varNum, variableName, variable);
    return factorGraph;
  }

  /**
   * Gets a new {@code FactorGraph} identical to this one, except with an
   * additional factor. {@code factor} must be defined over variables which are
   * already in {@code this} graph.
   */
  public FactorGraph addFactor(Factor factor) {
    Preconditions.checkArgument(getVariables().containsAll(factor.getVars()));

    FactorGraph factorGraph = new FactorGraph(this);
    int factorNum = factorGraph.factors.size();
    factorGraph.factors.add(factor);

    for (Integer i : factor.getVars().getVariableNums()) {
      factorGraph.variableFactorMap.put(i, factorNum);
      factorGraph.factorVariableMap.put(factorNum, i);
    }
    return factorGraph;
  }

  public FactorGraph addPlate(Plate plate) {
    Preconditions.checkArgument(getVariables().containsAll(plate.getReplicationVariables()));

    FactorGraph factorGraph = new FactorGraph(this);
    factorGraph.plates.add(plate);
    return factorGraph;
  }

  /**
   * Gets a new {@code FactorGraph} identical to this one, except with every
   * variable in {@code varNumsToEliminate} marginalized out. The returned
   * {@code FactorGraph} is defined on the variables in {@code this}, minus any
   * of the passed-in variables.
   * 
   * This procedure performs variable elimination on each variable in the order
   * returned by the iterator over {@code varNumsToEliminate}. Choosing a good
   * order (i.e., one with low treewidth) can dramatically improve the
   * performance of this method.
   * 
   * This method is preferred if you wish to actively manipulate the returned
   * factor graph. If you simply want marginals, see {@link MarginalCalculator}.
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
      for (String variableName : currentFactorGraph.getVariableNames()) {
        int varIndex = currentFactorGraph.getVariableIndex(variableName);
        if (varIndex != eliminatedVariableIndex) {
          nextFactorGraph = nextFactorGraph.addVariableWithIndex(variableName,
              currentFactorGraph.variables.getVariable(varIndex), varIndex);
        }
      }

      // Identify the factors which contain the variable, which must be
      // multiplied together. All other factors can be immediately copied into
      // the next factor graph.
      List<Factor> factorsToMultiply = Lists.newArrayList();
      for (Factor factor : currentFactorGraph.getFactors()) {
        if (factor.getVars().contains(eliminatedVariableIndex)) {
          factorsToMultiply.add(factor);
        } else {
          // No variable in factor is being eliminated, so we don't have to
          // modify it.
          nextFactorGraph = nextFactorGraph.addFactor(factor);
        }
      }

      if (factorsToMultiply.size() > 0) {
        // If the variable is present, eliminate it!
        Factor productFactor = FactorUtils.product(factorsToMultiply);
        nextFactorGraph = nextFactorGraph.addFactor(
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

    FactorGraph newFactorGraph = new FactorGraph();
    newFactorGraph.conditionedValues = this.conditionedValues.union(assignment);

    // Copy the uneliminated variables in currentFactorGraph to nextFactorGraph
    for (String variableName : getVariableNames()) {
      int varIndex = getVariableIndex(variableName);
      if (!assignment.contains(varIndex)) {
        newFactorGraph = newFactorGraph.addVariableWithIndex(variableName,
            variables.getVariable(varIndex), varIndex);
      }
    }

    // Dynamic variable instantiation for dynamic factor graphs.
    for (Plate plate : plates) {
      if (newFactorGraph.conditionedValues.containsAll(
          plate.getReplicationVariables().getVariableNums())) {
        // Instantiate the variables in this plate.
        Map<String, Variable> newVariables = plate.instantiateVariables(newFactorGraph.conditionedValues);
        for (Map.Entry<String, Variable> newVariable : newVariables.entrySet()) {
          newFactorGraph = newFactorGraph.addVariable(newVariable.getKey(), newVariable.getValue());
        }
      } else {
        newFactorGraph = newFactorGraph.addPlate(plate);
      }
    }

    // Dynamic factor construction for dynamic factor graphs.
    // TODO: this isn't implemented just yet.
    /*
     * for (PlateFactor plateFactor : plateFactors) { if
     * (plateFactor.canInstantiate(newFactorGraph.conditionedValues)) {
     * List<Factor> newFactors =
     * plateFactor.instantiateFactors(newFactorGraph.conditionedValues,
     * newFactorGraph.getVariables()); for (Factor factor : newFactors) {
     * newFactorGraph = newFactorGraph.addFactor(factor); } } else {
     * newFactorGraph = newFactorGraph.addPlateFactor(plateFactor); } }
     */

    // Condition each factor on assignment.
    for (Factor factor : getFactors()) {
      newFactorGraph = newFactorGraph.addFactor(factor.conditional(assignment));
    }

    return newFactorGraph;
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
