package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.models.loglinear.FeatureSufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link DiscreteLogLinearFactor}.
 * 
 * @author jayantk
 */
public class DiscreteLogLinearFactorTest extends TestCase {

  DiscreteLogLinearFactor f;
  VariableNumMap vars;
  FeatureSufficientStatistics parameters;
  
  public void setUp() {
    DiscreteVariable v = new DiscreteVariable("Two values",
        Arrays.asList("T", "F" ));
    
    vars = new VariableNumMap(Arrays.asList(2, 3), Arrays.asList("v2", "v3"), Arrays.asList(v, v));
    
    f = DiscreteLogLinearFactor.createIndicatorFactor(vars);
    
    parameters = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "F"),
        1.0);
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "T"),
        0.5);
    f.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("T", "T"),
        0.5);
  }
  
  public void testGetFeatures() {
    assertEquals(4, f.getFeatures().size());
  }
  
  public void testGetFactorFromParameters() {
    TableFactor factor = f.getFactorFromParameters(parameters);
    assertEquals(Math.E, factor.getUnnormalizedProbability(vars.outcomeArrayToAssignment("T", "T")), .00001);
    assertEquals(Math.E, factor.getUnnormalizedProbability(vars.outcomeArrayToAssignment("T", "F")), .00001);
    assertEquals(1.0, factor.getUnnormalizedProbability(vars.outcomeArrayToAssignment("F", "T")), .00001);
    assertEquals(1.0, factor.getUnnormalizedProbability(vars.outcomeArrayToAssignment("F", "F")), .00001);
  }
  
  public void testGetNewSufficientStatistics() {
    assertEquals(4, f.getNewSufficientStatistics().getFeatures().size());
    assertEquals(4, f.getNewSufficientStatistics().getWeights().length);
  }
  
  public void testGetSufficientStatisticsFromAssignment() {
    Assignment tf = vars.outcomeArrayToAssignment("T", "F"); 
    FeatureSufficientStatistics s = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(s, tf, 1.0);
    
    List<FeatureFunction> features = s.getFeatures();
    double[] weights = s.getWeights();
    assertEquals(4, features.size());
    assertEquals(4, weights.length);
    for (int i = 0; i < features.size(); i++) {
      assertEquals(weights[i], features.get(i).getValue(tf));      
    }
  }
  
  public void testGetSufficientStatisticsFromBigAssignment() {
    VariableNumMap moreVars = vars.addMapping(7, "foo", new DiscreteVariable("Foo",
        Arrays.asList("foo", "bar")));
    Assignment tf = moreVars.outcomeArrayToAssignment("T", "F", "bar"); 
    FeatureSufficientStatistics s = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromAssignment(s, tf, 1.0);
    
    List<FeatureFunction> features = s.getFeatures();
    double[] weights = s.getWeights();
    assertEquals(4, features.size());
    assertEquals(4, weights.length);
    for (int i = 0; i < features.size(); i++) {
      assertEquals(weights[i], features.get(i).getValue(tf.intersection(vars)));      
    }
  }
  
  public void testGetSufficientStatisticsFromMarginal() {
    TableFactor factor = f.getFactorFromParameters(parameters);
    double partitionFunction = 2 * (1.0 + Math.E);
    FeatureSufficientStatistics s = f.getNewSufficientStatistics();
    f.incrementSufficientStatisticsFromMarginal(s, factor, Assignment.EMPTY, 1.0, partitionFunction);
    
    List<FeatureFunction> features = s.getFeatures();
    double[] weights = s.getWeights();
    assertEquals(4, features.size());
    assertEquals(4, weights.length);
    for (int i = 0; i < features.size(); i++) {
      FeatureFunction feature = features.get(i);
      double weight = 0.0;
      Iterator<Assignment> iter = feature.getNonzeroAssignments(); 
      while(iter.hasNext()) {
        Assignment a = iter.next();
        weight += feature.getValue(a) * factor.getUnnormalizedProbability(a);
      }
      weight = weight / partitionFunction; 
      assertEquals(weights[i], weight);
    }
  }
}
