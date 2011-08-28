package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.bayesnet.BayesNet;
import com.jayantkrish.jklol.models.bayesnet.BayesNetBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class JunctionTreePerformanceTest extends TestCase {

	BayesNet f;
	JunctionTree t;

	TableFactor factor1;
	TableFactor factor2;
	TableFactor factor3;

	public void setUp() {
		int numValues = 10000;
		
		BayesNetBuilder builder = new BayesNetBuilder();

		List<Integer> varValues = new ArrayList<Integer>();
		for (int i =0 ; i < numValues; i++) {
			varValues.add(i);
		}

		DiscreteVariable var = new DiscreteVariable("int var", varValues);

		builder.addDiscreteVariable("Var0", var);
		builder.addDiscreteVariable("Var1", var);
		builder.addDiscreteVariable("Var2", var);

		factor1 = builder.addNewTableFactor(Arrays.asList(new String[] {"Var0", "Var1"}));
		for (int i = 0; i < numValues; i++) {
			factor1.setWeightList(Arrays.asList(new Integer[] {i, (numValues - 1) - i}), 1.0);
		}

		factor2 = builder.addNewTableFactor(Arrays.asList(new String[] {"Var1", "Var2"}));
		for (int i = 0; i < numValues; i++) {
			factor2.setWeightList(Arrays.asList(new Integer[] {i, i}), 1.0);
		}

		factor3 = builder.addNewTableFactor(Arrays.asList(new String[] {"Var2"}));
		for (int i = 0; i < 1000; i++) {
			factor3.setWeightList(Arrays.asList(new Integer[] {i}), 1.0);
		}

		f = builder.build();		
		t = new JunctionTree();
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

		t.computeMarginals(f);

		long elapsed = System.currentTimeMillis() - start;
		System.out.println("Elapsed: " + elapsed + " ms");
	}

	public void testConditionalMarginals() {
		System.out.println("testConditionalMarginals");
		long start = System.currentTimeMillis();

		t.computeMarginals(f, new Assignment(Arrays.asList(new Integer[] {0, 2}),
				Arrays.asList(new Object[] {0,0})));

		long elapsed = System.currentTimeMillis() - start;
		System.out.println("Elapsed: " + elapsed + " ms");
	}
	
	public void testConditional() {
	  System.out.println("testConditional");
		long start = System.currentTimeMillis();

		factor2.conditional(new Assignment(Arrays.asList(0), Arrays.asList(0)));

		long elapsed = System.currentTimeMillis() - start;
		System.out.println("Elapsed: " + elapsed + " ms");
	  
	}
	
	public void testSufficientStatistics() {
	  
	  MarginalSet marginals = t.computeMarginals(f);
	  
	  System.out.println("testSufficientStatistics");
		long start = System.currentTimeMillis();

		f.computeSufficientStatistics(marginals, 1.0);

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