package com.jayantkrish.jklol.models.parametric;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class CombiningParametricFactorTest extends TestCase {

  VariableNumMap vars;
  DiscreteLogLinearFactor f,g;
  CombiningParametricFactor factor;
  ListSufficientStatistics stats;
  
  private static final double TOLERANCE = 1e-10;
  
  public void setUp() {
    DiscreteVariable v = new DiscreteVariable("Two values",
        Arrays.asList("F", "T" ));

    vars = new VariableNumMap(Arrays.asList(1, 2), Arrays.asList("v1", "v2"), Arrays.asList(v, v));    
    f = DiscreteLogLinearFactor.createIndicatorFactor(vars);
    
    DiscreteVariable features = new DiscreteVariable("features", Arrays.asList("F*", "*F"));
    VariableNumMap featureVar = VariableNumMap.singleton(3, "features", features);
    VariableNumMap joint = vars.union(featureVar);
    TableFactorBuilder featureBuilder = new TableFactorBuilder(joint, 
        SparseTensorBuilder.getFactory());
    
    featureBuilder.setWeight(1.0, "F", "F", "F*");
    featureBuilder.setWeight(1.0, "F", "T", "F*");
    featureBuilder.setWeight(1.0, "F", "F", "*F");
    featureBuilder.setWeight(1.0, "T", "F", "*F");

    g = new DiscreteLogLinearFactor(vars, featureVar, featureBuilder.build());

    factor = new CombiningParametricFactor(vars, Arrays.asList("indicators", "falses"),
        Arrays.asList(f, g));
    
    stats = factor.getNewSufficientStatistics().coerceToList();
    factor.incrementSufficientStatisticsFromAssignment(stats, 
        vars.outcomeArrayToAssignment("F", "T"), 1.0);
    factor.incrementSufficientStatisticsFromAssignment(stats, 
        vars.outcomeArrayToAssignment("T", "T"), 2.0);
  }
  
  public void testGetModelFromParameters() {
    Factor f = factor.getModelFromParameters(stats);
    assertEquals(1.0, f.getUnnormalizedLogProbability("F", "F"), TOLERANCE);
    assertEquals(2.0, f.getUnnormalizedLogProbability("F", "T"), TOLERANCE);
    assertEquals(0.0, f.getUnnormalizedLogProbability("T", "F"), TOLERANCE);
    assertEquals(2.0, f.getUnnormalizedLogProbability("T", "T"), TOLERANCE);
  }
  
  public void testIncrementSufficientStatisticsFromMarginal() {
    List<String> marginalList = Arrays.asList("F,F,0", "F,T,0", "T,F,1", "T,T,1");
    Factor marginal = TableFactor.fromDelimitedFile(vars, marginalList, ",", false);

    factor.incrementSufficientStatisticsFromMarginal(stats, marginal, Assignment.EMPTY,
        1, 2);

    Factor f = factor.getModelFromParameters(stats);
    assertEquals(1.5, f.getUnnormalizedLogProbability("F", "F"), TOLERANCE);
    assertEquals(2.0, f.getUnnormalizedLogProbability("F", "T"), TOLERANCE);
    assertEquals(1.0, f.getUnnormalizedLogProbability("T", "F"), TOLERANCE);
    assertEquals(2.5, f.getUnnormalizedLogProbability("T", "T"), TOLERANCE);
  }
}
