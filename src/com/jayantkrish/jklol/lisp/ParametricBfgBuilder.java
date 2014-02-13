package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.lisp.BranchingFactorGraph.ChildBfg;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricBfgBuilder {

  private final ParametricFactorGraphBuilder fgBuilder;
  private final boolean isRoot;

  private final List<Factor> crossingFactors;
  private final List<ChildBuilder> children;

  private final List<MarkedVars> markedVars;

  public ParametricBfgBuilder(boolean isRoot) {
    this.fgBuilder = new ParametricFactorGraphBuilder();
    this.markedVars = Lists.newArrayList();
    this.isRoot = isRoot;

    this.crossingFactors = Lists.newArrayList();
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
    if (fgBuilder.getVariables().containsAll(factor.getVars())) {
      fgBuilder.addConstantFactor(factorName, factor);
    } else {
      Preconditions.checkState(!isRoot);
      crossingFactors.add(factor);
    }
  }
  
  public List<Factor> getCrossingFactors() {
    return crossingFactors;
  }

  public ParametricBfgBuilder createChild(VariableNumMap vars, Assignment assignment) {
    ParametricBfgBuilder childBuilder = new ParametricBfgBuilder(false);
    children.add(new ChildBuilder(childBuilder, vars, assignment));
    return childBuilder;
  }

  public BranchingFactorGraph build() {
    List<ChildBfg> childrenFgs = Lists.newArrayList();
    for (ChildBuilder child : children) {
      childrenFgs.add(child.build());
    }

    ParametricFactorGraph pfg = fgBuilder.build();
    return new BranchingFactorGraph(pfg.getModelFromParameters(pfg.getNewSufficientStatistics())
        .conditional(DynamicAssignment.EMPTY), childrenFgs);
  }

  public ParametricFactorGraph buildNoBranching() {
    Preconditions.checkState(isRoot && children.size() == 0);
    return fgBuilder.build();
  }

  public void addMark(VariableNumMap vars, ParametricFactor pf, int parameterIndex) {
    markedVars.add(new MarkedVars(vars, pf, parameterIndex));
  }

  public List<MarkedVars> getMarkedVars() {
    return markedVars;
  }

  private static class ChildBuilder {
    private final ParametricBfgBuilder builder;
    private final VariableNumMap vars;
    private final Assignment assignment;
    
    public ChildBuilder(ParametricBfgBuilder builder, VariableNumMap vars,
        Assignment assignment) {
      this.builder = Preconditions.checkNotNull(builder);
      this.vars = Preconditions.checkNotNull(vars);
      this.assignment = Preconditions.checkNotNull(assignment);
    }

    public ParametricBfgBuilder getBuilder() {
      return builder;
    }
    
    public VariableNumMap getVars() {
      return vars;
    }
    
    public Assignment getAssignment() {
      return assignment;
    }

    public ChildBfg build() {
      return new ChildBfg(builder.build(), vars, assignment, builder.getCrossingFactors());
    }
  }
  
  public static class MarkedVars {
    private final VariableNumMap vars;
    private final ParametricFactor factor;
    private final int parameterIndex;

    public MarkedVars(VariableNumMap vars, ParametricFactor factor, int parameterIndex) {
      this.vars = Preconditions.checkNotNull(vars);
      this.factor = Preconditions.checkNotNull(factor);
      this.parameterIndex = parameterIndex;
    }

    public VariableNumMap getVars() {
      return vars;
    }

    public ParametricFactor getFactor() {
      return factor;
    }

    public int getParameterIndex() {
      return parameterIndex;
    }
  }
}
