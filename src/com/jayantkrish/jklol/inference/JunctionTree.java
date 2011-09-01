package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.google.common.base.Supplier;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
    CliqueTree cliqueTree = new CliqueTree(factorGraph, assignment);
    int rootFactorNum = runMessagePassing(cliqueTree, true);
    return cliqueTreeToMarginalSet(cliqueTree, rootFactorNum);
  }

  @Override
  public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph, Assignment assignment) {
    CliqueTree cliqueTree = new CliqueTree(factorGraph, assignment);
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
    System.out.println(startFactor + ":" + cliqueTree.getFactor(startFactor).getVars() + " (" + cliqueTree.getFactor(startFactor).size() + ") --> " 
          + destFactor + ":" + cliqueTree.getFactor(destFactor).getVars() + " (" + cliqueTree.getFactor(destFactor).size() + ")");
    
    VariableNumMap sharedVars = cliqueTree.getFactor(startFactor).getVars().intersection(cliqueTree.getFactor(destFactor).getVars());

    // Find the factors which have yet to be merged into the marginal distribution of factor, but are
    // necessary for computing the specified message.
    Set<Integer> factorIndicesToCombine = Sets.newHashSet(cliqueTree.getNeighboringFactors(startFactor));
    factorIndicesToCombine.removeAll(cliqueTree.getFactorsInMarginal(startFactor));
    
    // If this is the upstream round of message passing, we might not have received a 
    // message from destFactor yet. However, if we have received the message, we 
    // should include it in the product as it will increase sparsity and thereby   
    // improve efficiency.
    if (cliqueTree.getMessage(destFactor, startFactor) == null) {
      factorIndicesToCombine.remove(destFactor);
    }
    
    List<Factor> factorsToCombine = new ArrayList<Factor>();
    for (Integer adjacentFactorNum : factorIndicesToCombine) {
      factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, startFactor));
      System.out.println("  combining: " + adjacentFactorNum + ": " + cliqueTree.getMessage(adjacentFactorNum, startFactor).getVars()
          + " (" + cliqueTree.getMessage(adjacentFactorNum, startFactor).size() + ")"); 
    }
    
    // Update the marginal distribution of startFactor in the clique tree.
    Factor updatedMarginal = cliqueTree.getMarginal(startFactor).product(factorsToCombine);
    cliqueTree.setMarginal(startFactor, updatedMarginal);
    cliqueTree.addFactorsToMarginal(startFactor, factorIndicesToCombine);

    // The message from startFactor to destFactor is the marginal of productFactor, divided by the message from
    // destFactor to startFactor, if it exists.
    Factor messageFactor = null;
    if (useSumProduct) {
      messageFactor = updatedMarginal.marginalize(updatedMarginal.getVars().removeAll(sharedVars).getVariableNums());
    } else {
      messageFactor = updatedMarginal.maxMarginalize(updatedMarginal.getVars().removeAll(sharedVars).getVariableNums());
    }
    
    // Divide out the destFactor -> startFactor message if necessary.
    if (cliqueTree.getFactorsInMarginal(startFactor).contains(destFactor)) {
      messageFactor = messageFactor.product(cliqueTree.getMessage(destFactor, startFactor).inverse());
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
    
    Set<Integer> factorNumsToCombine = Sets.newHashSet(cliqueTree.getNeighboringFactors(factorNum));
    factorNumsToCombine.removeAll(cliqueTree.getFactorsInMarginal(factorNum));
    
    List<Factor> factorsToCombine = Lists.newArrayList();
    for (int adjacentFactorNum : factorNumsToCombine) {
      factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, factorNum));
    }
    
    Factor newMarginal = cliqueTree.getMarginal(factorNum).product(factorsToCombine);
    cliqueTree.setMarginal(factorNum, newMarginal);
    cliqueTree.addFactorsToMarginal(factorNum, factorNumsToCombine);
    
    return newMarginal;
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
    return new FactorMaxMarginalSet(FactorGraph.createFromFactors(marginalFactors));
  }

  private class CliqueTree {

    private List<Factor> cliqueFactors;
    
    // These data structures represent the actual junction tree.
    private HashMultimap<Integer, Integer> factorEdges;
    private List<Map<Integer, SeparatorSet>> separatorSets;
    private List<Map<Integer, Factor>> messages;
    
    // As message passing progresses, we will multiply together the factors necessary to compute marginals
    // on each node. marginals contains the current factor that is approaching the marginal, and 
    // factorsInMarginals tracks the factors whose messages have been combined into marginals.
    private List<Factor> marginals;
    private List<Set<Integer>> factorsInMarginals;

    private HashMultimap<Integer, Integer> varCliqueFactorMap;
    private List<Integer> cliqueEliminationOrder;

    public CliqueTree(FactorGraph factorGraph, Assignment conditionedOn) {
      cliqueFactors = new ArrayList<Factor>();

      factorEdges = new HashMultimap<Integer, Integer>();
      separatorSets = new ArrayList<Map<Integer, SeparatorSet>>();
      messages = new ArrayList<Map<Integer, Factor>>();
      
      List<Factor> factorGraphFactors = new ArrayList<Factor>();
      for (Factor factor : factorGraph.getFactors()) {
        factorGraphFactors.add(factor.conditional(conditionedOn));
      }
      
      // This code is going to change to make inference more efficient.
      Map<Factor, Integer> initialEliminationOrder = Maps.newHashMap();
      System.out.println("Initial elimination order: ");
      if (factorGraph.getInferenceHint() != null) {
        for (int i = 0; i < factorGraphFactors.size(); i++) {
          initialEliminationOrder.put(factorGraphFactors.get(i), 
              factorGraph.getInferenceHint().getFactorEliminationOrder()[i]);
          System.out.println("  " + factorGraph.getInferenceHint().getFactorEliminationOrder()[i]
              + ": " + factorGraphFactors.get(i).getVars());
        }
      } else {
        for (int i = 0; i < factorGraphFactors.size(); i++) {
          initialEliminationOrder.put(factorGraphFactors.get(i), i);
          System.out.println("  " + i + ": " + factorGraphFactors.get(i).getVars());
        }
      }
      
      // Store factors which contain e)ach variable so that we can
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
        Set<Factor> mergeableFactors = new HashSet<Factor>(factorGraphFactors);
        for (Integer varNum : f.getVars().getVariableNums()) {
          mergeableFactors.retainAll(varFactorMap.get(varNum));
          varFactorMap.put(varNum, f);
        }

        if (mergeableFactors.size() > 0) {
          // Choose the sparsest factor to merge this factor into.
          Iterator<Factor> mergeableIterator = mergeableFactors.iterator();
          Factor superset = mergeableIterator.next();
          while (mergeableIterator.hasNext()) {
            Factor next = mergeableIterator.next();
            if (next.size() < superset.size()) {
              superset = next;
            }
          }
          int cliqueNum = factorCliqueMap.get(superset);
          
          System.out.println("Merging " + f.getVars() + " into " + cliqueFactors.get(cliqueNum).getVars());

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
      System.out.println("Elimination Order:");
      for (Integer position : bestEliminationOrder.keySet()) {
        cliqueEliminationOrder.add(bestEliminationOrder.get(position));
        System.out.println("  " + cliqueEliminationOrder.size() + " " + cliqueFactors.get(bestEliminationOrder.get(position)).getVars());
      }
            
      marginals = Lists.newArrayList(cliqueFactors);
      factorsInMarginals = Lists.newArrayList();        
      for (int i = 0; i < marginals.size(); i++) {
        factorsInMarginals.add(Sets.<Integer>newHashSet());
      }
    }

    public int numFactors() {
      return cliqueFactors.size();
    }

    public Factor getFactor(int factorNum) {
      return cliqueFactors.get(factorNum);
    }

    public Set<Integer> getFactorIndicesWithVariable(int varNum) {
      return varCliqueFactorMap.get(varNum);
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
    
    public Factor getMarginal(int factorNum) {
      return marginals.get(factorNum);
    }
    
    public void setMarginal(int factorNum, Factor marginal) {
      marginals.set(factorNum, marginal);
    }
    
    public Set<Integer> getFactorsInMarginal(int factorNum) {
      return Collections.unmodifiableSet(factorsInMarginals.get(factorNum));
    }
    
    public void addFactorsToMarginal(int factorNum, Set<Integer> factorsToAdd) {
      factorsInMarginals.get(factorNum).addAll(factorsToAdd);
    }
  }
}