package com.jayantkrish.jklol.models;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.dynamic.PlateFactor;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.dynamic.WrapperVariablePattern;

/**
 * Abstract base class for building factor graph-like objects. This
 * class allows users to associate objects of type {@code T} with
 * (possibly replicated) cliques in a dynamic factor graph. The
 * typical use of this class is to create a subclass with a
 * {@code build()} method that constructs the desired factor
 * graph-like object.
 * 
 * @author jayantk
 * @param <T> type of object associated with each clique
 */
public abstract class AbstractFactorGraphBuilder<T> {
  protected DynamicVariableSet variables;
  protected List<PlateFactor> constantFactors;
  protected List<String> constantFactorNames;

  protected List<T> parametricFactors;
  protected List<VariablePattern> factorPatterns;
  protected List<String> parametricFactorNames;

  /**
   * Create an empty log-linear model builder
   */
  public AbstractFactorGraphBuilder() {
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
  
  public DynamicVariableSet getDynamicVariableSet() {
    return variables;
  }

  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  /*
   * public ParametricFactorGraph build() { return new
   * ParametricFactorGraph(new DynamicFactorGraph(variables,
   * constantFactors, constantFactorNames), parametricFactors,
   * factorPatterns, parametricFactorNames); }
   */
  
  public void addVariable(String name, Variable variable) {
    variables = variables.addFixedVariable(name, variable);
  }

  /**
   * Adds {@code newVariables} to the variables in the factor
   * graph.
   * 
   * @param newVariables
   */
  public void addVariables(VariableNumMap newVariables) {
    variables = variables.addFixedVariables(newVariables);
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
   * Adds an unparameterized, dynamically-instantiated factor to the
   * model under construction.
   * 
   * @param factor
   */
  public void addConstantFactor(String factorName, PlateFactor factor) {
    constantFactors.add(factor);
    constantFactorNames.add(factorName);
  }

  /**
   * Adds a parameterized factor to the log linear model being
   * constructed.
   * 
   * @param factor
   */
  public void addFactor(String factorName, T factor, VariablePattern factorPattern) {
    parametricFactors.add(factor);
    factorPatterns.add(factorPattern);
    parametricFactorNames.add(factorName);
  }

  /**
   * Adds a parameterized, unreplicated factor to the model being
   * constructed. The factor will match only the variables which it is
   * defined over.
   * 
   * @param factor
   */
  public void addUnreplicatedFactor(String factorName, T factor, VariableNumMap vars) {
    parametricFactors.add(factor);
    factorPatterns.add(new WrapperVariablePattern(vars));
    parametricFactorNames.add(factorName);
  }
}
