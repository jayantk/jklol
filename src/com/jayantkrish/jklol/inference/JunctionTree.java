package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.util.*;
import com.jayantkrish.jklol.models.*;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Implements the junction tree algorithm for computing exact marginal
 * distributions. Currently assumes that the provided factor graph already
 * has a tree structure, and will probably explode if this is not true.
 */ 
public class JunctionTree implements InferenceEngine {

    private FactorGraph factorGraph;
    // Cache of factors representing marginal distributions of the
    // variables.
    private Map<Integer, Factor> cachedMarginals;
    private CliqueTree cliqueTree;
    private boolean useSumProduct;

    private int[] upstreamOrder;

    public JunctionTree() {
	this.factorGraph = null;
	this.cachedMarginals = new HashMap<Integer, Factor>();
	this.cliqueTree = null;
	useSumProduct = true;
	upstreamOrder = null;
    }

    /**
     * upstreamOrder defines the order in which cliques (numbered by clique tree number) are 
     * traversed during upstream message passing.
     */
    public JunctionTree(int[] upstreamOrder) {
	this.factorGraph = null;
	this.cachedMarginals = new HashMap<Integer, Factor>();
	this.cliqueTree = null;
	useSumProduct = true;
	this.upstreamOrder = upstreamOrder;
    }


