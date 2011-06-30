import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.models.bayesnet.Cpt;
import com.jayantkrish.jklol.models.bayesnet.CptFactor;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.training.BNCountTrainer;
import com.jayantkrish.jklol.util.Assignment;

public class BNCountTrainerTest extends TestCase {

	BayesNet bn;
	BNCountTrainer t;
	List<Assignment> trainingData;

	public void setUp() {
		BayesNetBuilder builder = new BayesNetBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addDiscreteVariable("Var0", tfVar);
		builder.addDiscreteVariable("Var1", tfVar);
		builder.addDiscreteVariable("Var2", tfVar);

		List<String> emptyStringList = Collections.emptyList();

		CptTableFactor f0 = builder.addCptFactor(emptyStringList, Arrays.asList(new String[] {"Var0"}));
		CptTableFactor f1 = builder.addCptFactor(emptyStringList, Arrays.asList(new String[] {"Var1"}));
		CptTableFactor f2 = builder.addCptFactor(Arrays.asList(new String[] {"Var0", "Var1"}), 
				Arrays.asList(new String[] {"Var2"}));

		List<DiscreteVariable> emptyVariableList = Collections.emptyList();
		Cpt noNodeCpt = new Cpt(emptyVariableList, Arrays.asList(new DiscreteVariable[] {tfVar}));
		Cpt vCpt = new Cpt(Arrays.asList(new DiscreteVariable[] {tfVar, tfVar}), Arrays.asList(new DiscreteVariable[] {tfVar}));

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
		bn = builder.build();

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
