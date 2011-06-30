package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.FactorMath;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.factors.Factor;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.HashMultimap;

/**
 * Implements the junction tree algorithm for computing exact marginal
 * distributions. Currently assumes that the provided factor graph already
 * has a tree structure, and will probably explode if this is not true.
 */ 
public class JunctionTree implements InferenceEngine {

	// Cache of factors representing marginal distributions of the
	// variables.
	private Map<Integer, Factor<?>> cachedMarginals;
	private CliqueTree cliqueTree;
	private boolean useSumProduct;

	public JunctionTree() {
		this.cachedMarginals = new HashMap<Integer, Factor<?>>();
		this.cliqueTree = null;
		useSumProduct = true;
	}

	public void setFactorGraph(FactorGraph f) {
		// TODO: this assumes the factor graph is already tree structured
		cliqueTree = new CliqueTree(f);
		cachedMarginals.clear();
	}

	/**
	 * Computes and caches marginals without conditioning on anything.
	 */ 
	public void computeMarginals() {
		computeMarginals(Assignment.EMPTY);
	}

	/**
	 * Computes and caches marginals, conditioning on any variable assignments provided in the
	 * argument.
	 */
	public void computeMarginals(Assignment assignment) {
		cliqueTree.clear();
		cachedMarginals.clear();

		cliqueTree.setEvidence(assignment);
		useSumProduct = true;
		runMessagePassing();
	}

	public void computeMaxMarginals() {
		computeMaxMarginals(Assignment.EMPTY);
	}

	public void computeMaxMarginals(Assignment assignment) {
		cliqueTree.clear();
		cachedMarginals.clear();

		cliqueTree.setEvidence(assignment);
		useSumProduct = false;
		runMessagePassing();
	}

	private void runMessagePassing() {
		// Upstream pass of Junction Tree message passing.
		boolean keepGoing = true;
		while (keepGoing) {
			keepGoing = false;
			for (int factorNum = 0; factorNum < cliqueTree.numFactors(); factorNum++) {
				Set<Integer> incomingFactors = cliqueTree.getIncomingFactors(factorNum);
				Set<Integer> adjacentFactors = cliqueTree.getNeighboringFactors(factorNum);
				Set<Integer> outboundFactors = cliqueTree.getOutboundFactors(factorNum);

				if (adjacentFactors.size() - incomingFactors.size() == 1 &&
						outboundFactors.size() == 0) {
					keepGoing = true;


					Set<Integer> tempCopy = new HashSet<Integer>(adjacentFactors);
					tempCopy.removeAll(incomingFactors);
					// There should only be one adjacent factor!
					assert tempCopy.size() == 1;
					for (Integer adjacent : tempCopy) {
						// System.out.println("up:" + factorNum + "-->" + adjacent);
						passMessage(factorNum, adjacent);
					}
				}
			}
		}

		// Downstream pass.
		keepGoing = true;
		while (keepGoing) {
			keepGoing = false;
			for (int factorNum = 0; factorNum < cliqueTree.numFactors(); factorNum++) {
				Set<Integer> incomingFactors = cliqueTree.getIncomingFactors(factorNum);
				Set<Integer> adjacentFactors = cliqueTree.getNeighboringFactors(factorNum);
				Set<Integer> outboundFactors = cliqueTree.getOutboundFactors(factorNum);

				if (adjacentFactors.size() - incomingFactors.size() == 0 &&
						outboundFactors.size() != adjacentFactors.size()) {
					keepGoing = true;

					// Get the adjacent factors we haven't messaged.
					Set<Integer> tempCopy = new HashSet<Integer>(adjacentFactors);
					tempCopy.removeAll(outboundFactors);
					for (Integer adjacent : tempCopy) {
						//System.out.println("down:" + factorNum + "-->" + adjacent);
						passMessage(factorNum, adjacent);
					}
				}
			}
		}
	}

