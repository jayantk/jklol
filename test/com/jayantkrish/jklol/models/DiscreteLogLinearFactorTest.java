package com.jayantkrish.jklol.models;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.TensorBase;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link DiscreteLogLinearFactor}.
 * 
 * @author jayantk
 */
public class DiscreteLogLinearFactorTest extends TestCase {

  DiscreteLogLinearFactor f;
  VariableNumMap vars;
  SufficientStatistics parameters;
  
  public void setUp() {
    DiscreteVariable v = new DiscreteVariable("Two values",
        Arrays.asList("T", "F" ));
    
    vars = new VariableNumMap(Arrays.asList(2, 3), Arrays.asList("v2", "v3"), Arrays.asList(v, v));
    TableFactorBuilder initialWeights = TableFactorBuilder.ones(vars);
    initialWeights.setWeight(0.0, "F", "F");
    f = DiscreteLogLinearFactor.createIndicatorFactor(vars, initialWeights);
    
    parameters = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "F"),
        1.0);
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "T"),
        0.5);
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "T"),
        0.5);
  }
  
  public void testGetFactorFromParameters() {
    TableFactor factor = f.getFactorFromParameters(parameters);
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
    TableFactor factor = f.getFactorFromParameters(parameters);
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
}
