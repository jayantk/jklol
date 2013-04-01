package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

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
    
    factor = new ConditionalLogLinearFactor(input, output, VariableNumMap.emptyMap(), 
        DiscreteVariable.sequence("foo", 5));
    
    featureVectors = Lists.newArrayList();
    featureVectors.add(SparseTensor.vector(0, 5, new double[] {1, 2, 0, 0, 0}));
    featureVectors.add(SparseTensor.vector(0, 5, new double[] {0, 1, 2, 0, 0}));
  }
  
  public void testGetFactorFromParameters() {
    Factor uniform = factor.getModelFromParameters(factor.getNewSufficientStatistics())
        .conditional(input.outcomeArrayToAssignment(featureVectors.get(0)));
    Factor empty = uniform.marginalize(output); 
    
    SufficientStatistics stats = factor.getNewSufficientStatistics();
    factor.incrementSufficientStatisticsFromAssignment(stats, 
        both.outcomeArrayToAssignment(featureVectors.get(0), "A"), 1.0);
    factor.incrementSufficientStatisticsFromMarginal(stats, empty,  
        both.outcomeArrayToAssignment(featureVectors.get(1), "B"), 1.0,
        empty.getTotalUnnormalizedProbability());
    
    Factor classifier = factor.getModelFromParameters(stats);
    Assignment inputAssignment = input.outcomeArrayToAssignment(featureVectors.get(0));
    Factor conditional = classifier.conditional(inputAssignment);

    assertEquals(Math.exp(2.0), conditional.getUnnormalizedProbability(output.outcomeArrayToAssignment("B")), 0.001);
    assertEquals(Math.exp(5.0), conditional.getUnnormalizedProbability(output.outcomeArrayToAssignment("A")), 0.001);
    assertEquals(Math.exp(0.0), conditional.getUnnormalizedProbability(output.outcomeArrayToAssignment("C")), 0.001);
    
    
    // Try incrementing the sufficient statistics with a marginal distribution.
    double partitionFunction = conditional.getTotalUnnormalizedProbability();
    factor.incrementSufficientStatisticsFromMarginal(stats, conditional, inputAssignment, 1.0, partitionFunction);
    
    classifier = factor.getModelFromParameters(stats);
    conditional = classifier.conditional(inputAssignment);

    // partitionFunction = 157.8022
    // e^5 / partitionFunction = .9405
    // e^2 / partitionFunction = .0468
    // e^0 / partitionFunction = .0063
    assertEquals(2.0 + (.0468 * 5.0), conditional.getUnnormalizedLogProbability(output.outcomeArrayToAssignment("B")), 0.001);
    assertEquals(1.9405 * 5.0, conditional.getUnnormalizedLogProbability(output.outcomeArrayToAssignment("A")), 0.001);
    assertEquals(0.0063 * 5.0, conditional.getUnnormalizedLogProbability(output.outcomeArrayToAssignment("C")), 0.001);
  }
}
