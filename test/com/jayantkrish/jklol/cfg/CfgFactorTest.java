import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.cfg.BinaryProduction;
import com.jayantkrish.jklol.cfg.CfgFactor;
import com.jayantkrish.jklol.cfg.CptTableProductionDistribution;
import com.jayantkrish.jklol.cfg.Grammar;
import com.jayantkrish.jklol.cfg.Production;
import com.jayantkrish.jklol.cfg.TerminalProduction;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.bayesnet.CptFactor;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.training.IncrementalEMTrainer;
import com.jayantkrish.jklol.util.Assignment;

/**
 * This is a regression test which tests learning grammar probabilities with hidden trees.
 */
public class CfgFactorTest extends TestCase {

	Grammar g;
	CfgFactor cfgFactor;
	BayesNet bn;

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

		CptTableFactor f0 = builder.addCptFactorWithNewCpt(Collections.EMPTY_LIST, Arrays.asList(new String[] {"Var1"}));
		CptTableFactor f1 = builder.addCptFactorWithNewCpt(Arrays.asList(new String[] {"Var1"}), 
				Arrays.asList(new String[] {"Var2"}));
		cfgFactor = builder.addCfgCptFactor("Var1", "Var0", g, new CptTableProductionDistribution(g));

		bn = builder.build();

		List<String> observedVarNames = Arrays.asList(new String[] {"Var0", "Var2"});
		trainingData = new ArrayList<Assignment>();
		Assignment a1 = bn.outcomeToAssignment(observedVarNames,
				Arrays.asList(new Object[] {terminals.get(0), "T"}));
		Assignment a2 = bn.outcomeToAssignment(observedVarNames,
				Arrays.asList(new Object[] {terminals.get(1), "F"}));
		Assignment a3 = bn.outcomeToAssignment(observedVarNames,
				Arrays.asList(new Object[] {terminals.get(2), "T"}));
		for (int i = 0; i < 3; i++) {
			trainingData.add(a1);
			trainingData.add(a2);
			trainingData.add(a3);
		}
		trainer = new IncrementalEMTrainer(10, 1.0, new JunctionTree());
	}


	public void testTrain() {
		trainer.train(bn, trainingData);

		for (CptFactor f : bn.getCptFactors()) {
			System.out.println(f);
		}
	}
}
