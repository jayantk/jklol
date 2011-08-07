import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.util.Assignment;

public class JunctionTreePerformanceTest extends TestCase {

	FactorGraph f;
	JunctionTree t;

	TableFactor factor1;
	TableFactor factor2;
	TableFactor factor3;

	public void setUp() {
		int numValues = 10000;
		f = new FactorGraph();

		List<Integer> varValues = new ArrayList<Integer>();
		for (int i =0 ; i < numValues; i++) {
			varValues.add(i);
		}

		DiscreteVariable var = new DiscreteVariable("int var", varValues);

		f.addVariable("Var0", var);
		f.addVariable("Var1", var);
		f.addVariable("Var2", var);

		factor1 = f.addTableFactor(Arrays.asList(new String[] {"Var0", "Var1"}));
		for (int i = 0; i < numValues; i++) {
			factor1.setWeightList(Arrays.asList(new Integer[] {i, (numValues - 1) - i}), 1.0);
		}

		factor2 = f.addTableFactor(Arrays.asList(new String[] {"Var1", "Var2"}));
		for (int i = 0; i < numValues; i++) {
			factor2.setWeightList(Arrays.asList(new Integer[] {i, i}), 1.0);
		}

		factor3 = f.addTableFactor(Arrays.asList(new String[] {"Var2"}));
		for (int i = 0; i < 1000; i++) {
			factor3.setWeightList(Arrays.asList(new Integer[] {i}), 1.0);
		}

		t = new JunctionTree();
		t.setFactorGraph(f);
	}

	public void testFactorProductSubset() {
		System.out.println("testFactorProductSubset");
		long start = System.currentTimeMillis();

		TableFactor.productFactor(Arrays.asList(new DiscreteFactor[] {factor2, factor3}));

		long elapsed = System.currentTimeMillis() - start;
		System.out.println("Elapsed: " + elapsed + " ms");
	}

	public void testFactorSumProductSubset() {
		System.out.println("testFactorSumProductSubset");
		long start = System.currentTimeMillis();

		Factor product = TableFactor.productFactor(Arrays.asList(new DiscreteFactor[] {factor2, factor3}));
		product.marginalize(Arrays.asList(new Integer[] {0, 2}));

		long elapsed = System.currentTimeMillis() - start;
		System.out.println("Elapsed: " + elapsed + " ms");
	}

	public void testMarginals() {
		System.out.println("testMarginals");
		long start = System.currentTimeMillis();

		t.computeMarginals();

		long elapsed = System.currentTimeMillis() - start;
		System.out.println("Elapsed: " + elapsed + " ms");
	}

	public void testConditionalMarginals() {
		System.out.println("testConditionalMarginals");
		long start = System.currentTimeMillis();

		t.computeMarginals(new Assignment(Arrays.asList(new Integer[] {0, 2}),
				Arrays.asList(new Object[] {0,0})));

		long elapsed = System.currentTimeMillis() - start;
		System.out.println("Elapsed: " + elapsed + " ms");
	}


	/*
    public void testFactorProductNoSubset() {
	System.out.println("testFactorProductNoSubset");
	long start = System.currentTimeMillis();

	TableFactor.productFactor(Arrays.asList(new Factor[] {factor1, factor2}));

	long elapsed = System.currentTimeMillis() - start;
	System.out.println("Elapsed: " + elapsed + " ms");
    }
	 */
}