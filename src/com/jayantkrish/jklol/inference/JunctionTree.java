package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.SeparatorSet;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.LogFunctions;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Implementation of the junction tree algorithm for computing exact marginal
 * distributions. This class supports both marginals and max-marginals. This
 * algorithm is suitable for use in graphical models with low tree-width, for
 * example, tree structured graphs with small factors.
 * <p>
 * This implementation assumes that input factor graphs are easily simplified
 * into a junction tree. Easily simplified factor graphs are those where
 * variable elimination can be performed without introducing additional cliques
 * to the original model. Essentially all graphical models where inference is
 * tractable should fall into this class. If an input factor graph cannot be
 * simplified, the marginal computation will throw an exception.
 */
public class JunctionTree implements MarginalCalculator {
  private static final long serialVersionUID = 1L;

  private final boolean renormalize;
  private final PruningStrategy pruningStrategy;

  /**
   * Creates a new junction tree without renormalization.
   */
  public JunctionTree() {
    this.renormalize = false;
    this.pruningStrategy = null;
  }

  /**
   * Creates a new junction tree.
   * 
   * @param renormalize If {@code true}, renormalize marginals to probability
   * distributions throughout the message passing process. Renormalization will
   * prevent numerical issues (overflow) and preserve the original probability
   * distribution, but will not preserve the unnormalized weights of assignments.
   */
  public JunctionTree(boolean renormalize) {
    this.renormalize = renormalize;
    this.pruningStrategy = null;
  }

  public JunctionTree(boolean renormalize, PruningStrategy pruningStrategy) {
    this.renormalize = renormalize;
    this.pruningStrategy = pruningStrategy;
  }

  @Override
  public FactorMarginalSet computeMarginals(FactorGraph factorGraph) {
    // Efficiency overrides.
    if (factorGraph.getVariables().size() == 0) {
      // All variables in the factor graph have assigned values.
      // This returns the partition function, wrapped in a factor marginal set.
      return FactorMarginalSet.fromAssignment(factorGraph.getConditionedVariables(),
          factorGraph.getConditionedValues(), 
          factorGraph.getUnnormalizedLogProbability(Assignment.EMPTY));
    } else if (factorGraph.getFactors().size() == 1) {
      // Factor graph has only one factor, meaning the marginal distribution is 
      // already computed.
      Factor theFactor = Iterables.getOnlyElement(factorGraph.getFactors());
      return new FactorMarginalSet(factorGraph.getFactors(), theFactor.getTotalUnnormalizedLogProbability(),
          factorGraph.getConditionedVariables(), factorGraph.getConditionedValues());
    }
    
    // long time = System.nanoTime();
    CliqueTree cliqueTree = CliqueTree.fromHeuristicVariableElimination(factorGraph);
    // long delta = (System.nanoTime() - time) / 1000;
    // System.out.println("building clique tree: " + delta);
    
    // time = System.nanoTime();
    Set<Integer> rootFactorNums = runMessagePassing(cliqueTree, true);
    // delta = (System.nanoTime() - time) / 1000;
    // System.out.println("Running message passing: " + delta);

    // time = System.nanoTime();
    FactorMarginalSet marginals = cliqueTreeToMarginalSet(cliqueTree, rootFactorNums, factorGraph);
    // delta = (System.nanoTime() - time) / 1000;
    // System.out.println("marginals: " + delta);

    return marginals;
  }

