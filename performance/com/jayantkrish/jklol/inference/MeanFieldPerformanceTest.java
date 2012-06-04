package com.jayantkrish.jklol.inference;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.testing.PerformanceTest;
import com.jayantkrish.jklol.testing.PerformanceTestCase;
import com.jayantkrish.jklol.testing.PerformanceTestRunner;

public class MeanFieldPerformanceTest extends PerformanceTestCase {
  FactorGraph f;
  MeanFieldVariational m;
  
  int numValues = 2;
  int numNodes = 10000;
  
  public void setUp() {
    DiscreteVariable var = DiscreteVariable.sequence("int var", numValues);
    
    f = new FactorGraph();
    for (int i = 0; i < numNodes; i++) {
      f = f.addVariable("var" + i, var);
    }
    
    for (int i = 0; i < numNodes; i++) {
      VariableNumMap curVar = f.getVariables().getVariablesByName("var" + i);
      Tensor weights = new LogSpaceTensorAdapter(DenseTensor.random(new int[] {curVar.getOnlyVariableNum()}, curVar.getVariableSizes(), 0, 1));
      f = f.addFactor("factor" + i, new TableFactor(curVar, weights));
    }
    
    for (int i = 0; i < numNodes - 1; i++) {
      VariableNumMap curVars = f.getVariables().getVariablesByName("var" + i, "var" + (i + 1));
      Tensor weights = new LogSpaceTensorAdapter(DenseTensor.random(Ints.toArray(curVars.getVariableNums()), curVars.getVariableSizes(), 0, 1));
      f = f.addFactor("factor" + i + "," + (i+1), new TableFactor(curVars, weights));
    }
    
    m = new MeanFieldVariational();
  }
  
  @PerformanceTest(3)
  public void testInference() {
    m.computeMarginals(f);
  }
  
  public static void main(String[] args) {
    PerformanceTestRunner.run(new MeanFieldPerformanceTest());
  }
}
