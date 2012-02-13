package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Unit tests for {@link ConditionalLogLinearFactor}.
 * 
 * @author jayantk
 */
public class ConditionalLogLinearFactorTest extends TestCase {

  VariableNumMap input, output, both;
  
  ConditionalLogLinearFactor factor;
  
  List<Tensor> featureVectors;
  
  public void setUp() {
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);
    DiscreteVariable discreteVar = new DiscreteVariable("output", Arrays.asList("A", "B", "C", "D"));
    
    input = new VariableNumMap(Ints.asList(0), Arrays.asList("input"), 
        Arrays.<Variable>asList(tensorVar));
    output = new VariableNumMap(Ints.asList(1), Arrays.asList("output"), 
        Arrays.<Variable>asList(discreteVar));
    both = input.union(output);
    
    factor = new ConditionalLogLinearFactor(input, output, 5, SparseTensorBuilder.getFactory());
    
    featureVectors = Lists.newArrayList();
    featureVectors.add(SparseTensor.vector(0, 4, new double[] {1, 2, 0, 0, 0}));
    featureVectors.add(SparseTensor.vector(0, 4, new double[] {0, 1, 2, 0, 0}));
  }
  
  public void testGetFactorFromParameters() {
    SufficientStatistics stats = factor.getNewSufficientStatistics();
    factor.incrementSufficientStatisticsFromAssignment(stats, 
        both.outcomeArrayToAssignment(featureVectors.get(0), "A"), 1.0);
    factor.incrementSufficientStatisticsFromAssignment(stats, 
        both.outcomeArrayToAssignment(featureVectors.get(1), "B"), 1.0);
    
    Factor classifier = factor.getFactorFromParameters(stats);
    Factor conditional = classifier.conditional(input.outcomeArrayToAssignment(featureVectors.get(0)));

    assertEquals(Math.exp(2.0), conditional.getUnnormalizedProbability(output.outcomeArrayToAssignment("B")), 0.001);
    assertEquals(Math.exp(5.0), conditional.getUnnormalizedProbability(output.outcomeArrayToAssignment("A")), 0.001);
    assertEquals(Math.exp(0.0), conditional.getUnnormalizedProbability(output.outcomeArrayToAssignment("C")), 0.001);
  }
}
