package com.jayantkrish.jklol.models.bayesnet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.jayantkrish.jklol.cfg.CptCfgFactor;
import com.jayantkrish.jklol.cfg.CptProductionDistribution;
import com.jayantkrish.jklol.cfg.Grammar;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

/**
 * A {@code BayesNetBuilder} provides methods for constructing a Bayesian
 * Network, which is represented as a {@link ParametricFactorGraph}.
 * 
 * Bayesian networks are directed graphical models parameterized by conditional
 * probability tables (CPTs). This class represents a set of Bayesian networks
 * with the same graph structure, but different CPTs. The direction of the edges
 * of in the graph structure is implicitly represented in the {@link CptFactor}s
 * contained in the {@code ParametricFactorGraph}. {@code ParametricFactorGraph}
 * actually allows a combination of directed and undirected factors; however,
 * the undirected factors must be constant (i.e., not parameterized).
 * 
 */
public class BayesNetBuilder {

  private FactorGraph bayesNet;
  private List<ParametricFactor<SufficientStatistics>> cptFactors;
  private VariableNumMap discreteVars;

  public BayesNetBuilder() {
    bayesNet = new FactorGraph();
    cptFactors = new ArrayList<ParametricFactor<SufficientStatistics>>();
    discreteVars = VariableNumMap.emptyMap();
  }

  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  public ParametricFactorGraph build() {
    return new ParametricFactorGraph(bayesNet, cptFactors);
  }

  /**
   * Add a variable to the bayes net.
   */
  public int addDiscreteVariable(String name, DiscreteVariable variable) {
    bayesNet = bayesNet.addVariable(name, variable);
    int varNum = bayesNet.getVariableIndex(name);
    discreteVars = discreteVars.addMapping(varNum, variable);
    return varNum;
  }

  /**
   * Gets the variables with the specified names.
   * 
   * @param names
   * @return
   */
  public VariableNumMap lookupVariables(Collection<String> names) {
    return bayesNet.lookupVariables(names);
  }

  /**
   * Add a factor to the Bayes net being constructed.
   * 
   * @param factor
   */
  public void addFactor(ParametricFactor<SufficientStatistics> factor) {
    cptFactors.add(factor);
  }

  public void addTableFactor(Factor tf) {
    bayesNet = bayesNet.addFactor(tf);
  }

  /**
   * Adds a new CptTableFactor that is created with its own (new) Cpt.
   */
  public CptTableFactor addCptFactorWithNewCpt(List<String> parentVariableNames,
      List<String> childVariableNames) {
    VariableNumMap parentVars = bayesNet.lookupVariables(parentVariableNames);
    VariableNumMap childVars = bayesNet.lookupVariables(childVariableNames);
    VariableNumMap allVars = parentVars.union(childVars);

    BiMap<Integer, Integer> cptVarNumMap = HashBiMap.create();
    for (Integer varNum : allVars.getVariableNums()) {
      cptVarNumMap.put(varNum, varNum);
    }

    CptTableFactor factor = new CptTableFactor(parentVars, childVars, cptVarNumMap);
    addFactor(factor);

    return factor;
  }

  /**
   * Add a new conditional probability factor embedding a context-free grammar.
   */
  public CptCfgFactor addCfgCptFactor(String parentVarName, String childVarName,
      Grammar grammar, CptProductionDistribution productionDist) {
    VariableNumMap parentVars = bayesNet.lookupVariables(Arrays.asList(new String[] { parentVarName }));
    VariableNumMap childVars = bayesNet.lookupVariables(Arrays.asList(new String[] { childVarName }));

    CptCfgFactor factor = new CptCfgFactor(parentVars, childVars,
        grammar, productionDist);
    addFactor(factor);
    return factor;
  }

  /**
   * Gets the basic factor graph which the bayes net under construction will use
   * for its variables, etc. Mostly used to expose methods of
   * {@code FactorGraph} which are useful during building.
   * 
   * @return
   */
  public FactorGraph getBaseFactorGraph() {
    return bayesNet;
  }

  /**
   * Be careful with this method.
   * 
   * @param factorGraph
   */
  public void setBaseFactorGraph(FactorGraph factorGraph) {
    bayesNet = factorGraph;
  }
}
