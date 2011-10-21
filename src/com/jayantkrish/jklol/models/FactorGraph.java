package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * A {@code FactorGraph} represents a graphical model as a set of variables and
 * a set of factors defined over variable cliques.
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
    inferenceHint = null;
  }

  /**
   * Copy constructor.
   * 
   * @param factorGraph
   */
  private FactorGraph(FactorGraph factorGraph) {
    this.variables = new VariableNumMap(factorGraph.variables);
    this.variableNames = new HashMap<String, Integer>(factorGraph.variableNames);
    this.variableFactorMap = HashMultimap.create(factorGraph.variableFactorMap);
    this.factorVariableMap = HashMultimap.create(factorGraph.factorVariableMap);
    this.factors = new IndexedList<Factor>(factorGraph.factors);
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
   * Get a variable using its index number.
   */
  public Variable getVariableFromIndex(int varNum) {
    return variables.getVariable(varNum);
  }

  /**
   * Get the index of a variable from its name.
   */
  public int getVariableIndex(String variableName) {
    return variableNames.get(variableName);
  }

  /**
   * Get the variables in the factor graph.
   */
  public List<Variable> getVariables() {
    return variables.getVariables();
  }

  /**
   * Get the variables that this factor graph is defined over.
   * 
   * @return
   */
  public VariableNumMap getVariableNumMap() {
    return variables;
  }

  /**
   * Get the variable numbers and variables corresponding to the given set of
   * variable names. Note that the order of the names in factorVariables is
   * irrelevant.
   */
  public VariableNumMap lookupVariables(Collection<String> factorVariables) {
    List<Integer> varNums = new ArrayList<Integer>();
    List<Variable> vars = new ArrayList<Variable>();
    for (String variableName : factorVariables) {
      if (!variableNames.containsKey(variableName)) {
        throw new IllegalArgumentException("Must use an already specified variable name.");
      }
      varNums.add(variableNames.get(variableName));
      vars.add(variables.getVariable(variableNames.get(variableName)));
    }
    return new VariableNumMap(varNums, vars);
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
      if (a.containsVar(varNum)) {
        objectVals.put(varName, a.getVarValue(varNum));
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
    Preconditions.checkArgument(assignment.containsVars(variables.getVariableNums()));
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
    Preconditions.checkArgument(assignment.containsVars(variables.getVariableNums()));
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
   * type {@code variable}.
   */
  public FactorGraph addVariable(String variableName, Variable variable) {
    FactorGraph factorGraph = new FactorGraph(this);
    int varNum = factorGraph.variables.size();
    factorGraph.variableNames.put(variableName, varNum);
    factorGraph.variables = factorGraph.variables.addMapping(varNum, variable);
    return factorGraph;
  }

  /**
   * Gets a new {@code FactorGraph} identical to this one, except with an
   * additional factor. {@code factor} must be defined over variables which are
   * already in {@code this} graph; see {@link #lookupVariables(Collection)} for
   * an easy way to construct {@code factor} over a particular set of variables.
   */
  public FactorGraph addFactor(Factor factor) {
    Preconditions.checkArgument(getVariableNumMap().containsAll(factor.getVars()));

    FactorGraph factorGraph = new FactorGraph(this);
    int factorNum = factorGraph.factors.size();
    factorGraph.factors.add(factor);

    for (Integer i : factor.getVars().getVariableNums()) {
      factorGraph.variableFactorMap.put(i, factorNum);
      factorGraph.factorVariableMap.put(factorNum, i);
    }
    return factorGraph;
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
