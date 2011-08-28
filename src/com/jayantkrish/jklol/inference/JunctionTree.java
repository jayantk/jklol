package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.google.common.base.Supplier;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.SeparatorSet;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.training.StepwiseEMTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.HashMultimap;

/**
 * Implements the junction tree algorithm for computing exact marginal
 * distributions. Currently assumes that the provided factor graph already has a
 * tree structure, and will explode if this is not true.
 */
public class JunctionTree extends AbstractMarginalCalculator {

  public JunctionTree() {}

  /**
   * Gets a supplier which always returns a new JunctionTree instance. Useful
   * for running parallelized training algorithms such as
   * {@link StepwiseEMTrainer}.
   * 
   * @return
   */
  public static Supplier<MarginalCalculator> getSupplier() {
    return new Supplier<MarginalCalculator>() {
      @Override
      public MarginalCalculator get() {
        return new JunctionTree();
      }
    };
  }

  @Override
  public MarginalSet computeMarginals(FactorGraph factorGraph, Assignment assignment) {
    CliqueTree cliqueTree = new CliqueTree(factorGraph);
    cliqueTree.setEvidence(assignment);
    int rootFactorNum = runMessagePassing(cliqueTree, true);
    return cliqueTreeToMarginalSet(cliqueTree, rootFactorNum);
  }

