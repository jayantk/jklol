import com.jayantkrish.jklol.models.*;
import com.jayantkrish.jklol.inference.*;
import com.jayantkrish.jklol.training.IncrementalEMTrainer;
import com.jayantkrish.jklol.training.StepwiseEMTrainer;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import java.util.*;
import junit.framework.*;

public class EMTrainerTest extends TestCase {

    BayesNet bn;
    IncrementalEMTrainer t;
    List<Assignment> trainingData;
    Cpt actualNoNodeCpt;
    Cpt actualVCpt;
    Cpt expectedNoNodeCpt;
    Cpt expectedVCpt;

    Assignment a1,a2,a3;
    List<String> allVarNames;

    StepwiseEMTrainer s;

    String[][] testAssignments = new String[][] {{"F", "F", "T"}, {"T", "F", "F"}, 
						 {"T", "T", "T"}};
    double[] incrementalEmExpected = new double[] {(7.0 / 11.0) * (7.0 / 11.0)};
    double[] stepwiseEmExpected = new double[] {};
    double TOLERANCE = 0.05;

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

	actualNoNodeCpt = new Cpt(Collections.EMPTY_LIST, Arrays.asList(new Variable[] {tfVar}));
	actualVCpt = new Cpt(Arrays.asList(new Variable[] {tfVar, tfVar}), Arrays.asList(new Variable[] {tfVar}));

	Map<Integer, Integer> nodeCptMap0 = new HashMap<Integer, Integer>();
	nodeCptMap0.put(0, 0);
	Map<Integer, Integer> nodeCptMap1 = new HashMap<Integer, Integer>();
	nodeCptMap1.put(1, 0);
	Map<Integer, Integer> vCptMap = new HashMap<Integer, Integer>();
	vCptMap.put(0, 0);
	vCptMap.put(1, 1);
	vCptMap.put(2, 2);

	f0.setCpt(actualNoNodeCpt, nodeCptMap0);
	f1.setCpt(actualNoNodeCpt, nodeCptMap1);
	f2.setCpt(actualVCpt, vCptMap);

	allVarNames = Arrays.asList(new String[] {"Var0", "Var1", "Var2"});
	List<String> observedVarNames = Arrays.asList(new String[] {"Var0", "Var2"});

	trainingData = new ArrayList<Assignment>();
	a1 = bn.outcomeToAssignment(observedVarNames,
		Arrays.asList(new String[] {"F", "T"}));
	a2 = bn.outcomeToAssignment(observedVarNames,
		Arrays.asList(new String[] {"T", "F"}));
	a3 = bn.outcomeToAssignment(observedVarNames,
		Arrays.asList(new String[] {"F", "F"}));
	for (int i = 0; i < 3; i++) {
	    trainingData.add(a1);
	    trainingData.add(a2);
	    trainingData.add(a3);
	}

	t = new IncrementalEMTrainer(10, 1.0, new JunctionTree());
	s = new StepwiseEMTrainer(10, 3, 1.0, 0.9, new JunctionTree());
    }

    public void testIncrementalEM() {
	t.train(bn, trainingData);
       
	//Assignment a = bn.outcomeToAssignment(allVarNames, 
	//	Arrays.asList());

	for (CptFactor f : bn.getCptFactors()) {
	    System.out.println(f);
	}
    }

    public void testStepwiseEM() {
	s.train(bn, trainingData);

	for (CptFactor f : bn.getCptFactors()) {
	    System.out.println(f);
	}
    }
}
