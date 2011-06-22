import com.jayantkrish.jklol.cfg.*;
import com.jayantkrish.jklol.models.*;
import com.jayantkrish.jklol.inference.*;
import com.jayantkrish.jklol.training.*;

import junit.framework.*;

import java.util.*;

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

	bn = new BayesNet();

	Variable<Production> prodVar = new Variable<Production>("Productions",
		Arrays.asList(new Production[] {prod("A"), prod("C")}));

	List<List<Production>> terminals = new ArrayList<List<Production>>();
	terminals.add(Arrays.asList(new Production[] {prod("a"), prod("c")}));
	terminals.add(Arrays.asList(new Production[] {prod("a"), prod("b")}));
	terminals.add(Arrays.asList(new Production[] {prod("a"), prod("b"), prod("c")}));

	Variable<List<Production>> terminalVar = new Variable<List<Production>>("terminals",
		terminals);

	Variable<String> otherVar = new Variable<String>("TF",
		Arrays.asList(new String[] {"T", "F"}));

	bn.addVariable("Var0", terminalVar);
	bn.addVariable("Var1", prodVar);
	bn.addVariable("Var2", otherVar);

	CptTableFactor f0 = bn.addCptFactorWithNewCpt(Collections.EMPTY_LIST, Arrays.asList(new String[] {"Var1"}));
	CptTableFactor f1 = bn.addCptFactorWithNewCpt(Arrays.asList(new String[] {"Var1"}), 
		Arrays.asList(new String[] {"Var2"}));
	cfgFactor = bn.addCfgCptFactor("Var1", "Var0", g, new CptTableProductionDistribution(g));

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