    public void setFactorGraph(FactorGraph f) {
	factorGraph = f;

	if (upstreamOrder == null) {
	    upstreamOrder = new int[f.numFactors()];
	    for (int i = 0; i < upstreamOrder.length; i++) {
		upstreamOrder[i] = i;
	    }
	}

	// TODO: this assumes the factor graph is already tree structured
	cliqueTree = new CliqueTree(f, upstreamOrder);
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
	    for (int factorNum : cliqueTree.getEliminationOrder()) {
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
			System.out.println("up:" + factorNum + "-->" + adjacent);
			passUpMessage(factorNum, adjacent);
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
			System.out.println("down:" + factorNum + "-->" + adjacent);
			passDownMessage(factorNum, adjacent);
		    }
		}
	    }
	}
    }

    /*
     * Compute the message that gets passed from startFactor to destFactor on the upward pass of
     * junction tree.
     */
    private void passUpMessage(int startFactor, int destFactor) {
	Set<Integer> sharedVars = new HashSet<Integer>();
	sharedVars.addAll(cliqueTree.getFactor(startFactor).getVarNums());
	sharedVars.retainAll(cliqueTree.getFactor(destFactor).getVarNums());

	List<Factor> factorsToCombine = new ArrayList<Factor>();
	for (int adjacentFactorNum : cliqueTree.getNeighboringFactors(startFactor)) {
	    if (adjacentFactorNum == destFactor) {
		continue;
	    }

	    factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, startFactor));
	}
	Factor messageFactor = null;
	if (useSumProduct) {
	    messageFactor = cliqueTree.getFactor(startFactor).sumProduct(factorsToCombine, sharedVars);
	} else {
	    messageFactor = cliqueTree.getFactor(startFactor).maxProduct(factorsToCombine, sharedVars);
	}
	//// System.out.println(factorsToCombine);
	//// System.out.println(startFactor + " --> " + destFactor + " : " + messageFactor);

	cliqueTree.addMessage(startFactor, destFactor, messageFactor);
    }

    /*
     * Compute the message that gets passed from startFactor to destFactor on the downward pass.
     */
    private void passDownMessage(int startFactor, int destFactor) {
	Set<Integer> sharedVars = new HashSet<Integer>();
	sharedVars.addAll(cliqueTree.getFactor(startFactor).getVarNums());
	sharedVars.retainAll(cliqueTree.getFactor(destFactor).getVarNums());
	Set<Integer> toEliminate = new HashSet<Integer>(cliqueTree.getFactor(startFactor).getVarNums());
	toEliminate.removeAll(sharedVars);

	Factor marginal = computeMarginal(startFactor);
	Factor toDivide = cliqueTree.getMessage(destFactor, startFactor);

	Factor messageFactor = null;
	if (useSumProduct) {
	    messageFactor = marginal.marginalize(toEliminate);
	} else {
	    messageFactor = marginal.maxMarginalize(toEliminate);
	}
	messageFactor = messageFactor.divide(toDivide);
	//// System.out.println(factorsToCombine);
	//// System.out.println(startFactor + " --> " + destFactor + " : " + messageFactor);

	cliqueTree.addMessage(startFactor, destFactor, messageFactor);
    }



    /**
     * Get a marginal distribution over a set of variables. You must select
     * variables which are in the same clique of the clique tree.
     */ 
    public Factor getMarginal(List<Integer> varNums) {

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

	Factor marginal = computeMarginal(chosenFactorNum);
	// Marginalize out any remaining variables...
	Set<Integer> allVarNums = new HashSet<Integer>(marginal.getVarNums());
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
    private Factor computeMarginal(int startFactor) {
	if (cachedMarginals.containsKey(startFactor)) {
	    return cachedMarginals.get(startFactor);
	}
	
	List<Integer> vars = cliqueTree.getFactor(startFactor).getVarNums();
	//System.out.println(vars);

	List<Factor> factorsToCombine = new ArrayList<Factor>();
	for (int adjacentFactorNum : cliqueTree.getNeighboringFactors(startFactor)) {
	    factorsToCombine.add(cliqueTree.getMessage(adjacentFactorNum, startFactor));
	}

	//System.out.println(factorsToCombine);
	
	Factor returnFactor = null;
	if (useSumProduct) {
	    returnFactor = cliqueTree.getFactor(startFactor).sumProduct(factorsToCombine, vars);
	} else {
	    returnFactor = cliqueTree.getFactor(startFactor).maxProduct(factorsToCombine, vars);
	}
	cachedMarginals.put(startFactor, returnFactor);
	return returnFactor;
    }

    private class CliqueTree {

	private List<Integer> eliminationOrder;
	private List<Factor> cliqueFactors;
	private List<Factor> cliqueConditionalFactors;

	// These data structures represent the actual junction tree.
	private List<Map<Integer, Factor>> messages;
	private HashMultimap<Integer, Integer> factorEdges;

	private HashMultimap<Integer, Integer> varCliqueFactorMap;

	public CliqueTree(FactorGraph factorGraph, int[] factorEliminationOrder) {
	    factorEdges = new HashMultimap<Integer, Integer>();
	    messages = new ArrayList<Map<Integer, Factor>>();
	    cliqueFactors = new ArrayList<Factor>();
	    eliminationOrder = new ArrayList<Integer>();
	    cliqueConditionalFactors = new ArrayList<Factor>();

	    // Store factors which contain each variable so that we can eliminate
	    // factors that are subsets of others.
	    List<Factor> factorGraphFactors = new ArrayList<Factor>(factorGraph.getFactors());
	    Collections.sort(factorGraphFactors, new Comparator<Factor>(){
			public int compare(Factor f1, Factor f2) {
			    return f2.getVarNums().size() - f1.getVarNums().size();
			}
		    });
	    Map<Factor, Integer> factorCliqueMap = new HashMap<Factor, Integer>();
	    HashMultimap<Integer, Factor> varFactorMap = new HashMultimap<Integer, Factor>();
	    varCliqueFactorMap = new HashMultimap<Integer, Integer>();
	    for (Factor f : factorGraphFactors) {
		Set<Factor> mergeableFactors = new HashSet<Factor>(factorGraph.getFactors());
		for (Integer varNum : f.getVarNums()) {
		    mergeableFactors.retainAll(varFactorMap.get(varNum));
		    varFactorMap.put(varNum, f);
		}

		if (mergeableFactors.size() > 0) {
		    // Arbitrarily select a factor to merge this factor in to.
		    Factor superset = mergeableFactors.iterator().next();
		    int cliqueNum = factorCliqueMap.get(superset);
		    cliqueFactors.set(cliqueNum, cliqueFactors.get(cliqueNum).product(f));
		    eliminationOrder.set(cliqueNum, Math.max(eliminationOrder.get(cliqueNum), 
				    factorEliminationOrder[factorGraph.getFactorIndex(f)]));
		    factorCliqueMap.put(f, cliqueNum);
		} else {
		    int chosenNum = cliqueFactors.size();
		    factorCliqueMap.put(f, chosenNum);
		    cliqueFactors.add(f);
		    eliminationOrder.add(factorEliminationOrder[factorGraph.getFactorIndex(f)]);
		    messages.add(new HashMap<Integer, Factor>());

		    for (Integer varNum : f.getVarNums()) {
			varCliqueFactorMap.put(varNum, chosenNum);
		    }
		}
	    }

	    for (int i = 0; i < cliqueFactors.size(); i++) {
		Factor c = cliqueFactors.get(i);
		for (Integer varNum : c.getVarNums()) {
		    factorEdges.putAll(i, varCliqueFactorMap.get(varNum));
		    factorEdges.remove(i, i);
		}
	    }

	    List<Integer> tempElimOrder = new ArrayList<Integer>();
	    for (int i = 0; i < cliqueFactors.size(); i++) {
		int ind = 0;
		for (int j = 0; j < cliqueFactors.size(); j++) {
		    if (eliminationOrder.get(j) < eliminationOrder.get(i)) {
			ind++;
		    }
		}
		tempElimOrder.add(ind);
	    }
	    eliminationOrder = tempElimOrder;
	}

	public int numFactors() {
	    return cliqueFactors.size();
	}

	public List<Integer> getEliminationOrder() {
	    return eliminationOrder;
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