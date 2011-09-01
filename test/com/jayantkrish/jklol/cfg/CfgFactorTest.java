package com.jayantkrish.jklol.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.IncrementalEMTrainer;
import com.jayantkrish.jklol.util.Assignment;

/**
 * This is a regression test which tests learning grammar probabilities with hidden trees.
 */
public class CfgFactorTest extends TestCase {

	Grammar g;
	CptCfgFactor cfgFactor;
	CptTableFactor rootFactor;
	CptTableFactor var1Factor;
	ParametricFactorGraph bn;

	IncrementalEMTrainer trainer;
	List<Assignment> trainingData;

	private Production prod(String s) {
		return Production.getProduction(s);
	}

	private TerminalProduction term(String p, String c) {
		return new TerminalProduction(prod(p), prod(c));
	}

	private BinaryProduction bp(String p, String l, String r) {
		return new BinaryProduction(prod(p), prod(l), prod(r));
	}

	public void setUp() {

		g = new Grammar();

		g.addProductionRule(bp("A", "A", "A"));
		g.addProductionRule(bp("C", "A", "C"));
		g.addTerminal(term("A", "a"));
		g.addTerminal(term("A", "b"));
		g.addTerminal(term("A", "c"));
		g.addTerminal(term("C", "c"));

		BayesNetBuilder builder = new BayesNetBuilder();

		DiscreteVariable prodVar = new DiscreteVariable("Productions",
				Arrays.asList(new Production[] {prod("A"), prod("C")}));

		List<List<Production>> terminals = new ArrayList<List<Production>>();
		terminals.add(Arrays.asList(new Production[] {prod("a"), prod("c")}));
		terminals.add(Arrays.asList(new Production[] {prod("a"), prod("b")}));
		terminals.add(Arrays.asList(new Production[] {prod("a"), prod("b"), prod("c")}));

		DiscreteVariable terminalVar = new DiscreteVariable("terminals",
				terminals);

		DiscreteVariable otherVar = new DiscreteVariable("TF",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addDiscreteVariable("Var0", terminalVar);
		builder.addDiscreteVariable("Var1", prodVar);
		builder.addDiscreteVariable("Var2", otherVar);

		rootFactor = builder.addCptFactorWithNewCpt(Collections.<String>emptyList(), Arrays.asList(new String[] {"Var1"}));
		var1Factor = builder.addCptFactorWithNewCpt(Arrays.asList(new String[] {"Var1"}), 
				Arrays.asList(new String[] {"Var2"}));
		cfgFactor = builder.addCfgCptFactor("Var1", "Var0", g, new CptTableProductionDistribution(g));

		bn = builder.build();

		List<String> observedVarNames = Arrays.asList(new String[] {"Var0", "Var2"});
		trainingData = new ArrayList<Assignment>();
		Assignment a1 = bn.lookupVariables(observedVarNames).outcomeToAssignment(
		    Arrays.asList(new Object[] {terminals.get(0), "T"}));
		Assignment a2 = bn.lookupVariables(observedVarNames).outcomeToAssignment(
				Arrays.asList(new Object[] {terminals.get(1), "F"}));
		Assignment a3 = bn.lookupVariables(observedVarNames).outcomeToAssignment(
				Arrays.asList(new Object[] {terminals.get(2), "T"}));
		for (int i = 0; i < 3; i++) {
			trainingData.add(a1);
			trainingData.add(a2);
			trainingData.add(a3);
		}
		trainer = new IncrementalEMTrainer(10, new JunctionTree());
	}


	public void testTrain() {
	  SufficientStatistics initialParameters = bn.getNewSufficientStatistics();
	  initialParameters.increment(1.0);
		SufficientStatistics finalParameters = trainer.train(bn, initialParameters, trainingData);

		// This is a nice debug message.
		// for (CptFactor f : bn.getCptFactors()) {
    // System.out.println(f);
		// }
		
		FactorGraph factorGraph = bn.getFactorGraphFromParameters(finalParameters);
		Factor rootFactor = factorGraph.getFactor(0);
		Factor var1Factor = factorGraph.getFactor(1);
		CfgFactor cfgFactor = (CfgFactor) factorGraph.getFactor(2);
		
		Assignment rootA = bn.lookupVariables(Arrays.asList("Var1")).outcomeToAssignment(Arrays.asList(prod("A")));
		assertEquals(0.37, rootFactor.getUnnormalizedProbability(rootA), 0.05);
		Assignment rootC = bn.lookupVariables(Arrays.asList("Var1")).outcomeToAssignment(Arrays.asList(prod("C")));
    assertEquals(0.63, rootFactor.getUnnormalizedProbability(rootC), 0.05);
		
		Assignment rootAvar2F = bn.lookupVariables(Arrays.asList("Var1", "Var2")).outcomeToAssignment( 
		    Arrays.asList(prod("A"), "F")); 
		assertEquals(0.8, var1Factor.getUnnormalizedProbability(rootAvar2F), 0.05);
		Assignment rootCvar2T = bn.lookupVariables(Arrays.asList("Var1", "Var2")).outcomeToAssignment( 
		    Arrays.asList(prod("C"), "T")); 
    assertEquals(0.875, var1Factor.getUnnormalizedProbability(rootCvar2T), 0.05);

    ProductionDistribution prodDist = cfgFactor.getProductionDistribution();
    assertEquals(4.0 / 7.0, prodDist.getRuleProbability(bp("C", "A", "C")), 0.05);
    assertEquals(3.0 / 7.0, prodDist.getTerminalProbability(term("C", "c")), 0.05);
	}
}