  @Override
  public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph) {
    // Efficiency override -- all variables in the factor graph have assigned
    // values.
    if (factorGraph.getVariables().size() == 0) {
      return new FactorMaxMarginalSet(CliqueTree.fromHeuristicVariableElimination(new FactorGraph()),
          factorGraph.getConditionedValues());
    }

    LogFunction log = LogFunctions.getLogFunction();

    log.startTimer("inference/build_clique_tree");
    CliqueTree cliqueTree = CliqueTree.fromHeuristicVariableElimination(factorGraph);
    log.stopTimer("inference/build_clique_tree");

    log.startTimer("inference/message_passing");
    runMessagePassing(cliqueTree, false);
    log.stopTimer("inference/message_passing");

    log.startTimer("inference/build_max_marginals");
    MaxMarginalSet maxMarginals = cliqueTreeToMaxMarginalSet(cliqueTree, factorGraph);
    log.stopTimer("inference/build_max_marginals");
    return maxMarginals;
  }

  /**
   * Runs the junction tree message-passing algorithm on {@code cliqueTree}. If
   * {@code useSumProduct == true}, then uses sum-product. Otherwise uses
   * max-product.
   */
  private Set<Integer> runMessagePassing(CliqueTree cliqueTree, boolean useSumProduct) {
    Set<Integer> rootFactors = Sets.newHashSet();
    int numFactors = cliqueTree.numFactors();

    for (int i = 0; i < 2 * numFactors; i++) {
      // Perform both rounds of message passing in the same loop by
      // going up the factor elimination indexes, then back down.
      int factorNum = -1;
      if (i < numFactors) {
        factorNum = cliqueTree.getFactorEliminationOrder().get(i);
      } else {
        factorNum = cliqueTree.getFactorEliminationOrder().get((2 * numFactors - 1) - i);
      }
      Map<SeparatorSet, Factor> inboundMessages = cliqueTree.getInboundMessages(factorNum);
      Set<SeparatorSet> possibleOutboundMessages = cliqueTree.getFactor(factorNum).getComputableOutboundMessages(inboundMessages);

      // Pass any messages which we haven't already computed.
      Set<Integer> alreadyPassedMessages = cliqueTree.getOutboundFactors(factorNum);
      for (SeparatorSet possibleOutboundMessage : possibleOutboundMessages) {
        if (!alreadyPassedMessages.contains(possibleOutboundMessage.getEndFactor())) {
          passMessage(cliqueTree, possibleOutboundMessage.getStartFactor(), possibleOutboundMessage.getEndFactor(), useSumProduct);
        }
      }

      // Find any root nodes of the junction tree (which is really a junction
      // forest) by finding factors which have received all of their inbound
      // messages before passing any outbound messages. These root nodes are
      // used to compute the partition function of the graphical model.
      int numInboundFactors = 0;
      for (Factor inboundFactor : inboundMessages.values()) {
        if (inboundFactor != null) {
          numInboundFactors++;
        }
      }
      if (alreadyPassedMessages.size() == 0 &&
          numInboundFactors == cliqueTree.getNeighboringFactors(factorNum).size()) {
        rootFactors.add(factorNum);
      }
    }

    return rootFactors;
  }

  /*
   * Compute the message that gets passed from startFactor to destFactor.
   */
  private void passMessage(CliqueTree cliqueTree, int startFactor, int destFactor, boolean useSumProduct) {
    VariableNumMap sharedVars = cliqueTree.getFactor(startFactor).getVars().intersection(cliqueTree.getFactor(destFactor).getVars());

    // Find the factors which have yet to be merged into the marginal
    // distribution of factor, but are necessary for computing the 
    // specified message.
    Set<Integer> factorIndicesToCombine = Sets.newHashSet(cliqueTree.getNeighboringFactors(startFactor));
    factorIndicesToCombine.removeAll(cliqueTree.getFactorsInMarginal(startFactor));

    // If this is the upstream round of message passing, we might not have
    // received a message from destFactor yet. However, if we have received the 
    // message, we should include it in the product as it will increase sparsity 
    // and thereby improve efficiency.
    if (cliqueTree.getMessage(destFactor, startFactor) == null) {
      factorIndicesToCombine.remove(destFactor);
    }

    List<Factor> factorsToCombine = new ArrayList<Factor>();
    for (Integer adjacentFactorNum : factorIndicesToCombine) {
      factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, startFactor));
    }

    // Update the marginal distribution of startFactor in the clique tree.
    Factor updatedMarginal = cliqueTree.getMarginal(startFactor).product(factorsToCombine);
    if (cliqueTree.getOutboundFactors(startFactor).size() == 0) {
      // If this factor has yet to send any outbound messages, we can 
      // use it to renormalize the probability distribution to avoid
      // possible numerical overflow issues. Updating the marginal
      // at this point is equivalent to multiplying the original factor
      // by some constant value, which doesn't affect the probability
      // distribution.
      if (renormalize) {
        updatedMarginal = updatedMarginal.product(1.0 / updatedMarginal.getTotalUnnormalizedProbability());
      }
      
      // Can also prune this marginal if a pruning strategy was provided.
      if (pruningStrategy != null) {
        updatedMarginal = pruningStrategy.apply(updatedMarginal);
      }
    }
    cliqueTree.setMarginal(startFactor, updatedMarginal);
    cliqueTree.addFactorsToMarginal(startFactor, factorIndicesToCombine);

    // The message from startFactor to destFactor is the marginal of
    // productFactor, divided by the message from destFactor to
    // startFactor, if it exists.
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
      Factor message = cliqueTree.getMessage(adjacentFactorNum, factorNum);
      Preconditions.checkState(message != null, "Invalid message passing order! Trying to pass %s -> %s",
          adjacentFactorNum, factorNum);
      factorsToCombine.add(message);
    }

    Factor newMarginal = cliqueTree.getMarginal(factorNum).product(factorsToCombine);
    cliqueTree.setMarginal(factorNum, newMarginal);
    cliqueTree.addFactorsToMarginal(factorNum, factorNumsToCombine);

    return newMarginal;
  }

  private static FactorMarginalSet cliqueTreeToMarginalSet(CliqueTree cliqueTree,
      Set<Integer> rootFactorNums, FactorGraph originalFactorGraph) {
    List<Factor> marginalFactors = Lists.newArrayList();
    for (int i = 0; i < cliqueTree.numFactors(); i++) {
      marginalFactors.add(computeMarginal(cliqueTree, i, true));
    }

    // Get the partition function from the root nodes of the junction forest.
    double logPartitionFunction = 0.0;
    for (int rootFactorNum : rootFactorNums) {
      Factor rootFactor = marginalFactors.get(rootFactorNum);
      double totalProb = rootFactor.marginalize(rootFactor.getVars().getVariableNums())
          .getUnnormalizedLogProbability(Assignment.EMPTY);
      logPartitionFunction += totalProb;
    }

    if (logPartitionFunction == Double.NEGATIVE_INFINITY) {
      throw new ZeroProbabilityError();
    }

    return new FactorMarginalSet(marginalFactors, logPartitionFunction,
        originalFactorGraph.getConditionedVariables(), originalFactorGraph.getConditionedValues());
  }

  /**
   * Retrieves max marginals from the given clique tree.
   * 
   * @param cliqueTree
   * @param rootFactorNum
   * @return
   */
  private static MaxMarginalSet cliqueTreeToMaxMarginalSet(CliqueTree cliqueTree,
      FactorGraph originalFactorGraph) {
    for (int i = 0; i < cliqueTree.numFactors(); i++) {
      computeMarginal(cliqueTree, i, false);
    }
    return new FactorMaxMarginalSet(cliqueTree, originalFactorGraph.getConditionedValues());
  }
  
  /**
   * Clique tree data structure used to implement the junction tree
   * algorithm. Represents factors over cliques of variables in the graphical
   * model with edges (separator sets) between factors that share variables.
   */
  public static class CliqueTree {

    private List<Factor> cliqueFactors;

    // These data structures represent the actual junction tree.
    private HashMultimap<Integer, Integer> factorEdges;
    private List<Map<Integer, SeparatorSet>> separatorSets;
    private List<Map<Integer, Factor>> messages;

    // As message passing progresses, we will multiply together the factors
    // necessary to compute marginals on each node. marginals contains the
    // current factor that is approaching the marginal, and factorsInMarginals
    // tracks the factors whose messages have been combined into marginals.
    private List<Factor> marginals;
    private List<Set<Integer>> factorsInMarginals;

    private List<Integer> cliqueEliminationOrder;

    private CliqueTree(List<Factor> cliqueFactors, HashMultimap<Integer, Integer> factorEdges,
        List<Integer> cliqueEliminationOrder) {
      this.cliqueFactors = Preconditions.checkNotNull(cliqueFactors);
      this.factorEdges = Preconditions.checkNotNull(factorEdges);

      this.cliqueEliminationOrder = Preconditions.checkNotNull(cliqueEliminationOrder);

      // Initialize messages and separator sets along the
      // edges of the clique tree.
      this.separatorSets = new ArrayList<Map<Integer, SeparatorSet>>();
      this.messages = new ArrayList<Map<Integer, Factor>>();
      for (int i = 0; i < cliqueFactors.size(); i++) {
        separatorSets.add(Maps.<Integer, SeparatorSet> newHashMap());
        messages.add(Maps.<Integer, Factor> newHashMap());

        for (Integer adjacentFactor : factorEdges.get(i)) {
          separatorSets.get(i).put(adjacentFactor, new SeparatorSet(i, adjacentFactor,
              cliqueFactors.get(i).getVars().intersection(cliqueFactors.get(adjacentFactor).getVars())));
        }
      }

      marginals = Lists.newArrayList(cliqueFactors);
      factorsInMarginals = Lists.newArrayList();
      for (int i = 0; i < marginals.size(); i++) {
        factorsInMarginals.add(Sets.<Integer> newHashSet());
      }
    }

    public static CliqueTree fromHeuristicVariableElimination(FactorGraph factorGraph) {
      // Initialize cliqueFactors with minimal cliques from the factor graph.
      List<Factor> cliqueFactors = new ArrayList<Factor>(factorGraph.getMinimalFactors());
      HashMultimap<Integer, Integer> factorEdges = HashMultimap.create();

      // Store factors which contain each variable so that we can
      // perform variable elimination.
      HashMultimap<Integer, Factor> varFactorMap = HashMultimap.create();
      Map<Factor, Integer> factorIndexMap = Maps.newHashMap();
      int index = 0;
      for (Factor f : cliqueFactors) {
        for (int varNum : f.getVars().getVariableNumsArray()) {
          varFactorMap.put(varNum, f);
        }
        factorIndexMap.put(f, index);
        index++;
      }

      // Count the number of occurrences of each variable in factors to
      // quickly determine which variable to eliminate next.
      TreeMultimap<Integer, Integer> countsOfVars = TreeMultimap.create();
      for (Integer varNum : varFactorMap.keySet()) {
        countsOfVars.put(varFactorMap.get(varNum).size(), varNum);
      }

      Set<Factor> remainingFactors = Sets.newHashSet(cliqueFactors);
      SortedMap<Integer, Integer> possibleEliminationOrder = Maps.newTreeMap();
      int eliminationIndex = 0;
      while (remainingFactors.size() > 1) {
        // Each iteration eliminates one factor from the factor graph.
        Factor justEliminated = null;

        for (Integer varNum : countsOfVars.get(1)) {
          Preconditions.checkState(varFactorMap.get(varNum).size() == 1);
          justEliminated = tryEliminateFactor(Iterables.getOnlyElement(varFactorMap.get(varNum)),
              varFactorMap, factorIndexMap, countsOfVars, factorEdges);

          if (justEliminated != null) {
            remainingFactors.remove(justEliminated);
            possibleEliminationOrder.put(eliminationIndex, factorIndexMap.get(justEliminated));
            eliminationIndex++;
            break;
          }
        }

        if (justEliminated == null) {
          // Heuristic of eliminating only variables in a single factor
          // has failed. Pick a variable (that only occurs in a few factors)
          // eliminate it, potentially creating large factors in the
          // clique tree.
          Integer minCount = countsOfVars.keySet().first();
          int varToEliminate = Iterables.getFirst(countsOfVars.get(minCount), null);

          List<Factor> factorsContainingVar = Lists.newArrayList(varFactorMap.get(varToEliminate));
          VariableNumMap allVars = VariableNumMap.EMPTY;
          for (Factor factor : factorsContainingVar) {
            allVars = allVars.union(factor.getVars());
          }
          
          Preconditions.checkState(factorsContainingVar.size() != 0);
          
          Factor factorToMergeWith = null;
          if (minCount == 1) {
            // If there's only a single factor containing this variable, 
            // simply sum out this variable to create a smaller factor.
            allVars = allVars.removeAll(varToEliminate);
          }

          if (factorToMergeWith == null) {
            // Create a new factor containing all neighboring variables
            // and add it to the variable elimination data structures.
            factorToMergeWith = TableFactor.unity(allVars);
            int newFactorIndex = cliqueFactors.size();
            cliqueFactors.add(factorToMergeWith);
            remainingFactors.add(factorToMergeWith);
            factorIndexMap.put(factorToMergeWith, newFactorIndex);

            for (int varNum : factorToMergeWith.getVars().getVariableNumsArray()) {
              int oldCount = varFactorMap.get(varNum).size();
              countsOfVars.remove(oldCount, varNum);
              countsOfVars.put(oldCount + 1, varNum);
              varFactorMap.put(varNum, factorToMergeWith);
            }
          }

          // Eliminate every factor containing the variable.
          for (Factor factor : factorsContainingVar) {
            // Remove the factor from the map containing variable counts.
            for (int variableNum : factor.getVars().getVariableNumsArray()) {
              // First decrement the occurrence count of this variable.
              int count = varFactorMap.get(variableNum).size();
              countsOfVars.remove(count, variableNum);
              if (count > 1) {
                countsOfVars.put(count - 1, variableNum);
              }
              varFactorMap.remove(variableNum, factor);
            }
            
            int curFactorIndex = factorIndexMap.get(factor);
            int destFactorIndex = factorIndexMap.get(factorToMergeWith);
            factorEdges.put(curFactorIndex, destFactorIndex);
            factorEdges.put(destFactorIndex, curFactorIndex);

            justEliminated = factor;
            remainingFactors.remove(justEliminated);
            possibleEliminationOrder.put(eliminationIndex, curFactorIndex);
            eliminationIndex++;
          }
        }
        Preconditions.checkState(justEliminated != null,
            "Could not convert %s into a clique tree.", factorGraph);
      }
      if (remainingFactors.size() > 0) {
        possibleEliminationOrder.put(eliminationIndex, factorIndexMap.get(Iterables.getOnlyElement(remainingFactors)));
      }
      
      List<Integer> cliqueEliminationOrder = new ArrayList<Integer>();
      // System.out.println("Elimination Order:");
      for (int i = 0; i < cliqueFactors.size(); i++) {
        cliqueEliminationOrder.add(possibleEliminationOrder.get(i));
        // System.out.println("  " + cliqueEliminationOrder.size() + " " +
        // cliqueFactors.get(bestEliminationOrder.get(position)).getVars());
      }
      
      return new CliqueTree(cliqueFactors, factorEdges, cliqueEliminationOrder);
    }

    /*
     * Helper method for constructing the clique tree by eliminating a single factor from the input.
     */
    private static Factor tryEliminateFactor(Factor f, Multimap<Integer, Factor> varFactorMap,
        Map<Factor, Integer> factorIndexMap, TreeMultimap<Integer, Integer> countsOfVars,
        HashMultimap<Integer, Integer> factorEdges) {
      Set<Integer> variablesToEliminate = Sets.newHashSet();
      Collection<Integer> factorVariables = f.getVars().getVariableNums();
      Set<Factor> mergeableFactors = new HashSet<Factor>();
      for (Integer variableNum : factorVariables) {
        if (varFactorMap.get(variableNum).size() == 1) {
          // The factor f is the only factor containing variableNum,
          // so it can be eliminated.
          variablesToEliminate.add(variableNum);
        }
        mergeableFactors.addAll(varFactorMap.get(variableNum));
      }

      Set<Integer> variablesToRetain = Sets.newHashSet(factorVariables);
      variablesToRetain.removeAll(variablesToEliminate);

      // Merge f with a factor containing all of the variables
      // which are not being eliminated.
      mergeableFactors.remove(f);
      for (Integer variableNum : variablesToRetain) {
        mergeableFactors.retainAll(varFactorMap.get(variableNum));
      }

      // It's possible that variablesToRetain is divided amongst two factors,
      // which means this factor cannot currently be eliminated.
      if (mergeableFactors.size() == 0 && variablesToRetain.size() > 0) {
        return null;
      }

      Factor superset = null;
      if (variablesToRetain.size() == 0) {
        // Don't need to merge this with anything.
      } else {
        // Merge this factor with the sparsest factor among the valid choices.
        Iterator<Factor> mergeableIterator = mergeableFactors.iterator();
        superset = mergeableIterator.next();
        while (mergeableIterator.hasNext()) {
          Factor next = mergeableIterator.next();
          if (next.size() < superset.size()) {
            superset = next;
          }
        }
      }

      // Remove the factor from the map containing variable counts.
      for (int variableNum : f.getVars().getVariableNumsArray()) {
        // First decrement the occurrence count of this variable.
        int count = varFactorMap.get(variableNum).size();
        countsOfVars.remove(count, variableNum);
        if (count > 1) {
          countsOfVars.put(count - 1, variableNum);
        }
        varFactorMap.remove(variableNum, f);
      }

      // Add an undirected edge in the clique tree from f to superset.
      if (superset != null) {
        int curFactorIndex = factorIndexMap.get(f);
        int destFactorIndex = factorIndexMap.get(superset);
        factorEdges.put(curFactorIndex, destFactorIndex);
        factorEdges.put(destFactorIndex, curFactorIndex);
      }

      return f;
    }

    public int numFactors() {
      return cliqueFactors.size();
    }

    public Factor getFactor(int factorNum) {
      return cliqueFactors.get(factorNum);
    }

    public List<Integer> getFactorEliminationOrder() {
      return cliqueEliminationOrder;
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
      return Sets.newHashSet(messages.get(factorNum).keySet());
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
    
    public List<Factor> getMarginals() {
      return marginals;
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