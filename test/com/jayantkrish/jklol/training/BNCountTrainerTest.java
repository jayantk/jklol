import com.jayantkrish.jklol.models.*;
import com.jayantkrish.jklol.training.BNCountTrainer;
import java.util.*;
import junit.framework.*;

public class BNCountTrainerTest extends TestCase {

    BayesNet bn;
    BNCountTrainer t;
    List<Assignment> trainingData;

    public void setUp() {
	bn = new BayesNet();

	Variable<String> tfVar = new Variable<String>("TrueFalse",
		Arrays.asList(new String[] {"T", "F"}));

	bn.addVariable("Var0", tfVar);
	bn.addVariable("Var1", tfVar);
	bn.addVariable("Var2", tfVar);

	CptTableFactor f0 = bn.addCptFactor(Collections.EMPTY_LIST, Arrays.asList(new String[] {"Var0"}));
	CptTableFactor f1 = bn.addCptFactor(Collections.EMPTY_LIST, Arrays.asList(new String[] {"Var1"}));
	CptTableFactor f2 = bn.addCptFactor(Arrays.asList(new String[] {"Var0", "Var1"}), 
		Arrays.asList(new String[] {"Var2"}));

	Cpt noNodeCpt = new Cpt(Collections.EMPTY_LIST, Arrays.asList(new Variable<?>[] {tfVar}));
	Cpt vCpt = new Cpt(Arrays.asList(new Variable<?>[] {tfVar, tfVar}), Arrays.asList(new Variable<?>[] {tfVar}));

	Map<Integer, Integer> nodeCptMap0 = new HashMap<Integer, Integer>();
	nodeCptMap0.put(0, 0);
	Map<Integer, Integer> nodeCptMap1 = new HashMap<Integer, Integer>();
	nodeCptMap1.put(1, 0);
	Map<Integer, Integer> vCptMap = new HashMap<Integer, Integer>();
	vCptMap.put(0, 0);
	vCptMap.put(1, 1);
	vCptMap.put(2, 2);

	f0.setCpt(noNodeCpt, nodeCptMap0);
	f1.setCpt(noNodeCpt, nodeCptMap1);
	f2.setCpt(vCpt, vCptMap);

	List<String> allVarNames = Arrays.asList(new String[] {"Var0", "Var1", "Var2"});

	trainingData = new ArrayList<Assignment>();
	Assignment a1 = bn.outcomeToAssignment(allVarNames,
		Arrays.asList(new String[] {"F", "T", "T"}));
	Assignment a2 = bn.outcomeToAssignment(allVarNames,
		Arrays.asList(new String[] {"T", "F", "F"}));
	Assignment a3 = bn.outcomeToAssignment(allVarNames,
		Arrays.asList(new String[] {"F", "F", "F"}));
	for (int i = 0; i < 3; i++) {
	    trainingData.add(a1);
	    trainingData.add(a2);
	    trainingData.add(a3);
	}
	t = new BNCountTrainer(1);
    }

    public void testTrain() {
	t.train(bn, trainingData);

	List<CptFactor> cptFactors = bn.getCptFactors();
	CptFactor m0 = cptFactors.get(0);
	assertEquals(7.0 / 20.0,
		m0.getUnnormalizedProbability(m0.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T"}))));

	assertEquals(13.0 / 20.0,
		m0.getUnnormalizedProbability(m0.getVars().outcomeToAssignment(Arrays.asList(new String[] {"F"}))));

	CptFactor m1 = cptFactors.get(1);
	assertEquals(7.0 / 20.0,
		m1.getUnnormalizedProbability(m1.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T"}))));

	assertEquals(13.0 / 20.0,
		m1.getUnnormalizedProbability(m1.getVars().outcomeToAssignment(Arrays.asList(new String[] {"F"}))));

	CptFactor m2 = cptFactors.get(2);
	
	assertEquals(0.8,
		m2.getUnnormalizedProbability(m2.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "F", "F"}))));
	assertEquals(0.2,
		m2.getUnnormalizedProbability(m2.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "F", "T"}))));
	assertEquals(0.8,
		m2.getUnnormalizedProbability(m2.getVars().outcomeToAssignment(Arrays.asList(new String[] {"F", "F", "F"}))));

	// Only smoothing applies to this conditional probability
	assertEquals(0.5,
		m2.getUnnormalizedProbability(m2.getVars().outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "T"}))));
    }

}
