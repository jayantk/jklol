package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricGfgBuilder {

  private final ParametricFactorGraphBuilder fgBuilder;

  private final List<ChildBuilder> children;
  
  public ParametricGfgBuilder(ParametricFactorGraphBuilder fgBuilder) {
    this.fgBuilder = Preconditions.checkNotNull(fgBuilder);
    this.children = Lists.newArrayList();
  }

  public void addVariables(VariableNumMap newVariables) {
    fgBuilder.addVariables(newVariables);
  }

  /**
   * Adds an unparameterized factor to the model under construction.
   * 
   * @param factor
   */
  public void addConstantFactor(String factorName, Factor factor) {
    fgBuilder.addConstantFactor(factorName, factor);
  }

  public ParametricGfgBuilder createChild(VariableNumMap vars, Assignment assignment) {
    ParametricGfgBuilder childBuilder = new ParametricGfgBuilder(new ParametricFactorGraphBuilder());
    children.add(new ChildBuilder(childBuilder, vars, assignment));
    return childBuilder;
  }

  public ParametricFactorGraph build() {
    return fgBuilder.build();
  }
  
  private static class ChildBuilder {
    private final ParametricGfgBuilder builder;
    private final VariableNumMap vars;
    private final Assignment assignment;
    
    public ChildBuilder(ParametricGfgBuilder builder, VariableNumMap vars,
        Assignment assignment) {
      this.builder = Preconditions.checkNotNull(builder);
      this.vars = Preconditions.checkNotNull(vars);
      this.assignment = Preconditions.checkNotNull(assignment);
    }

    public ParametricGfgBuilder getBuilder() {
      return builder;
    }
    
    public VariableNumMap getVars() {
      return vars;
    }
    
    public Assignment getAssignment() {
      return assignment;
    }
  }
}
