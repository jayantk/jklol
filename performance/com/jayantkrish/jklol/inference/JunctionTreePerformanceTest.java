package com.jayantkrish.jklol.inference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jayantkrish.jklol.inference.MarginalCalculator.ZeroProbabilityError;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.testing.PerformanceTest;
import com.jayantkrish.jklol.testing.PerformanceTestCase;
import com.jayantkrish.jklol.testing.PerformanceTestRunner;
import com.jayantkrish.jklol.util.Assignment;

public class JunctionTreePerformanceTest extends PerformanceTestCase {

  FactorGraph f;
  JunctionTree t;

  TableFactor factor1, factor2, factor3, factor4, factor5, factor6;

  int numValues = 10000;

  public void setUp() {
    List<Integer> varValues = new ArrayList<Integer>();
    for (int i =0 ; i < numValues; i++) {
      varValues.add(i);
    }

    DiscreteVariable var = new DiscreteVariable("int var", varValues);

    f = new FactorGraph();
    f = f.addVariable("Var0", var);
    f = f.addVariable("Var1", var);
    f = f.addVariable("Var2", var);

    VariableNumMap vars = f.getVariables().getVariablesByName(Arrays.asList("Var0", "Var1"));
    TableFactorBuilder tfBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < numValues; i++) {
      Assignment a = new Assignment(vars.getVariableNums(), 
          Arrays.asList(new Integer[] {i, (numValues - 1) - i}));
      tfBuilder.setWeight(a, 1.0);
    }
    factor1 = tfBuilder.build();
    f = f.addFactor("factor1", factor1);

    vars = f.getVariables().getVariablesByName(Arrays.asList("Var1", "Var2"));
    tfBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < numValues; i++) {
      Assignment a = new Assignment(vars.getVariableNums(), 
          Arrays.asList(new Integer[] {i, i}));
      tfBuilder.setWeight(a, 1.0);
    }
    factor2 = tfBuilder.build();
    f = f.addFactor("factor2", factor2);

    vars = f.getVariables().getVariablesByName(Arrays.asList("Var2"));
    tfBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < 1000; i++) {
      Assignment a = new Assignment(vars.getVariableNums(), 
          Arrays.asList(new Integer[] {i}));
      tfBuilder.setWeight(a, 1.0);
    }
    factor3 = tfBuilder.build();
    f = f.addFactor("factor3", factor3);

    vars = f.getVariables().getVariablesByName(Arrays.asList("Var1"));
    tfBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < 10; i++) {
      Assignment a = new Assignment(vars.getVariableNums(), 
          Arrays.asList(new Integer[] {i * 100}));
      tfBuilder.setWeight(a, 1.0);
    }
    factor4 = tfBuilder.build();

    vars = f.getVariables().getVariablesByName(Arrays.asList("Var1", "Var2"));
    tfBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (int i = 0; i < 500000; i++) {
      Assignment a = new Assignment(vars.getVariableNums(), 
          Arrays.asList(new Integer[] {i / 50, i % 50}));
      tfBuilder.setWeight(a, 1.0);
    }
    factor5 = tfBuilder.build();

    t = new JunctionTree();
  }

  @PerformanceTest
  public void testFactorProductRightSubset() {
    factor2.product(factor3);
  }

  @PerformanceTest
  public void testFactorProductLeftSubset() {
    factor2.product(factor4);
  }

  @PerformanceTest
  public void testFactorProductLeftSubsetBig() {
    factor5.product(factor4);
  }

  @PerformanceTest
  public void testArrayFactorProduct() {
    double[][] fAssignments = new double[2][numValues];
    double[] fValues = new double[numValues];
    double[][] gAssignments = new double[1][1000];
    double[] gValues = new double[1000];

    Arrays.fill(fValues, 1.0);
    Arrays.fill(gValues, 2.0);

    for (int i = 0; i < numValues; i++) {
      fAssignments[0][i] = i;
      fAssignments[1][i] = (numValues - 1) - i;
    }

    for (int i = 0; i < 1000; i++) {
      gAssignments[0][i] = i;
    }

    int fInd = 0;
    int gInd = 0;

    double[][] targetAssignments = new double[2][numValues];
    double[] targetValues = new double[numValues];

    while (fInd < fValues.length && gInd < gValues.length) {
      while (fInd < fValues.length && gInd < gValues.length && 
          fAssignments[0][fInd] != gAssignments[0][gInd]) {
        if (fAssignments[0][fInd] > gAssignments[0][gInd]) {
          fInd++;
        } else {
          gInd++;
        }
      }

      if (!(fInd < fValues.length && gInd < gValues.length)) {
        break;
      }

      for (int j = 0; j < 2; j++) {
        targetAssignments[j][fInd] = fAssignments[j][fInd];
      }
      targetValues[fInd] = fValues[fInd] * gValues[gInd];
      fInd++;
    }		
  }

  @PerformanceTest
  public void testFactorSumProductSubset() {
    Factor product = factor2.product(factor3);
    product.marginalize(Arrays.asList(new Integer[] {0, 2}));
  }

  @PerformanceTest
  public void testMarginals() {
    t.computeMarginals(f);
  }

  @PerformanceTest
  public void testConditionalMarginals() {
    try {
      FactorGraph c = f.conditional(new Assignment(Arrays.asList(new Integer[] {0}),
          Arrays.asList(new Object[] {0})));
      t.computeMarginals(c);
    } catch (ZeroProbabilityError e) {
      // This exception should be thrown.
    }
  }
  
  @PerformanceTest(3)
  public void testSingleFactorMarginals() {
    t.computeMarginals(FactorGraph.createFromFactors(Arrays.<Factor>asList(factor1)));
  }
  
  @PerformanceTest(3)
  public void testSingleFactorMaxMarginals() {
    t.computeMaxMarginals(FactorGraph.createFromFactors(Arrays.<Factor>asList(factor1)));
  }
  
  @PerformanceTest(1)
  public void testSingleFactorCliqueTree() {
    new JunctionTree.CliqueTree(FactorGraph.createFromFactors(Arrays.<Factor>asList(factor1)));
  }

  @PerformanceTest
  public void testConditional() {
    factor2.conditional(new Assignment(Arrays.asList(0), Arrays.asList(0)));
  }	

  public static void main(String[] args) {
    PerformanceTestRunner.run(new JunctionTreePerformanceTest());
  }
}