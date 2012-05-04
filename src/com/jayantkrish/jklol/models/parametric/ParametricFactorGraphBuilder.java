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
import com.jayantkrish.jklol.models.dynamic.WrapperVariablePattern;

/**
 * Builder for incrementally constructing {@link ParametricFactorGraph}s.
 * Depending on the types of factors added to the builder, this class can build
 * either a Bayesian network or a log-linear model.
 */
public class ParametricFactorGraphBuilder {

  private DynamicVariableSet variables;
  private List<PlateFactor> constantFactors;
  private List<String> constantFactorNames;

  private List<ParametricFactor> parametricFactors;
  private List<VariablePattern> factorPatterns;
  private List<String> parametricFactorNames;

  /**
   * Create an empty log-linear model builder
   */
  public ParametricFactorGraphBuilder() {
    super();
    variables = DynamicVariableSet.EMPTY;
    constantFactors = Lists.newArrayList(); 
    constantFactorNames = Lists.newArrayList();
    parametricFactors = Lists.newArrayList();
    factorPatterns = Lists.newArrayList();
    parametricFactorNames = Lists.newArrayList();
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
    return new ParametricFactorGraph(new DynamicFactorGraph(variables, constantFactors, constantFactorNames),
        parametricFactors, factorPatterns, parametricFactorNames); 
  }

  public void addVariable(String name, Variable variable) {
    variables = variables.addFixedVariable(name, variable);
  }

  public void addPlate(String plateName, DynamicVariableSet plateVariables, int maxReplications) {
    variables = variables.addPlate(plateName, variables, maxReplications);
  }

  public void addPlate(String plateName, VariableNumMap plateVariables, int maxReplications) {
    variables = variables.addPlate(plateName, DynamicVariableSet.fromVariables(plateVariables), maxReplications);
  }

  /**
   * Adds an unparameterized factor to the model under construction.
   * 
   * @param factor
   */
  public void addConstantFactor(String factorName, Factor factor) {
    constantFactors.add(ReplicatedFactor.fromFactor(factor));
    constantFactorNames.add(factorName);
  }

  /**
   * Adds an unparameterized, dynamically-instantiated factor to the model under
   * construction.
   * 
   * @param factor
   */
  public void addConstantFactor(String factorName, PlateFactor factor) {
    constantFactors.add(factor);
    constantFactorNames.add(factorName);
  }

  /**
   * Adds a parameterized factor to the log linear model being constructed.
   * 
   * @param factor
   */
  public void addFactor(String factorName, ParametricFactor factor, VariablePattern factorPattern) {
    parametricFactors.add(factor);
    factorPatterns.add(factorPattern);
    parametricFactorNames.add(factorName);
  }

  /**
   * Adds a parameterized, unreplicated factor to the model being constructed.
   * The factor will match only the variables which it is defined over.
   * 
   * @param factor
   */
  public void addUnreplicatedFactor(String factorName, ParametricFactor factor) {
    parametricFactors.add(factor);
    factorPatterns.add(new WrapperVariablePattern(factor.getVars()));
    parametricFactorNames.add(factorName);
  }
}