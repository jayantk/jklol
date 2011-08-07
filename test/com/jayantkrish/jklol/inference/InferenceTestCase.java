import java.util.Arrays;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.util.Assignment;

/**
 * InferenceTestCase wraps a graphical model and series of tests for inference algorithms
 * which use that model.
 * @author jayant
 *
 */
public class InferenceTestCase {

	private static final Variable threeValueVar = new DiscreteVariable("Three values",
			Arrays.asList(new String[] {"T", "F", "U"}));
	private static final Variable trueFalseVar = new DiscreteVariable("True/False",
			Arrays.asList(new String[] {"T", "F"}));

	/**
	 * A factor graph with several discrete variables and lots of zero weights.
	 * The factor graph shape is already a clique tree.
	 * @return
	 */
	public static FactorGraph basicFactorGraph() {
		FactorGraph factorGraph = new FactorGraph();

		DiscreteVariable otherVar = new DiscreteVariable("Two values",
				Arrays.asList(new String[] {"foo", "bar"}));

		factorGraph.addVariable("Var0", threeValueVar);
		factorGraph.addVariable("Var1", otherVar);
		factorGraph.addVariable("Var2", threeValueVar);
		factorGraph.addVariable("Var3", threeValueVar);
		factorGraph.addVariable("Var4", threeValueVar);

		TableFactor factor1 = factorGraph.addTableFactor(Arrays.asList(new String[] {"Var0", "Var2", "Var3"}));
		factor1.setWeightList(Arrays.asList(new String[] {"T", "T", "T"}), 1.0);
		factor1.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 1.0);
		factor1.setWeightList(Arrays.asList(new String[] {"U", "F", "F"}), 2.0);

		TableFactor factor2 = factorGraph.addTableFactor(Arrays.asList(new String[] {"Var2", "Var1"}));
		factor2.setWeightList(Arrays.asList(new String[] {"foo", "F"}), 2.0);
		factor2.setWeightList(Arrays.asList(new String[] {"foo", "T"}), 3.0);
		factor2.setWeightList(Arrays.asList(new String[] {"bar", "T"}), 2.0);
		factor2.setWeightList(Arrays.asList(new String[] {"bar", "F"}), 1.0);

		TableFactor factor3 = factorGraph.addTableFactor(Arrays.asList(new String[] {"Var3", "Var4"}));
		factor3.setWeightList(Arrays.asList(new String[] {"F", "U"}), 2.0);
		factor3.setWeightList(Arrays.asList(new String[] {"T", "U"}), 2.0);
		factor3.setWeightList(Arrays.asList(new String[] {"T", "F"}), 3.0);
		
		return factorGraph;
	}
	
	public static MarginalTestCase testBasicUnconditional() {
		MarginalTestCase testCase = new MarginalTestCase(basicFactorGraph(), Assignment.EMPTY, false);
		// Test the marginal on variable 1
		testCase.addTest(27.0 / 43.0, new Integer[] {1}, "foo");
		testCase.addTest(16.0 / 43.0, new Integer[] {1}, "bar");
		// Test the marginal on variables 0, 2
		testCase.addTest(25.0 / 43.0, new Integer[] {0,2}, "T", "T");
		testCase.addTest(6.0 / 43.0, new Integer[] {0,2}, "T", "F");
		testCase.addTest(12.0 / 43.0, new Integer[] {0,2}, "U", "F");
		testCase.addTest(0.0, new Integer[] {0,2}, "U", "U");
		
		return testCase;
	}
	
	public static MarginalTestCase testBasicConditional() {
		FactorGraph f = basicFactorGraph();
		Assignment conditional = f.outcomeToAssignment(Arrays.asList(new String[]{"Var2"}),
				Arrays.asList(new String[]{"F"})); 
		MarginalTestCase testCase = new MarginalTestCase(f, conditional, false);

		testCase.addTest(12.0 / 18.0, new Integer[] {1}, "foo");
		testCase.addTest(6.0 / 18.0, new Integer[] {1}, "bar");

		testCase.addTest(1.0, new Integer[] {3, 4}, "F", "U");
		testCase.addTest(0.0, new Integer[] {3, 4}, "T", "F");

		testCase.addTest(0.0, new Integer[] {0, 2, 3}, "T", "T", "T");
		testCase.addTest(6.0 / 18.0, new Integer[] {0, 2, 3}, "T", "F", "F");
		testCase.addTest(12.0 / 18.0, new Integer[] {0, 2, 3}, "U", "F", "F");

		return testCase;
	}
	
	public static MarginalTestCase testBasicMaxMarginals() {
		MarginalTestCase testCase = new MarginalTestCase(basicFactorGraph(), Assignment.EMPTY, false);		

		testCase.addTest(6.0 / 18.0, new Integer[] {1}, "bar");
		testCase.addTest(9.0 / 18.0, new Integer[] {1}, "foo");

		testCase.addTest(9.0 / 18.0, new Integer[] {0, 2}, "T", "T");
		testCase.addTest(4.0 / 18.0, new Integer[] {0, 2}, "T", "F");
		testCase.addTest(8.0 / 18.0, new Integer[] {0, 2}, "U", "F");
		testCase.addTest(0.0 / 18.0, new Integer[] {0, 2}, "U", "U");
		
		return testCase;
	}

	/**
	 * A factor graph with several discrete variables and lots of zero weights.
	 * The factor graph shape is not a minimal clique tree, but must be made into
	 * a clique tree. 
	 * @return
	 */
	public static FactorGraph nonCliqueTreeFactorGraph() {
		FactorGraph factorGraph = new FactorGraph();

		factorGraph.addVariable("Var0", trueFalseVar);
		factorGraph.addVariable("Var1", trueFalseVar);
		factorGraph.addVariable("Var2", trueFalseVar);

		TableFactor tf = factorGraph.addTableFactor(Arrays.asList(new String[] {"Var0", "Var1"}));
		tf.setWeightList(Arrays.asList(new String[] {"F", "F"}), 2.0);
		tf.setWeightList(Arrays.asList(new String[] {"F", "T"}), 2.0);
		tf.setWeightList(Arrays.asList(new String[] {"T", "F"}), 3.0);
		tf.setWeightList(Arrays.asList(new String[] {"T", "T"}), 4.0);

		tf = factorGraph.addTableFactor(Arrays.asList(new String[] {"Var0", "Var2"}));
		tf.setWeightList(Arrays.asList(new String[] {"F", "F"}), 2.0);
		tf.setWeightList(Arrays.asList(new String[] {"F", "T"}), 2.0);
		tf.setWeightList(Arrays.asList(new String[] {"T", "F"}), 3.0);
		tf.setWeightList(Arrays.asList(new String[] {"T", "T"}), 1.0);

		tf = factorGraph.addTableFactor(Arrays.asList(new String[] {"Var0"}));
		tf.setWeightList(Arrays.asList(new String[] {"F"}), 2.0);
		tf.setWeightList(Arrays.asList(new String[] {"T"}), 1.0);
		
		return factorGraph;
	}

	public static MarginalTestCase testNonCliqueTreeUnconditional() {	
		MarginalTestCase testCase = new MarginalTestCase(nonCliqueTreeFactorGraph(), Assignment.EMPTY, false);
		testCase.addTest(28.0 / 60.0, new Integer[] {0}, "T");
		testCase.addTest(32.0 / 60.0, new Integer[] {0}, "F");
		
		return testCase;
	}
}
