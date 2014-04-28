package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.lisp.BranchingFactorGraph.ChildBfg;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricBfgBuilder {

  private final ParametricFactorGraphBuilder fgBuilder;
  private final boolean isRoot;
  private Assignment assignment;

  private final List<Factor> crossingFactors;
  private final List<ChildBuilder> children;

  private final List<MarkedVars> markedVars;
  
  private static int nextVarNum;

  public ParametricBfgBuilder(boolean isRoot) {
    this.fgBuilder = new ParametricFactorGraphBuilder();
    this.markedVars = Lists.newArrayList();
    this.isRoot = isRoot;
    this.assignment = Assignment.EMPTY;

    this.crossingFactors = Lists.newArrayList();
    this.children = Lists.newArrayList();
  }

  public static int getUniqueVarNum() {
    return nextVarNum++;
  }

  public void addVariables(VariableNumMap newVariables) {
    fgBuilder.addVariables(newVariables);
  }

  public void addAssignment(Assignment newAssignment) {
    assignment = assignment.union(newAssignment);
  }
  
  public Assignment getAssignment() {
    return assignment;
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
        .conditional(DynamicAssignment.EMPTY).conditional(assignment), childrenFgs);
  }

  public ParametricFactorGraph buildNoBranching() {
    Preconditions.checkState(isRoot && children.size() == 0);
    return fgBuilder.build();
  }

  public void addMark(VariableNumMap vars, ParametricFactor pf,
      VariableRelabeling varsToFactorRelabeling, int parameterIndex) {
    markedVars.add(new MarkedVars(vars, pf, varsToFactorRelabeling, new int[] {parameterIndex}));
  }

  public void addMark(VariableNumMap vars, ParametricFactor pf,
      VariableRelabeling varsToFactorRelabeling, int[] parameterIndexes) {
    markedVars.add(new MarkedVars(vars, pf, varsToFactorRelabeling, parameterIndexes));
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
    private final VariableRelabeling varsToFactorRelabeling;
    private final int[] parameterIds;

    public MarkedVars(VariableNumMap vars, ParametricFactor factor,
        VariableRelabeling varsToFactorRelabeling, int[] parameterIds) {
      this.vars = Preconditions.checkNotNull(vars);
      this.factor = Preconditions.checkNotNull(factor);
      this.varsToFactorRelabeling = Preconditions.checkNotNull(varsToFactorRelabeling);
      this.parameterIds = parameterIds;
    }

    public VariableNumMap getVars() {
      return vars;
    }

    public ParametricFactor getFactor() {
      return factor;
    }

    public VariableRelabeling getVarsToFactorRelabeling() {
      return varsToFactorRelabeling;
    }

    public int[] getParameterIds() {
      return parameterIds;
    }
    
    @Override
    public String toString() {
      return "mark:" + vars + ":" + Arrays.toString(parameterIds);
    }
  }
}