  @Override
  public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph, Assignment assignment) {
    CliqueTree cliqueTree = new CliqueTree(factorGraph);
    cliqueTree.setEvidence(assignment);
    int rootFactorNum = runMessagePassing(cliqueTree, false);
    return cliqueTreeToMaxMarginalSet(cliqueTree, rootFactorNum);
  }

  /**
   * Runs the junction tree message-passing algorithm on {@code cliqueTree}. If
   * {@code useSumProduct == true}, then  .
   */
  private int runMessagePassing(CliqueTree cliqueTree, boolean useSumProduct) {
    // This performs both passes of message passing.
    boolean keepGoing = true;
    int lastFactorNum = 0;
    while (keepGoing) {
      keepGoing = false;
      for (int i = 0; i < cliqueTree.numFactors(); i++) {
        int factorNum = cliqueTree.getFactorEliminationOrder().get(i);
        Map<SeparatorSet, Factor> inboundMessages = cliqueTree.getInboundMessages(factorNum);
        Set<SeparatorSet> possibleOutboundMessages = cliqueTree.getFactor(factorNum).getComputableOutboundMessages(inboundMessages);

        // Pass any messages which we haven't already computed.
        Set<Integer> alreadyPassedMessages = cliqueTree.getOutboundFactors(factorNum);
        for (SeparatorSet possibleOutboundMessage : possibleOutboundMessages) {
          if (!alreadyPassedMessages.contains(possibleOutboundMessage.getEndFactor())) {
            passMessage(cliqueTree, possibleOutboundMessage.getStartFactor(), possibleOutboundMessage.getEndFactor(), useSumProduct);
            keepGoing = true;
          }
        }

        // Find the last factor to send any outbound messages; this is the root
        // node of the junction tree, which will be used to compute the
        // partition function of the graphical model.
        if (alreadyPassedMessages.size() == 0) {
          lastFactorNum = factorNum;
        }
      }
    }
    return lastFactorNum;
  }

  /*
   * Compute the message that gets passed from startFactor to destFactor.
   */
  private void passMessage(CliqueTree cliqueTree, int startFactor, int destFactor, boolean useSumProduct) {
    System.out.println(startFactor + ":" + cliqueTree.getFactor(startFactor).getVars() + " --> " 
        + destFactor + ":" + cliqueTree.getFactor(destFactor).getVars());
    
    VariableNumMap sharedVars = cliqueTree.getFactor(startFactor).getVars().intersection(cliqueTree.getFactor(destFactor).getVars());

    List<Factor> factorsToCombine = new ArrayList<Factor>();
    for (int adjacentFactorNum : cliqueTree.getNeighboringFactors(startFactor)) {
      if (adjacentFactorNum == destFactor) {
        continue;
      }
      factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, startFactor));
    }
    Factor productFactor = cliqueTree.getFactor(startFactor).product(factorsToCombine);
    Factor messageFactor = null;
    if (useSumProduct) {
      messageFactor = productFactor.marginalize(productFactor.getVars().removeAll(sharedVars).getVariableNums());
    } else {
      messageFactor = productFactor.maxMarginalize(productFactor.getVars().removeAll(sharedVars).getVariableNums());
    }

    cliqueTree.addMessage(startFactor, destFactor, messageFactor);
  }

  /**
   * Computes the marginal distribution over the {@code factorNum}'th factor in
   * {@code cliqueTree}. If {@code useSumProduct} is {@code true}, this computes
   * marginals; otherwise, it computes max-marginals. Requires that
   * {@code cliqueTree} contains all of the inbound messages to factor
   * {@code factorNum}.
   * 
   * @param cliqueTree
   * @param factorNum
   * @param useSumProduct
   * @return
   */
  private static Factor computeMarginal(CliqueTree cliqueTree, int factorNum, boolean useSumProduct) {
    List<Factor> factorsToCombine = Lists.newArrayList();
    for (int adjacentFactorNum : cliqueTree.getNeighboringFactors(factorNum)) {
      factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, factorNum));
    }

    return cliqueTree.getFactor(factorNum).product(factorsToCombine);
  }

  private static MarginalSet cliqueTreeToMarginalSet(CliqueTree cliqueTree, int rootFactorNum) {
    List<Factor> marginalFactors = Lists.newArrayList();
    for (int i = 0; i < cliqueTree.numFactors(); i++) {
      marginalFactors.add(computeMarginal(cliqueTree, i, true));
    }

    // Get the partition function from the last eliminated node.
    // TODO(jayantk): More configurable options for choosing the root
    // factor.
    Factor rootFactor = computeMarginal(cliqueTree, rootFactorNum, true);
    double partitionFunction = rootFactor.marginalize(rootFactor.getVars().getVariableNums()).getUnnormalizedProbability(Assignment.EMPTY);

    return new FactorMarginalSet(marginalFactors, partitionFunction);
  }

  /**
   * Retrieves max marginals from the given clique tree.
   * 
   * @param cliqueTree
   * @param rootFactorNum
   * @return
   */
  private static MaxMarginalSet cliqueTreeToMaxMarginalSet(CliqueTree cliqueTree, int rootFactorNum) {
    List<Factor> marginalFactors = Lists.newArrayList();
    for (int i = 0; i < cliqueTree.numFactors(); i++) {
      marginalFactors.add(computeMarginal(cliqueTree, i, false));
    }
    return new FactorMaxMarginalSet(marginalFactors);
  }

  private class CliqueTree {

    private List<Factor> cliqueFactors;
    private List<Factor> cliqueConditionalFactors;

    // These data structures represent the actual junction tree.
    private HashMultimap<Integer, Integer> factorEdges;
    private List<Map<Integer, SeparatorSet>> separatorSets;
    private List<Map<Integer, Factor>> messages;

    private HashMultimap<Integer, Integer> varCliqueFactorMap;
    private List<Integer> cliqueEliminationOrder;

    public CliqueTree(FactorGraph factorGraph) {
      cliqueFactors = new ArrayList<Factor>();
      cliqueConditionalFactors = new ArrayList<Factor>();

      factorEdges = new HashMultimap<Integer, Integer>();
      separatorSets = new ArrayList<Map<Integer, SeparatorSet>>();
      messages = new ArrayList<Map<Integer, Factor>>();
      
      List<Factor> factorGraphFactors = new ArrayList<Factor>(factorGraph.getFactors());
      
      // This code is going to change to make inference more efficient.
      Map<Factor, Integer> initialEliminationOrder = Maps.newHashMap();
      if (factorGraph.getInferenceHint() != null) {
        for (int i = 0; i < factorGraphFactors.size(); i++) {
          initialEliminationOrder.put(factorGraphFactors.get(i), 
              factorGraph.getInferenceHint().getFactorEliminationOrder()[i]);
        }
      } else {
        for (int i = 0; i < factorGraphFactors.size(); i++) {
          initialEliminationOrder.put(factorGraphFactors.get(i), i);
        }
      }
      
      // Store factors which contain each variable so that we can
      // eliminate factors that are subsets of others.

      Collections.sort(factorGraphFactors, new Comparator<Factor>() {
        public int compare(Factor f1, Factor f2) {
          return f2.getVars().getVariableNums().size() - f1.getVars().getVariableNums().size();
        }
      });
      Map<Factor, Integer> factorCliqueMap = new HashMap<Factor, Integer>();
      HashMultimap<Integer, Factor> varFactorMap = new HashMultimap<Integer, Factor>();
      varCliqueFactorMap = new HashMultimap<Integer, Integer>();
      BiMap<Integer, Integer> cliquePriorityMap = HashBiMap.create();
      for (Factor f : factorGraphFactors) {
        Set<Factor> mergeableFactors = new HashSet<Factor>(factorGraph.getFactors());
        for (Integer varNum : f.getVars().getVariableNums()) {
          mergeableFactors.retainAll(varFactorMap.get(varNum));
          varFactorMap.put(varNum, f);
        }

        if (mergeableFactors.size() > 0) {
          // Arbitrarily select a factor to merge this factor in to.
          Factor superset = mergeableFactors.iterator().next();
          int cliqueNum = factorCliqueMap.get(superset);

          cliqueFactors.set(cliqueNum, cliqueFactors.get(cliqueNum).product(f));
          cliquePriorityMap.put(cliqueNum, 
              Math.max(cliquePriorityMap.get(cliqueNum), initialEliminationOrder.get(f)));
          factorCliqueMap.put(f, cliqueNum);
        } else {
          int chosenNum = cliqueFactors.size();
          factorCliqueMap.put(f, chosenNum);
          cliqueFactors.add(f);
          cliquePriorityMap.put(chosenNum, initialEliminationOrder.get(f));
          messages.add(new HashMap<Integer, Factor>());

          for (Integer varNum : f.getVars().getVariableNums()) {
            varCliqueFactorMap.put(varNum, chosenNum);
          }
        }
      }

      for (int i = 0; i < cliqueFactors.size(); i++) {
        Factor c = cliqueFactors.get(i);
        for (Integer varNum : c.getVars().getVariableNums()) {
          factorEdges.putAll(i, varCliqueFactorMap.get(varNum));
          factorEdges.remove(i, i);
        }
      }

      for (int i = 0; i < cliqueFactors.size(); i++) {
        separatorSets.add(Maps.<Integer, SeparatorSet> newHashMap());
        for (Integer adjacentFactor : factorEdges.get(i)) {
          separatorSets.get(i).put(adjacentFactor, new SeparatorSet(i, adjacentFactor, cliqueFactors.get(i).getVars().intersection(cliqueFactors.get(adjacentFactor).getVars())));
        }
      }
      
      // Find the best elimination order.
      SortedMap<Integer, Integer> bestEliminationOrder = Maps.newTreeMap();
      bestEliminationOrder.putAll(cliquePriorityMap.inverse());
      cliqueEliminationOrder = new ArrayList<Integer>();
      for (Integer position : bestEliminationOrder.keySet()) {
        cliqueEliminationOrder.add(bestEliminationOrder.get(position));
      }
    }

    public int numFactors() {
      return cliqueFactors.size();
    }

    public Factor getFactor(int factorNum) {
      return cliqueConditionalFactors.get(factorNum);
    }

    public Set<Integer> getFactorIndicesWithVariable(int varNum) {
      return varCliqueFactorMap.get(varNum);
    }

    /**
     * Delete all passed messages and conditional evidence.
     */
    public void clear() {
      for (int i = 0; i < messages.size(); i++) {
        messages.get(i).clear();
      }
      cliqueConditionalFactors.clear();
    }

    /**
     * Condition on the passed assignments to variables.
     */
    public void setEvidence(Assignment assignment) {
      cliqueConditionalFactors.clear();
      for (int i = 0; i < cliqueFactors.size(); i++) {
        cliqueConditionalFactors.add(cliqueFactors.get(i).conditional(assignment));
      }
    }
    
    public List<Integer> getFactorEliminationOrder() {
      return cliqueEliminationOrder; 
    }

    public Set<Integer> getIncomingFactors(int factorNum) {
      Set<Integer> factorsWithMessages = new HashSet<Integer>();
      for (int neighbor : getNeighboringFactors(factorNum)) {
        if (messages.get(neighbor).containsKey(factorNum)) {
          factorsWithMessages.add(neighbor);
        }
      }
      return factorsWithMessages;
    }

    public Map<SeparatorSet, Factor> getInboundMessages(int factorNum) {
      Map<SeparatorSet, Factor> inboundMessages = Maps.newHashMap();
      for (int neighbor : getNeighboringFactors(factorNum)) {
        SeparatorSet separatorSet = separatorSets.get(factorNum).get(neighbor);
        if (messages.get(neighbor).containsKey(factorNum)) {
          inboundMessages.put(separatorSet, messages.get(neighbor).get(factorNum));
        } else {
          inboundMessages.put(separatorSet, null);
        }
      }
      return inboundMessages;
    }

    public Set<Integer> getNeighboringFactors(int factorNum) {
      return factorEdges.get(factorNum);
    }

    public Set<Integer> getOutboundFactors(int factorNum) {
      return messages.get(factorNum).keySet();
    }

    public Factor getMessage(int startFactor, int endFactor) {
      return messages.get(startFactor).get(endFactor);
    }

    public void addMessage(int startFactor, int endFactor, Factor message) {
      messages.get(startFactor).put(endFactor, message);
    }
  }
}