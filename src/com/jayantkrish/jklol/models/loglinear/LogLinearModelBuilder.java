package com.jayantkrish.jklol.models.loglinear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * A Markov Network with log-linear parameter weights, parameterized by an
 * arbitrary set of (clique-factored) feature functions.
 */
public class LogLinearModelBuilder {

  private FactorGraph factorGraph;
  private List<ParametricFactor<SufficientStatistics>> logLinearFactors;

  private VariableNumMap discreteVariables;

  /**
   * Create an empty log-linear model builder
   */
  public LogLinearModelBuilder() {
    super();
    // Track model features / which factors can be trained.
    factorGraph = new FactorGraph();
    logLinearFactors = new ArrayList<ParametricFactor<SufficientStatistics>>();
    discreteVariables = VariableNumMap.emptyMap();
  }
  
  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  public ParametricFactorGraph build() {
    return new ParametricFactorGraph(factorGraph, logLinearFactors);
  }

  /**
   * Adds a new discrete variable to {@code this}.
   * 
   * @param name
   * @param variable
   * @return
   */
  public int addDiscreteVariable(String name, DiscreteVariable variable) {
    factorGraph = factorGraph.addVariable(name, variable);
    int varNum = factorGraph.getVariableIndex(name);
    discreteVariables = discreteVariables.addMapping(varNum, variable);
    return varNum;
  }
  
  /**
   * Gets the variables in {@code this} with the given names.
   * 
   * @param names
   * @return
   */
  public VariableNumMap lookupVariables(Collection<String> names) {
    return factorGraph.lookupVariables(names);
  }
  
  /**
   * Gets the variables in {@code this} with the given names.
   * 
   * @param names
   * @return
   */
  public VariableNumMap lookupVariables(String... names) {
    return factorGraph.lookupVariables(Arrays.asList(names));
  }

  /**
   * Adds an unparameterized factor to the model under construction.
   * 
   * @param factor
   */
  public void addConstantFactor(Factor factor) {
    factorGraph = factorGraph.addFactor(factor);
  }

  /**
   * Adds a parameterized factor to the log linear model being constructed.
   * 
   * @param factor
   */
  public void addFactor(ParametricFactor<SufficientStatistics> factor) {
    logLinearFactors.add(factor);
  }
}