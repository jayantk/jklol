package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.Pair;

/**
 * An HMMFactory dynamically constructs HMMs.
 */
public class HMMFactory<H, O> implements FactorGraphFactory<BayesNet, List<Pair<H, O>>> {

	private DiscreteVariable parent;
	private DiscreteVariable child;
	private Cpt transitionProbs;
	private Cpt emissionProbs;
	private Cpt initialProbs;

	public HMMFactory(DiscreteVariable parent, DiscreteVariable child, 
			Cpt transitionProbs, Cpt emissionProbs, Cpt initialProbs) {
		this.parent = parent;
		this.child = child;
		this.transitionProbs = transitionProbs;
		this.emissionProbs = emissionProbs;
		this.initialProbs = initialProbs;
	}

	public Pair<BayesNet, Assignment> instantiateFactorGraph(List<Pair<H,O>> sequence) {
		BayesNetBuilder bn = new BayesNetBuilder();

		String previousHiddenName = null;
		int previousHiddenId = -1;
		List<String> varNames = new ArrayList<String>();
		List<Object> varValues = new ArrayList<Object>();
		for (int i = 0; i < sequence.size(); i++) {
			String hiddenName = "Hidden" + i;
			String observedName = "Observable" + i;
			int hiddenId = bn.addDiscreteVariable(hiddenName, parent);
			int observedId = bn.addDiscreteVariable(observedName, child);
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
				List<String> emptyList = Collections.emptyList();
				obsFactor = bn.addCptFactor(emptyList, Collections.singletonList(hiddenName));
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

		BayesNet factorGraph = bn.build();
		return new Pair<BayesNet, Assignment>(factorGraph, 
				factorGraph.outcomeToAssignment(varNames, varValues));
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
