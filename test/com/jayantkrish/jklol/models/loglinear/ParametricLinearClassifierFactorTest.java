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
 * Unit tests for {@link ParametricLinearClassifierFactor}.
 * 
 * @author jayantk
 */
public class ParametricLinearClassifierFactorTest extends TestCase {

  VariableNumMap input, output, both;
  
  ParametricLinearClassifierFactor factor, naiveBayes;
  
  List<Tensor> featureVectors;
  
  public void setUp() {
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);
    DiscreteVariable discreteVar = new DiscreteVariable("output", Arrays.asList("A", "B", "C", "D"));
    
    input = new VariableNumMap(Ints.asList(0), Arrays.asList("input"), 
        Arrays.<Variable>asList(tensorVar));
    output = new VariableNumMap(Ints.asList(1), Arrays.asList("output"), 
        Arrays.<Variable>asList(discreteVar));
    both = input.union(output);
    
    factor = new ParametricLinearClassifierFactor(input, output, VariableNumMap.EMPTY, 
        DiscreteVariable.sequence("foo", 5), false);
    
    naiveBayes = new ParametricLinearClassifierFactor(input, output, VariableNumMap.EMPTY, 
        DiscreteVariable.sequence("foo", 5), true);
    
    featureVectors = Lists.newArrayList();
    featureVectors.add(SparseTensor.vector(0, 5, new double[] {1, 2, 0, 0, 0}));
    featureVectors.add(SparseTensor.vector(0, 5, new double[] {0, 1, 2, 0, 0}));
  }
  
  public void testGetFactorFromParameters() {
    SufficientStatistics currentParams = factor.getNewSufficientStatistics();
    Factor uniform = factor.getModelFromParameters(currentParams)
        .conditional(input.outcomeArrayToAssignment(featureVectors.get(0)));
    Factor empty = uniform.marginalize(output); 
    
    SufficientStatistics stats = factor.getNewSufficientStatistics();
    factor.incrementSufficientStatisticsFromAssignment(stats, currentParams,
        both.outcomeArrayToAssignment(featureVectors.get(0), "A"), 1.0);
    factor.incrementSufficientStatisticsFromMarginal(stats, currentParams, empty,  
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
    factor.incrementSufficientStatisticsFromMarginal(stats, currentParams, conditional,
        inputAssignment, 1.0, partitionFunction);
    
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
  
  public void testNaiveBayes() {
    SufficientStatistics stats = naiveBayes.getNewSufficientStatistics();
    SufficientStatistics currentParams = naiveBayes.getNewSufficientStatistics();
    stats.increment(1.0);
    naiveBayes.incrementSufficientStatisticsFromAssignment(stats, currentParams,
        both.outcomeArrayToAssignment(featureVectors.get(0), "A"), 1.0);
    
    Factor classifier = naiveBayes.getModelFromParameters(stats);
    Assignment inputAssignment = input.outcomeArrayToAssignment(featureVectors.get(0));
    Factor conditional = classifier.conditional(inputAssignment);
    
    // Parameters of A are: 2 3 1 1 1 = 8
    // = 1/4 3/8 1/8 1/8 1/8
    // P(A) = 1/4 * 3/8 * 3/8

    assertEquals(9.0 / 256, conditional.getUnnormalizedProbability(output.outcomeArrayToAssignment("A")), 0.001);
    assertEquals(1.0 / 125, conditional.getUnnormalizedProbability(output.outcomeArrayToAssignment("B")), 0.001);
  }
}
