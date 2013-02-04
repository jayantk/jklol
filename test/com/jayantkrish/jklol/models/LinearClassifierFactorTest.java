package com.jayantkrish.jklol.models;

import java.util.Arrays;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link LinearClassifierFactor}.
 * 
 * @author jayantk
 */
public class LinearClassifierFactorTest extends TestCase {

  VariableNumMap inputVar,outputVar;
  SparseTensor weights, input;
  LinearClassifierFactor factor, normedFactor;
  
  public void setUp() {
    DiscreteVariable outputVariable = new DiscreteVariable("foo", 
        Arrays.asList("A", "B", "C", "D"));
    ObjectVariable inputVariable = new ObjectVariable(SparseTensor.class);
    
    inputVar = new VariableNumMap(Ints.asList(1), Arrays.asList("inputVar"), 
        Arrays.<Variable>asList(inputVariable));
    outputVar = new VariableNumMap(Ints.asList(2), Arrays.asList("outputVar"), 
        Arrays.asList(outputVariable));
    
    SparseTensorBuilder weightBuilder = new SparseTensorBuilder(new int[] {1, 2}, new int[] {3, 4});
    for (int i = 0; i < 8; i++) {
      weightBuilder.put(new int[] {i / 4, i % 4}, i);
    }
    
    factor = new LinearClassifierFactor(inputVar, outputVar,
        DiscreteVariable.sequence("features", 4), weightBuilder.build());
    normedFactor = new LinearClassifierFactor(inputVar, outputVar, outputVar,
        DiscreteVariable.sequence("features", 4), weightBuilder.build());
    
    SparseTensorBuilder inputBuilder = new SparseTensorBuilder(new int[] {1}, new int[] {3});
    inputBuilder.put(new int[] {0}, 1);
    inputBuilder.put(new int[] {1}, 2);
    inputBuilder.put(new int[] {2}, 3); // Doesn't exist in weights.
    input = inputBuilder.build();
  }
  
  public void testGetUnnormalizedProbability() {
    Assignment a = new Assignment(Ints.asList(1, 2), Arrays.asList(input, "A"));
    assertEquals(8.0, Math.log(factor.getUnnormalizedProbability(a)), 0.001);
    a = new Assignment(Ints.asList(1, 2), Arrays.asList(input, "C"));
    assertEquals(14.0, Math.log(factor.getUnnormalizedProbability(a)), 0.001);
  }
  
  public void testGetUnnormalizedProbabilityInvalid() {
    Assignment a = new Assignment(Ints.asList(2), Arrays.asList("A"));
    try {
      factor.getUnnormalizedProbability(a);
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
  
  public void testConditional() {
    Assignment a = new Assignment(Ints.asList(1), Arrays.asList(input));
    Factor output = factor.conditional(a);
    assertEquals(8.0, Math.log(output.getUnnormalizedProbability("A")), 0.001);
    assertEquals(11.0, Math.log(output.getUnnormalizedProbability("B")), 0.001);
    assertEquals(14.0, Math.log(output.getUnnormalizedProbability("C")), 0.001);
    assertEquals(17.0, Math.log(output.getUnnormalizedProbability("D")), 0.001);
        
    a = new Assignment(Ints.asList(1, 2), Arrays.asList(input, "A"));
    output = factor.conditional(a);
    assertEquals(0, output.getVars().size());
    assertEquals(8.0, Math.log(output.getUnnormalizedProbability(Assignment.EMPTY)), 0.001);
  }
  
  public void testConditionalNormed() {
    Assignment a = new Assignment(Ints.asList(1), Arrays.asList(input));
    Factor output = normedFactor.conditional(a);
    assertEquals(-0.051, Math.log(output.getUnnormalizedProbability("D")), 0.001);
    assertEquals(1.0, output.getTotalUnnormalizedProbability(), .001);
        
    a = new Assignment(Ints.asList(1, 2), Arrays.asList(input, "D"));
    output = normedFactor.conditional(a);
    assertEquals(0, output.getVars().size());
    assertEquals(-0.051, Math.log(output.getUnnormalizedProbability(Assignment.EMPTY)), 0.001);
  }
  
  public void testConditionalInvalid() {
    Assignment a = new Assignment(Ints.asList(2), Arrays.asList("A"));
    try {
      factor.conditional(a);
    } catch (IllegalArgumentException e) {
      return;
    }
    fail("Expected IllegalArgumentException");
  }
}
