package com.jayantkrish.jklol.models;

import com.jayantkrish.jklol.util.*;
import java.util.*;

/**
 * An HMMFactory dynamically constructs HMMs.
 */
public class HMMFactory<H, O> implements BayesNetFactory<List<Pair<H, O>>> {

    private Variable parent;
    private Variable child;
    private Cpt transitionProbs;
    private Cpt emissionProbs;
    private Cpt initialProbs;

    public HMMFactory(Variable parent, Variable child, 
		      Cpt transitionProbs, Cpt emissionProbs, Cpt initialProbs) {
	this.parent = parent;
	this.child = child;
	this.transitionProbs = transitionProbs;
	this.emissionProbs = emissionProbs;
	this.initialProbs = initialProbs;
    }

    public Pair<BayesNet, Assignment> instantiateFactorGraph(List<Pair<H,O>> sequence) {
	BayesNet bn = new BayesNet();
	
	String previousHiddenName = null;
	int previousHiddenId = -1;
	List<String> varNames = new ArrayList<String>();
	List<Object> varValues = new ArrayList<Object>();
	for (int i = 0; i < sequence.size(); i++) {
	    String hiddenName = "Hidden" + i;
	    String observedName = "Observable" + i;
	    int hiddenId = bn.addVariable(hiddenName, parent);
	    int observedId = bn.addVariable(observedName, child);
	    varNames.add(hiddenName);
	    varNames.add(observedName);
	    varValues.add(sequence.get(i).getLeft());
	    varValues.add(sequence.get(i).getRight());
	    
	    CptTableFactor obsFactor = bn.addCptFactor(Collections.singletonList(hiddenName),
					       Collections.singletonList(observedName));
	    Map<Integer, Integer> cptVarNumMap = new HashMap<Integer, Integer>();
	    cptVarNumMap.put(hiddenId, 0);
	    cptVarNumMap.put(observedId, 1);
	    obsFactor.setCpt(emissionProbs, cptVarNumMap);

	    if (i == 0) {
		obsFactor = bn.addCptFactor(Collections.EMPTY_LIST, Collections.singletonList(hiddenName));
	        cptVarNumMap = new HashMap<Integer, Integer>();
		cptVarNumMap.put(hiddenId, 0);
		obsFactor.setCpt(initialProbs, cptVarNumMap);
	    } else {
		obsFactor = bn.addCptFactor(Collections.singletonList(previousHiddenName), Collections.singletonList(hiddenName));
		cptVarNumMap = new HashMap<Integer, Integer>();
		cptVarNumMap.put(previousHiddenId, 0);
		cptVarNumMap.put(hiddenId, 1);
		obsFactor.setCpt(transitionProbs, cptVarNumMap);
	    }
	    previousHiddenName = hiddenName;
	    previousHiddenId = hiddenId;
	}

	return new Pair<BayesNet, Assignment>(bn, bn.outcomeToAssignment(varNames, varValues));
    }

    public void addUniformSmoothing(double smoothingCounts) {
	transitionProbs.clearOutcomeCounts();
	transitionProbs.addUniformSmoothing(smoothingCounts);
	emissionProbs.clearOutcomeCounts();
	emissionProbs.addUniformSmoothing(smoothingCounts);
	initialProbs.clearOutcomeCounts();
	initialProbs.addUniformSmoothing(smoothingCounts);
    }

}