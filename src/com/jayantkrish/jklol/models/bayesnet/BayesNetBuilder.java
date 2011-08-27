package com.jayantkrish.jklol.models.bayesnet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.cfg.CfgFactor;
import com.jayantkrish.jklol.cfg.CptProductionDistribution;
import com.jayantkrish.jklol.cfg.Grammar;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * A BayesNetBuilder provides methods for constructing a BayesNet
 */
public class BayesNetBuilder {

  private FactorGraph bayesNet;
  private List<CptFactor> cptFactors;
  private List<Factor> otherFactors;
  private VariableNumMap discreteVars;

  public BayesNetBuilder() {
    bayesNet = new FactorGraph();
    cptFactors = new ArrayList<CptFactor>();
    otherFactors = Lists.newArrayList();
    discreteVars = VariableNumMap.emptyMap();
  }

  /**
   * Get the factor graph being constructed with this builder.
   * 
   * @return
   */
  public BayesNet build() {
    return new BayesNet(bayesNet, cptFactors);
  }

  /**
   * Add a variable to the bayes net.
   */
  public int addDiscreteVariable(String name, DiscreteVariable variable) {
    int varNum = bayesNet.addVariable(name, variable);
    discreteVars = discreteVars.addMapping(varNum, variable);
    return varNum;
  }

  /**
   * Add a factor to the Bayes net being constructed.
   * 
   * @param factor
   */
  public void addFactor(CptFactor factor) {
    cptFactors.add(factor);
    bayesNet.addFactor(factor);
  }

  public TableFactor addNewTableFactor(List<String> variableNames) {
    TableFactor tf = new TableFactor(bayesNet.lookupVariables(variableNames));
    otherFactors.add(tf);
    bayesNet.addFactor(tf);
    return tf;
  }

  /**
   * Adds a new CptTableFactor that is created with its own (new) Cpt.
   */
  public CptTableFactor addCptFactorWithNewCpt(List<String> parentVariableNames,
      List<String> childVariableNames) {
    VariableNumMap parentVars = bayesNet.lookupVariables(parentVariableNames);
    VariableNumMap childVars = bayesNet.lookupVariables(childVariableNames);
    VariableNumMap allVars = parentVars.union(childVars);
    
    Map<Integer, Integer> cptVarNumMap = Maps.newHashMap();
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
  public CfgFactor addCfgCptFactor(String parentVarName, String childVarName,
      Grammar grammar, CptProductionDistribution productionDist) {
    VariableNumMap parentVars = bayesNet.lookupVariables(Arrays.asList(new String[] { parentVarName }));
    VariableNumMap childVars = bayesNet.lookupVariables(Arrays.asList(new String[] { childVarName }));

    Variable parent = parentVars.getVariables().get(0);
    Variable child = parentVars.getVariables().get(0);

    // The passed-in strings must be identifiers of DiscreteVariables in the
    // graphical model.
    Preconditions.checkArgument(parent instanceof DiscreteVariable);
    Preconditions.checkArgument(child instanceof DiscreteVariable);

    CfgFactor factor = new CfgFactor((DiscreteVariable) parent,
        (DiscreteVariable) child, parentVars.getVariableNums().get(0),
        childVars.getVariableNums().get(0), grammar, productionDist);

    addFactor(factor);
    return factor;
  }
}