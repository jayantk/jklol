package com.jayantkrish.jklol.models.parametric;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;

/**
 * Builder for incrementally constructing {@link ParametricFactorGraph}s.
 * Depending on the types of factors added to the builder, this class can build
 * either a Bayesian network or a log-linear model.
 */
public class ParametricFactorGraphBuilder {

  private DynamicVariableSet variables;
  private List<PlateFactor> constantFactors;

  private List<ParametricFactor<SufficientStatistics>> logLinearFactors;
  private List<VariablePattern> factorPatterns;

  /**
   * Create an empty log-linear model builder
   */
  public ParametricFactorGraphBuilder() {
    super();
    variables = DynamicVariableSet.EMPTY;
    constantFactors = Lists.newArrayList();
    logLinearFactors = Lists.newArrayList();
    factorPatterns = Lists.newArrayList();
  }

  /**
   * Gets the variables that {@code this} is defined over.
   * 
   * @return
   */
  public VariableNumMap getVariables() {
    return variables.getFixedVariables();
  }

  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  public ParametricFactorGraph build() {
    return new ParametricFactorGraph(new DynamicFactorGraph(variables, constantFactors),
        logLinearFactors, factorPatterns);
  }

  public void addVariable(String name, Variable variable) {
    variables = variables.addFixedVariable(name, variable);
  }

  public void addPlate(String plateName, DynamicVariableSet plateVariables) {
    variables = variables.addPlate(plateName, variables);
  }

  public void addPlate(String plateName, VariableNumMap plateVariables) {
    variables = variables.addPlate(plateName, DynamicVariableSet.fromVariables(plateVariables));
  }

  /**
   * Adds an unparameterized factor to the model under construction.
   * 
   * @param factor
   */
  public void addConstantFactor(Factor factor) {
    constantFactors.add(ReplicatedFactor.fromFactor(factor));
  }

  /**
   * Adds an unparameterized, dynamically-instantiated factor to the model under
   * construction.
   * 
   * @param factor
   */
  public void addConstantFactor(PlateFactor factor) {
    constantFactors.add(factor);
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
   * Adds a parameterized factor to the log linear model being constructed.
   * 
   * @param factor
   */
  public void addFactor(ParametricFactor<SufficientStatistics> factor) {
    logLinearFactors.add(factor);
    factorPatterns.add(VariablePattern.fromVariableNumMap(factor.getVars()));
  }
}