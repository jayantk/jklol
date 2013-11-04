package com.jayantkrish.jklol.models;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class NormalizingFactorTest extends TestCase {
  
  private VariableNumMap inputVar, conditionalVars, outputVars;

  private NormalizingFactor factor;
  
  private static final double TOLERANCE = 1e-6;
  
  public void setUp() {
    DiscreteVariable inputVarType = new DiscreteVariable("inputType", Arrays.asList("a", "b", "c"));
    DiscreteVariable tfType = new DiscreteVariable("tfType", Arrays.asList("T", "F"));
    
    inputVar = VariableNumMap.singleton(0, "input", inputVarType);
    conditionalVars = VariableNumMap.singleton(1, "conditionalVar", tfType);
    outputVars = VariableNumMap.singleton(2, "outputVar", tfType);
    
    TableFactorBuilder inputBuilder = new TableFactorBuilder(inputVar.union(outputVars),
        SparseTensorBuilder.getFactory());
    inputBuilder.setWeight(1.0, "a", "T");
    inputBuilder.setWeight(3.0, "a", "F");
    inputBuilder.setWeight(3.0, "b", "F");
    
    Factor inputFactor = inputBuilder.build();
    
    TableFactorBuilder transitionFactorBuilder = new TableFactorBuilder(conditionalVars.union(outputVars),
        SparseTensorBuilder.getFactory());
    transitionFactorBuilder.setWeight(1.0, "T", "T");
    transitionFactorBuilder.setWeight(0.5, "T", "F");
    transitionFactorBuilder.setWeight(2.0, "F", "T");
    transitionFactorBuilder.setWeight(3.0, "F", "F");

    Factor transitionFactor = transitionFactorBuilder.build();
    
    factor = new NormalizingFactor(inputVar, conditionalVars, outputVars,
        Arrays.asList(inputFactor, transitionFactor));
  }
  
  public void testConditionalEmpty() {
    assertEquals(factor.conditional(Assignment.EMPTY), factor);
  }
  
  public void testConditional1() {
    Factor result = factor.conditional(inputVar.outcomeArrayToAssignment("a"));

    assertEquals(1.0 / 2.5, result.getUnnormalizedProbability("T", "T"), TOLERANCE);
    assertEquals(1.5 / 2.5, result.getUnnormalizedProbability("T", "F"), TOLERANCE);
    assertEquals(2.0 / 11.0, result.getUnnormalizedProbability("F", "T"), TOLERANCE);
    assertEquals(9.0 / 11.0, result.getUnnormalizedProbability("F", "F"), TOLERANCE);
  }
  
  public void testConditional2() {
    Factor result = factor.conditional(inputVar.outcomeArrayToAssignment("b"));
    assertEquals(0.0, result.getUnnormalizedProbability("T", "T"), TOLERANCE);
    assertEquals(1.0, result.getUnnormalizedProbability("T", "F"), TOLERANCE);
    assertEquals(0.0, result.getUnnormalizedProbability("F", "T"), TOLERANCE);
    assertEquals(1.0, result.getUnnormalizedProbability("F", "F"), TOLERANCE);
  }

  public void testGetUnnormalizedProbability() {
    assertEquals(1.0 / 2.5, factor.getUnnormalizedProbability("a", "T", "T"), TOLERANCE);
    assertEquals(1.5 / 2.5, factor.getUnnormalizedProbability("a", "T", "F"), TOLERANCE);
  }
}