	/*
	 * Compute the message that gets passed from startFactor to destFactor.
	 */
	private void passMessage(int startFactor, int destFactor) {
		VariableNumMap<?> sharedVars = cliqueTree.getFactor(startFactor).getVars().intersection(
				cliqueTree.getFactor(destFactor).getVars());

		List<Factor<?>> factorsToCombine = new ArrayList<Factor<?>>();
		for (int adjacentFactorNum : cliqueTree.getNeighboringFactors(startFactor)) {
			if (adjacentFactorNum == destFactor) {
				continue;
			}

			factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, startFactor));
		}
		factorsToCombine.add(cliqueTree.getFactor(startFactor));

		Factor<?> productFactor = FactorMath.product(factorsToCombine);
		Factor<?> messageFactor = null;
		if (useSumProduct) {
			messageFactor = productFactor.marginalize(sharedVars.removeAll(sharedVars).getVariableNums());
		} else {
			messageFactor = productFactor.maxMarginalize(sharedVars.removeAll(sharedVars).getVariableNums());		
		}
		//// System.out.println(factorsToCombine);
		//// System.out.println(startFactor + " --> " + destFactor + " : " + messageFactor);

		cliqueTree.addMessage(startFactor, destFactor, messageFactor);
	}


	/**
	 * Get a marginal distribution over a set of variables. You must select
	 * variables which are in the same clique of the clique tree.
	 */ 
	public Factor<?> getMarginal(List<Integer> varNums) {

		// This is all just trying to find a factor in the clique tree which includes all of the given variables.
		Set<Integer> relevantFactors = null;
		Set<Integer> varNumsToRetain = new HashSet<Integer>();
		for (Integer varNum : varNums) {
			varNumsToRetain.add(varNum);
			if (relevantFactors == null) {
				relevantFactors = new HashSet<Integer>(cliqueTree.getFactorIndicesWithVariable(varNum));
			} else {
				relevantFactors.retainAll(cliqueTree.getFactorIndicesWithVariable(varNum));
			}
		}

		if (relevantFactors.size() == 0) {
			throw new RuntimeException("Graph does not contain a factor with all variables: " + varNums);
		}

		//System.out.println("retaining: " + varNumsToRetain);

		// Pick a factor to use for the marginal
		int chosenFactorNum = relevantFactors.iterator().next();

		//System.out.println("choosing factor: " + chosenFactorNum);

		Factor<?> marginal = computeMarginal(chosenFactorNum);
		// Marginalize out any remaining variables...
		Set<Integer> allVarNums = new HashSet<Integer>(marginal.getVars().getVariableNums());
		allVarNums.removeAll(varNumsToRetain);
		if (useSumProduct) {
			return marginal.marginalize(allVarNums);
		} else {
			return marginal.maxMarginalize(allVarNums);
		}
	}


	/*
	 * For the given factor, take all of the inbound messages and 
	 * multiply them to get the final marginal distribution.
	 */
	private Factor<?> computeMarginal(int startFactor) {
		if (cachedMarginals.containsKey(startFactor)) {
			return cachedMarginals.get(startFactor);
		}

		VariableNumMap<?> vars = cliqueTree.getFactor(startFactor).getVars();
		//System.out.println(vars);

		List<Factor<?>> factorsToCombine = new ArrayList<Factor<?>>();
		for (int adjacentFactorNum : cliqueTree.getNeighboringFactors(startFactor)) {
			factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, startFactor));
		}
		factorsToCombine.add(cliqueTree.getFactor(startFactor));
		//System.out.println(factorsToCombine);

		Factor<?> productFactor = FactorMath.product(factorsToCombine);
		Factor<?> returnFactor = null;
		if (useSumProduct) {			
			returnFactor = productFactor.marginalize(productFactor.getVars().removeAll(vars).getVariableNums());
		} else {
			returnFactor = productFactor.maxMarginalize(productFactor.getVars().removeAll(vars).getVariableNums());
		}
		cachedMarginals.put(startFactor, returnFactor);
		return returnFactor;
	}

	private class CliqueTree {

		private List<Factor<?>> cliqueFactors;
		private List<Factor<?>> cliqueConditionalFactors;

		// These data structures represent the actual junction tree.
		private List<Map<Integer, Factor<?>>> messages;
		private HashMultimap<Integer, Integer> factorEdges;

		private HashMultimap<Integer, Integer> varCliqueFactorMap;

		public CliqueTree(FactorGraph factorGraph) {
			factorEdges = new HashMultimap<Integer, Integer>();
			messages = new ArrayList<Map<Integer, Factor<?>>>();
			cliqueFactors = new ArrayList<Factor<?>>();
			cliqueConditionalFactors = new ArrayList<Factor<?>>();

			// Store factors which contain each variable so that we can eliminate
			// factors that are subsets of others.
			List<Factor<?>> factorGraphFactors = new ArrayList<Factor<?>>(factorGraph.getFactors());
			Collections.sort(factorGraphFactors, new Comparator<Factor<?>>(){
				public int compare(Factor<?> f1, Factor<?> f2) {
					return f2.getVars().getVariableNums().size() - f1.getVars().getVariableNums().size();
				}
			});
			Map<Factor<?>, Integer> factorCliqueMap = new HashMap<Factor<?>, Integer>();
			HashMultimap<Integer, Factor<?>> varFactorMap = new HashMultimap<Integer, Factor<?>>();
			varCliqueFactorMap = new HashMultimap<Integer, Integer>();
			for (Factor<?> f : factorGraphFactors) {
				Set<Factor<?>> mergeableFactors = new HashSet<Factor<?>>(factorGraph.getFactors());
				for (Integer varNum : f.getVars().getVariableNums()) {
					mergeableFactors.retainAll(varFactorMap.get(varNum));
					varFactorMap.put(varNum, f);
				}

				if (mergeableFactors.size() > 0) {
					// Arbitrarily select a factor to merge this factor in to.
					Factor<?> superset = mergeableFactors.iterator().next();
					int cliqueNum = factorCliqueMap.get(superset);

					cliqueFactors.set(cliqueNum, FactorMath.product(cliqueFactors.get(cliqueNum), f));
					factorCliqueMap.put(f, cliqueNum);
				} else {
					int chosenNum = cliqueFactors.size();
					factorCliqueMap.put(f, chosenNum);
					cliqueFactors.add(f);
					messages.add(new HashMap<Integer, Factor<?>>());

					for (Integer varNum : f.getVars().getVariableNums()) {
						varCliqueFactorMap.put(varNum, chosenNum);
					}
				}
			}

			for (int i = 0; i < cliqueFactors.size(); i++) {
				Factor<?> c = cliqueFactors.get(i);
				for (Integer varNum : c.getVars().getVariableNums()) {
					factorEdges.putAll(i, varCliqueFactorMap.get(varNum));
					factorEdges.remove(i, i);
				}
			}
		}

		public int numFactors() {
			return cliqueFactors.size();
		}

		public Factor<?> getFactor(int factorNum) {
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

		public Factor<?> getMessage(int startFactor, int endFactor) {
			return messages.get(startFactor).get(endFactor);
		}

		public void addMessage(int startFactor, int endFactor, Factor<?> message) {
			messages.get(startFactor).put(endFactor, message);
		}
	}
}