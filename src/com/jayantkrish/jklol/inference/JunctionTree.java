package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.HashMultimap;

/**
 * Implements the junction tree algorithm for computing exact marginal
 * distributions. Currently assumes that the provided factor graph already has a
 * tree structure, and will probably explode if this is not true.
 */
public class JunctionTree extends AbstractInferenceEngine {

	// Cache of factors representing marginal distributions of the
	// variables.
	private Map<Integer, Factor> cachedMarginals;
	private CliqueTree cliqueTree;

	public JunctionTree() {
		this.cachedMarginals = new HashMap<Integer, Factor>();
		this.cliqueTree = null;
	}

	public void setFactorGraph(FactorGraph f) {
		// TODO: this assumes the factor graph is already tree structured
		cliqueTree = new CliqueTree(f);
		cachedMarginals.clear();
	}

	/**
	 * Computes and caches marginals, conditioning on any variable assignments
	 * provided in the argument.
	 */
	@Override
	public MarginalSet computeMarginals(Assignment assignment) {
		cliqueTree.clear();
		cachedMarginals.clear();

		cliqueTree.setEvidence(assignment);
		double partitionFunction = runMessagePassing(true);
		return cliqueTreeToMarginalSet(cliqueTree, partitionFunction, true);
	}

	@Override
	public MarginalSet computeMaxMarginals(Assignment assignment) {
		cliqueTree.clear();
		cachedMarginals.clear();

		cliqueTree.setEvidence(assignment);
		double partitionFunction = runMessagePassing(false);
		return cliqueTreeToMarginalSet(cliqueTree, partitionFunction, false);
	}

	private double runMessagePassing(boolean useSumProduct) {
		// Upstream pass of Junction Tree message passing.
		boolean keepGoing = true;
		int lastFactorNum = 0;
		while (keepGoing) {
			keepGoing = false;
			for (int factorNum = 0; factorNum < cliqueTree.numFactors(); factorNum++) {
				Set<Integer> incomingFactors = cliqueTree
						.getIncomingFactors(factorNum);
				Set<Integer> adjacentFactors = cliqueTree
						.getNeighboringFactors(factorNum);
				Set<Integer> outboundFactors = cliqueTree
						.getOutboundFactors(factorNum);

				if (adjacentFactors.size() - incomingFactors.size() == 1
						&& outboundFactors.size() == 0) {
					keepGoing = true;

					Set<Integer> tempCopy = new HashSet<Integer>(
							adjacentFactors);
					tempCopy.removeAll(incomingFactors);
					// There should be exactly one adjacent factor!
					Preconditions.checkState(tempCopy.size() == 1);
					for (Integer adjacent : tempCopy) {
						// System.out.println("up:" + factorNum + "-->" +
						// adjacent);
						passMessage(factorNum, adjacent, useSumProduct);
						lastFactorNum = adjacent;
					}
				}
			}
		}

		// Get the partition function from the last eliminated node.
		// TODO(jayantk): More configurable options for choosing the root
		// factor.
		Factor rootFactor = computeMarginal(cliqueTree, lastFactorNum, useSumProduct);
		double partitionFunction = rootFactor.marginalize(rootFactor.getVars().getVariableNums())
				.getUnnormalizedProbability(Assignment.EMPTY);

		// Downstream pass.
		keepGoing = true;
		while (keepGoing) {
			keepGoing = false;
			for (int factorNum = 0; factorNum < cliqueTree.numFactors(); factorNum++) {
				Set<Integer> incomingFactors = cliqueTree
						.getIncomingFactors(factorNum);
				Set<Integer> adjacentFactors = cliqueTree
						.getNeighboringFactors(factorNum);
				Set<Integer> outboundFactors = cliqueTree
						.getOutboundFactors(factorNum);

				if (adjacentFactors.size() - incomingFactors.size() == 0
						&& outboundFactors.size() != adjacentFactors.size()) {
					keepGoing = true;

					// Get the adjacent factors we haven't messaged.
					Set<Integer> tempCopy = new HashSet<Integer>(
							adjacentFactors);
					tempCopy.removeAll(outboundFactors);
					for (Integer adjacent : tempCopy) {
						// System.out.println("down:" + factorNum + "-->" +
						// adjacent);
						passMessage(factorNum, adjacent, useSumProduct);
					}
				}
			}
		}
		return partitionFunction;
	}

