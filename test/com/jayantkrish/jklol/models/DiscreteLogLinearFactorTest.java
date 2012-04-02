package com.jayantkrish.jklol.models;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.TensorBase;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link DiscreteLogLinearFactor}.
 * 
 * @author jayantk
 */
public class DiscreteLogLinearFactorTest extends TestCase {

  ParametricFactor f;
  VariableNumMap vars;
  SufficientStatistics parameters;
  
  DiscreteLogLinearFactor g;
  
  public void setUp() {
    DiscreteVariable v = new DiscreteVariable("Two values",
        Arrays.asList("T", "F" ));
    
    vars = new VariableNumMap(Arrays.asList(2, 3), Arrays.asList("v2", "v3"), Arrays.asList(v, v));
    TableFactorBuilder initialWeights = TableFactorBuilder.ones(vars);
    initialWeights.setWeight(0.0, "F", "F");
    f = DiscreteLogLinearFactor.createIndicatorFactor(vars, initialWeights);
    //f = new IndicatorLogLinearFactor(vars, initialWeights.build());
    
    parameters = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "F"),
        1.0);
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "T"),
        0.5);
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "T"),
        0.5);
    
    DiscreteVariable featureVar1 = new DiscreteVariable("features1", Arrays.asList("f1", "f2", "f3"));
    DiscreteVariable featureVar2 = new DiscreteVariable("features2", Arrays.asList("g1", "g2"));
    VariableNumMap featureVars = new VariableNumMap(Arrays.asList(4, 5), Arrays.asList("feat1", "feat2"), Arrays.asList(featureVar1, featureVar2));
    TableFactorBuilder featureBuilder = new TableFactorBuilder(featureVars.union(vars), SparseTensorBuilder.getFactory());
    VariableNumMap featureBuilderVars = featureBuilder.getVars(); 
    featureBuilder.setWeight(featureBuilderVars.outcomeArrayToAssignment("T", "T", "f1", "g1"), 1.0);
    featureBuilder.setWeight(featureBuilderVars.outcomeArrayToAssignment("F", "T", "f2", "g1"), 2.0);
    featureBuilder.setWeight(featureBuilderVars.outcomeArrayToAssignment("F", "T", "f2", "g2"), 3.0);
    featureBuilder.setWeight(featureBuilderVars.outcomeArrayToAssignment("T", "F", "f1", "g1"), 4.0);
    featureBuilder.setWeight(featureBuilderVars.outcomeArrayToAssignment("T", "F", "f3", "g1"), 5.0);
    featureBuilder.setWeight(featureBuilderVars.outcomeArrayToAssignment("F", "F", "f3", "g1"), 4.0);
    featureBuilder.setWeight(featureBuilderVars.outcomeArrayToAssignment("F", "F", "f3", "g2"), 6.0);

    g = new DiscreteLogLinearFactor(vars, featureVars, featureBuilder.build());
  }
  
  public void testGetFactorFromParameters() {
    TableFactor factor = (TableFactor) f.getFactorFromParameters(parameters);
    assertEquals(Math.E, factor.getUnnormalizedProbability(vars.outcomeArrayToAssignment("T", "T")), .00001);
    assertEquals(Math.E, factor.getUnnormalizedProbability(vars.outcomeArrayToAssignment("T", "F")), .00001);
    assertEquals(1.0, factor.getUnnormalizedProbability(vars.outcomeArrayToAssignment("F", "T")), .00001);
    assertEquals(0.0, factor.getUnnormalizedProbability(vars.outcomeArrayToAssignment("F", "F")), .00001);
  }
    
  public void testGetSufficientStatisticsFromAssignment() {
    Assignment tf = vars.outcomeArrayToAssignment("T", "F"); 
    SufficientStatistics s = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(s, tf, 1.0);
    
    TensorBase weights = ((TensorSufficientStatistics) s).get(0);
    assertEquals(1, weights.getDimensionNumbers().length);
    assertEquals(3, weights.getDimensionSizes()[0]);
    assertEquals(0.0, weights.getByDimKey(0));
    assertEquals(1.0, weights.getByDimKey(1));
    assertEquals(0.0, weights.getByDimKey(2));
  } 
  
  public void testGetSufficientStatisticsFromBigAssignment() {
    VariableNumMap moreVars = vars.addMapping(7, "foo", new DiscreteVariable("Foo",
        Arrays.asList("foo", "bar")));
    Assignment tf = moreVars.outcomeArrayToAssignment("T", "F", "bar"); 
    SufficientStatistics s = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(s, tf, 1.0);
    
    TensorBase weights = ((TensorSufficientStatistics) s).get(0);
    assertEquals(1, weights.getDimensionNumbers().length);
    assertEquals(3, weights.getDimensionSizes()[0]);
    assertEquals(0.0, weights.getByDimKey(0));
    assertEquals(1.0, weights.getByDimKey(1));
    assertEquals(0.0, weights.getByDimKey(2));
  }
  
  public void testGetSufficientStatisticsFromMarginal() {
    TableFactor factor = (TableFactor) f.getFactorFromParameters(parameters);
    double partitionFunction = 1.0 + (2 * Math.E);
    SufficientStatistics s = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromMarginal(s, factor, Assignment.EMPTY, 1.0, partitionFunction);
    
    TensorBase weights = ((TensorSufficientStatistics) s).get(0);
    assertEquals(1, weights.getDimensionNumbers().length);
    assertEquals(3, weights.getDimensionSizes()[0]);
    assertEquals((Math.E / partitionFunction), weights.getByDimKey(0), .00001);
    assertEquals((Math.E / partitionFunction), weights.getByDimKey(1), .00001);
    assertEquals((1.0 / partitionFunction), weights.getByDimKey(2), .00001);
  }
  
  public void testNonIndicatorFeatures() {
    TensorSufficientStatistics gParams = g.getNewSufficientStatistics();
    assertEquals(6, gParams.get(0).size());
    
    g.incrementSufficientStatisticsFromAssignment(gParams, vars.outcomeArrayToAssignment("T", "T"), 1.0);
    g.incrementSufficientStatisticsFromAssignment(gParams, vars.outcomeArrayToAssignment("F", "F"), 1.0);
    DiscreteFactor d = g.getFactorFromParameters(gParams);
    assertEquals(1.0, d.getUnnormalizedLogProbability("T", "T"), 0.00001);
    assertEquals(24.0, d.getUnnormalizedLogProbability("T", "F"), 0.00001);
    assertEquals(52.0, d.getUnnormalizedLogProbability("F", "F"), 0.00001);
  }
}
