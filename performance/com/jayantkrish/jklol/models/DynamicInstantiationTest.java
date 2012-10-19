package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.ReplicatedFactor;
import com.jayantkrish.jklol.models.dynamic.VariableNamePattern;
import com.jayantkrish.jklol.models.dynamic.VariablePattern;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.testing.PerformanceTest;
import com.jayantkrish.jklol.testing.PerformanceTestCase;
import com.jayantkrish.jklol.testing.PerformanceTestRunner;
import com.jayantkrish.jklol.util.Assignment;

public class DynamicInstantiationTest extends PerformanceTestCase {
  
  ParametricFactorGraph sequenceModel;
  DynamicFactorGraph dynamicFactorGraph;
  VariableNumMap x, y, all, sampleInstantiatedVariables;
  DynamicAssignment dynamicAssignment;
  ReplicatedFactor replicatedFactor;
  VariablePattern platePattern;
  
  SortedMap<Integer, Object> objects;
  SortedMap<String, Object> strings;
  
  int[] intArray;
  Object[] objectArray;
  
  @SuppressWarnings("unchecked")
  public void setUp() {
    objects = Maps.newTreeMap();
    strings = Maps.newTreeMap();
    
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();

    // Create a plate for each input/output pair.
    DiscreteVariable outputVar = new DiscreteVariable("tf",
        Arrays.asList("T", "F"));
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);
    builder.addPlate("plateVar", new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("x", "y"), Arrays.asList(tensorVar, outputVar)), 10000);

    // Factor connecting each x to the corresponding y.
    all = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("plateVar/?(0)/x", "plateVar/?(0)/y"), Arrays.asList(tensorVar, outputVar));
    x = all.getVariablesByName("plateVar/?(0)/x");
    y = all.getVariablesByName("plateVar/?(0)/y");
    ConditionalLogLinearFactor f = new ConditionalLogLinearFactor(x, y, VariableNumMap.emptyMap(), 
        DiscreteVariable.sequence("foo", 4)); 
    builder.addFactor("f1", f, VariableNamePattern.fromTemplateVariables(all, VariableNumMap.emptyMap()));
    platePattern = VariableNamePattern.fromTemplateVariables(all, VariableNumMap.emptyMap());

    // Factor connecting adjacent y's
    VariableNumMap adjacentVars = new VariableNumMap(Ints.asList(0, 1), 
        Arrays.asList("plateVar/?(0)/y", "plateVar/?(1)/y"), Arrays.asList(outputVar, outputVar));
    builder.addFactor("f2", DiscreteLogLinearFactor.createIndicatorFactor(adjacentVars),
        VariableNamePattern.fromTemplateVariables(adjacentVars, VariableNumMap.emptyMap()));

    sequenceModel = builder.build();
    dynamicFactorGraph = sequenceModel.getFactorGraphFromParameters(sequenceModel.getNewSufficientStatistics());
    replicatedFactor = new ReplicatedFactor(f.getFactorFromParameters(f.getNewSufficientStatistics()), 
        VariableNamePattern.fromTemplateVariables(all, VariableNumMap.emptyMap()));
    
    // Construct some training data.
    List<Assignment> inputAssignments = Lists.newArrayList();
    for (int i = 0; i < 3; i++) {
      double[] values = new double[4];
      values[0] = (i % 2) * 2 - 1;
      values[1] = ((i / 2) % 2) * 2 - 1;
      values[2] = ((i / 4) % 2) * 2 - 1;
      values[3] = 1;
      inputAssignments.add(x.outcomeArrayToAssignment(SparseTensor.vector(0, 4, values)));
    }
    dynamicAssignment = DynamicAssignment.createPlateAssignment("plateVar", inputAssignments);
    sampleInstantiatedVariables = dynamicFactorGraph.getVariables().instantiateVariables(dynamicAssignment);
  }

  @PerformanceTest(10)
  public void testFillMap() {
    for (int i = 0; i < 10; i++) {
      objects.put(i, i);
    }
  }
  
  @PerformanceTest(10)
  public void testFillMapString() {
    for (int i = 0; i < 10; i++) {
      strings.put(String.valueOf(i + 1000), i);
    }
  }
 
  @PerformanceTest(10)
  public void testFillArray() {
    int[] intArray = new int[10];
    Object[] objectArray = new Object[10];
    for (int i = 0; i < 10; i++) {
      intArray[i] = i;
      objectArray[i] = i;
    }
  }
  
  @PerformanceTest(10)
  public void testCreateMap() {
    SortedMap<Integer, Object> foo = Maps.newTreeMap();
    foo.put(1, 1);
  }
  
  @PerformanceTest(10)
  public void testGetFactorGraph() {
    dynamicFactorGraph.getFactorGraph(dynamicAssignment);
  }
  
  @PerformanceTest(10)
  public void testInstantiateVariables() {
    dynamicFactorGraph.getVariables().instantiateVariables(dynamicAssignment);
  }
  
  @PerformanceTest(10)
  public void testInstantiateFactors() {
    replicatedFactor.instantiateFactors(sampleInstantiatedVariables);
  }
  
  @PerformanceTest(10)
  public void testNamePatternMatch() {
    platePattern.matchVariables(sampleInstantiatedVariables);
  }

  public static void main(String[] args) {
    PerformanceTestRunner.run(new DynamicInstantiationTest());
  }
}