	/*
	 * Compute the message that gets passed from startFactor to destFactor.
	 */
	private void passMessage(int startFactor, int destFactor,
			boolean useSumProduct) {
		VariableNumMap sharedVars = cliqueTree.getFactor(startFactor).getVars()
				.intersection(cliqueTree.getFactor(destFactor).getVars());

		List<Factor> factorsToCombine = new ArrayList<Factor>();
		for (int adjacentFactorNum : cliqueTree
				.getNeighboringFactors(startFactor)) {
			if (adjacentFactorNum == destFactor) {
				continue;
			}

			factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum,
					startFactor));
		}

		Factor productFactor = cliqueTree.getFactor(startFactor).product(
				factorsToCombine);
		Factor messageFactor = null;
		if (useSumProduct) {
			messageFactor = productFactor.marginalize(sharedVars.removeAll(
					sharedVars).getVariableNums());
		} else {
			messageFactor = productFactor.maxMarginalize(sharedVars.removeAll(
					sharedVars).getVariableNums());
		}
		// // System.out.println(factorsToCombine);
		// // System.out.println(startFactor + " --> " + destFactor + " : " +
		// messageFactor);

		cliqueTree.addMessage(startFactor, destFactor, messageFactor);
	}

	/**
	 * Computes the marginal distribution over the {@code factorNum}'th factor
	 * in {@code cliqueTree}. If {@code useSumProduct} is {@code true}, this
	 * computes marginals; otherwise, it computes max-marginals. Requires that
	 * {@code cliqueTree} contains all of the inbound messages to factor {@code
	 * factorNum}.
	 * 
	 * @param cliqueTree
	 * @param factorNum
	 * @param useSumProduct
	 * @return
	 */
	private static Factor computeMarginal(CliqueTree cliqueTree, int factorNum,
			boolean useSumProduct) {
		List<Factor> factorsToCombine = Lists.newArrayList();
		for (int adjacentFactorNum : cliqueTree
				.getNeighboringFactors(factorNum)) {
			factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum,
					factorNum));
		}
		return cliqueTree.getFactor(factorNum).product(factorsToCombine);
	}

	private static MarginalSet cliqueTreeToMarginalSet(CliqueTree cliqueTree,
			double partitionFunction, boolean useSumProduct) {
		List<Factor> marginalFactors = Lists.newArrayList();
		for (int i = 0; i < cliqueTree.numFactors(); i++) {
			marginalFactors.add(computeMarginal(cliqueTree, i, useSumProduct));
		}
		return new FactorMarginalSet(marginalFactors, partitionFunction,
				useSumProduct);
	}

	private class CliqueTree {

		private List<Factor> cliqueFactors;
		private List<Factor> cliqueConditionalFactors;

		// These data structures represent the actual junction tree.
		private List<Map<Integer, Factor>> messages;
		private HashMultimap<Integer, Integer> factorEdges;

		private HashMultimap<Integer, Integer> varCliqueFactorMap;

		public CliqueTree(FactorGraph factorGraph) {
			factorEdges = new HashMultimap<Integer, Integer>();
			messages = new ArrayList<Map<Integer, Factor>>();
			cliqueFactors = new ArrayList<Factor>();
			cliqueConditionalFactors = new ArrayList<Factor>();

			// Store factors which contain each variable so that we can
			// eliminate
			// factors that are subsets of others.
			List<Factor> factorGraphFactors = new ArrayList<Factor>(factorGraph
					.getFactors());
			Collections.sort(factorGraphFactors, new Comparator<Factor>() {
				public int compare(Factor f1, Factor f2) {
					return f2.getVars().getVariableNums().size()
							- f1.getVars().getVariableNums().size();
				}
			});
			Map<Factor, Integer> factorCliqueMap = new HashMap<Factor, Integer>();
			HashMultimap<Integer, Factor> varFactorMap = new HashMultimap<Integer, Factor>();
			varCliqueFactorMap = new HashMultimap<Integer, Integer>();
			for (Factor f : factorGraphFactors) {
				Set<Factor> mergeableFactors = new HashSet<Factor>(factorGraph
						.getFactors());
				for (Integer varNum : f.getVars().getVariableNums()) {
					mergeableFactors.retainAll(varFactorMap.get(varNum));
					varFactorMap.put(varNum, f);
				}

				if (mergeableFactors.size() > 0) {
					// Arbitrarily select a factor to merge this factor in to.
					Factor superset = mergeableFactors.iterator().next();
					int cliqueNum = factorCliqueMap.get(superset);

					cliqueFactors.set(cliqueNum, cliqueFactors.get(cliqueNum)
							.product(f));
					factorCliqueMap.put(f, cliqueNum);
				} else {
					int chosenNum = cliqueFactors.size();
					factorCliqueMap.put(f, chosenNum);
					cliqueFactors.add(f);
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
				cliqueConditionalFactors.add(cliqueFactors.get(i).conditional(
						assignment));
			}
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