package com.jayantkrish.jklol.models.loglinear;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.Plate;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
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
  private List<VariablePattern> factorPatterns;

  private VariableNumMap discreteVariables;

  /**
   * Create an empty log-linear model builder
   */
  public LogLinearModelBuilder() {
    super();
    // Track model features / which factors can be trained.
    factorGraph = new FactorGraph();
    logLinearFactors = Lists.newArrayList();
    factorPatterns = Lists.newArrayList();
    discreteVariables = VariableNumMap.emptyMap();
  }
  
  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  public ParametricFactorGraph build() {
    return new ParametricFactorGraph(factorGraph, logLinearFactors, factorPatterns);
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
    discreteVariables = discreteVariables.addMapping(varNum, name, variable);
    return varNum;
  }
  
  public int addVariable(String name, Variable variable) {
    factorGraph = factorGraph.addVariable(name, variable);
    return factorGraph.getVariableIndex(name);
  }
  
  /**
   * Gets the variables that {@code this} is defined over.
   * 
   * @return
   */
  public VariableNumMap getVariables() {
    return factorGraph.getVariables();
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
  public void addFactor(ParametricFactor<SufficientStatistics> factor, VariablePattern factorPattern) {
    logLinearFactors.add(factor);
    factorPatterns.add(factorPattern);
  }
  
  /**
   * Adds a set of dynamically-instantiated variables to this factor graph.
   * 
   * @param plate
   */
  public void addPlate(Plate plate) {
    factorGraph = factorGraph.addPlate(plate);
  }
}