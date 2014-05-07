package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

public class BranchingFactorGraph {
  private final FactorGraph factorGraph;
  private final List<ChildBfg> children;

  public BranchingFactorGraph(FactorGraph factorGraph, List<ChildBfg> children) {
    this.factorGraph = Preconditions.checkNotNull(factorGraph);
    this.children = Preconditions.checkNotNull(children);
  }

  private FactorGraph getFactorGraph() {
    return factorGraph;
  }
  
  private FactorGraph buildChildFactorGraph(boolean useSumProduct) {
    FactorGraph combinedFactorGraph = factorGraph;
    for (int i = 0; i < children.size(); i++) {
      Factor childMessage = children.get(i).getMessage(useSumProduct);
      combinedFactorGraph = combinedFactorGraph.addFactor("child-" + i, childMessage);
    }
    return combinedFactorGraph;
  }

  public MarginalSet getMarginals() {
    FactorGraph combinedFactorGraph = buildChildFactorGraph(true);

    System.out.println("calculating marginals: " + combinedFactorGraph.getVariables().size() + " vars");
    
    JunctionTree jt = new JunctionTree();
    MarginalSet marginals = jt.computeMarginals(combinedFactorGraph);
    return marginals;
  }

  public MarginalSet getMarginals(VariableNumMap vars) {
    FactorGraph combinedFactorGraph = buildChildFactorGraph(true);

    combinedFactorGraph = combinedFactorGraph.getConnectedComponent(vars);

    JunctionTree jt = new JunctionTree();
    MarginalSet marginals = jt.computeMarginals(combinedFactorGraph);
    return marginals;
  }

  public MaxMarginalSet getMaxMarginals() {
    FactorGraph combinedFactorGraph = factorGraph;

    for (int i = 0; i < children.size(); i++) {
      Factor childMessage = children.get(i).getMessage(false);
      System.out.println("childMessage: ");
      System.out.println(childMessage.getParameterDescription());
      combinedFactorGraph = combinedFactorGraph.addFactor("child-" + i, childMessage);
    }
    
    JunctionTree jt = new JunctionTree();
    MaxMarginalSet marginals = jt.computeMaxMarginals(combinedFactorGraph);
    return marginals;
  }
  
  public String getParameterDescription() {
    StringBuilder sb = new StringBuilder();
    getParameterDescription(sb, 0);
    return sb.toString();
  }
  
  public void getParameterDescription(StringBuilder sb, int depth) {
    sb.append(factorGraph.getParameterDescription());
    for (ChildBfg child : children) {
      child.getParameterDescription(sb, depth + 1);
    }
  }

  public static class ChildBfg {
    private final BranchingFactorGraph factorGraph;
    private final VariableNumMap vars;
    private final Assignment assignment;

    // Crossing factors are factors whose variables partly
    // belong to the child factor graph and to the
    // parent factor graph.
    private final List<Factor> crossingFactors;

    public ChildBfg(BranchingFactorGraph factorGraph, VariableNumMap vars, Assignment assignment,
        List<Factor> crossingFactors) {
      this.factorGraph = Preconditions.checkNotNull(factorGraph);
      this.vars = Preconditions.checkNotNull(vars);
      this.assignment = Preconditions.checkNotNull(assignment);
      this.crossingFactors = ImmutableList.copyOf(crossingFactors);
    }

    public BranchingFactorGraph getFactorGraph() {
      return factorGraph;
    }

    public VariableNumMap getVars() {
      return vars;
    }

    public Assignment getAssignment() {
      return assignment;
    }
    
    public List<Factor> getCrossingFactors() {
      return crossingFactors;
    }

    public Factor getMessage(boolean useSumProduct) {
      VariableNumMap childVars = factorGraph.getFactorGraph().getVariables();

      VariableNumMap crossingVars = VariableNumMap.EMPTY;
      for (Factor crossingFactor : crossingFactors) {
        crossingVars = crossingVars.union(crossingFactor.getVars());
      }

      VariableNumMap crossingChildVars = crossingVars.intersection(childVars);
      Factor childMarginal = null;
      if (useSumProduct) {
        MarginalSet marginals = factorGraph.getMarginals();
        childMarginal = marginals.getMarginal(crossingChildVars);
      } else {
        MaxMarginalSet maxMarginals = factorGraph.getMaxMarginals();
        childMarginal = maxMarginals.getMaxMarginal(crossingChildVars);
      }

      Factor message = TableFactor.unity(crossingVars);
      message = message.product(childMarginal);
      for (Factor crossingFactor : crossingFactors) {
        message = message.product(crossingFactor);
      }
      if (useSumProduct) {
        message = message.marginalize(crossingChildVars);
      } else {
        message = message.maxMarginalize(crossingChildVars);
      }

      VariableNumMap conditionVars = message.getVars();
      VariableNumMap messageVars = vars.union(conditionVars);
      TableFactorBuilder tfBuilder = TableFactorBuilder.ones(messageVars);
      tfBuilder.incrementWeight(TableFactor.pointDistribution(vars, assignment)
          .outerProduct(TableFactor.unity(conditionVars)).product(-1.0));
      tfBuilder.incrementWeight(TableFactor.pointDistribution(vars, assignment).outerProduct(message));

      return tfBuilder.build();
    }

    public void getParameterDescription(StringBuilder sb, int depth) {
      sb.append("condition: " + vars + "=" + assignment + "\n");
      factorGraph.getParameterDescription(sb, depth);
      
      for (Factor crossingFactor : crossingFactors) {
        sb.append("crossing: \n");
        sb.append(crossingFactor.getParameterDescription());
      }
    }
  }
}
