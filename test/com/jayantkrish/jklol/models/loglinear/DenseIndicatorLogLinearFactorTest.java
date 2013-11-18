package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class DenseIndicatorLogLinearFactorTest extends TestCase {
  
  VariableNumMap alphabetVar, truthVar, vars;
  
  DiscreteFactor featureIndicators;

  private static final double TOLERANCE = 1e-5;

  public void setUp() {
    DiscreteVariable var1 = new DiscreteVariable("alphabet", Arrays.asList("A", "B", "C"));
    DiscreteVariable var2 = new DiscreteVariable("truth", Arrays.asList("T", "F"));
    
    alphabetVar = VariableNumMap.singleton(0, "alphabet", var1);
    truthVar = VariableNumMap.singleton(1, "truth", var2);
    vars = alphabetVar.union(truthVar);
    
    TableFactorBuilder builder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    builder.setWeight(1.0, "A", "T");
    builder.setWeight(1.0, "B", "F");
    builder.setWeight(1.0, "C", "F");
    builder.setWeight(1.0, "B", "T");
    featureIndicators = builder.build();
  }
  
  private void runTestAllFeatures(DenseIndicatorLogLinearFactor parametricFactor) {
    SufficientStatistics parameters = parametricFactor.getNewSufficientStatistics();
    Factor initial = parametricFactor.getModelFromParameters(parameters);
    assertEquals(0.0, initial.getUnnormalizedLogProbability("A", "T"), TOLERANCE);
    assertEquals(0.0, initial.getUnnormalizedLogProbability("B", "T"), TOLERANCE);
    assertEquals(0.0, initial.getUnnormalizedLogProbability("C", "F"), TOLERANCE);

    parametricFactor.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("B", "F"), 2.0);
    parametricFactor.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("B", "F"), -3.0);
    parametricFactor.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("A", "T"), 1.0);
    parametricFactor.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("C", "T"), 2.0);

    Factor factor = parametricFactor.getModelFromParameters(parameters);
    assertEquals(-1.0, factor.getUnnormalizedLogProbability("B", "F"), TOLERANCE);
    assertEquals(1.0, factor.getUnnormalizedLogProbability("A", "T"), TOLERANCE);
    assertEquals(2.0, factor.getUnnormalizedLogProbability("C", "T"), TOLERANCE);

    TableFactorBuilder incrementBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    incrementBuilder.setWeight(4.0, "A", "F");
    incrementBuilder.setWeight(6.0, "C", "F");
    Factor increment = incrementBuilder.build(); 
    parametricFactor.incrementSufficientStatisticsFromMarginal(parameters, increment, Assignment.EMPTY, 3.0, 2.0);

    factor = parametricFactor.getModelFromParameters(parameters);
    assertEquals(-1.0, factor.getUnnormalizedLogProbability("B", "F"), TOLERANCE);
    assertEquals(1.0, factor.getUnnormalizedLogProbability("A", "T"), TOLERANCE);
    assertEquals(2.0, factor.getUnnormalizedLogProbability("C", "T"), TOLERANCE);
    assertEquals(6.0, factor.getUnnormalizedLogProbability("A", "F"), TOLERANCE);
    assertEquals(9.0, factor.getUnnormalizedLogProbability("C", "F"), TOLERANCE);

    TableFactor pointDist = TableFactor.logPointDistribution(truthVar, truthVar.outcomeArrayToAssignment("T"));
    parametricFactor.incrementSufficientStatisticsFromMarginal(parameters, pointDist,
        alphabetVar.outcomeArrayToAssignment("B"), 3, 2.0);
    factor = parametricFactor.getModelFromParameters(parameters);
    assertEquals(1.5, factor.getUnnormalizedLogProbability("B", "T"), TOLERANCE);
  }
  
  private void runTestRestrictedFeatures(DenseIndicatorLogLinearFactor parametricFactor) {
    SufficientStatistics parameters = parametricFactor.getNewSufficientStatistics();
    Factor initial = parametricFactor.getModelFromParameters(parameters);
    assertEquals(0.0, initial.getUnnormalizedLogProbability("A", "T"), TOLERANCE);
    assertEquals(0.0, initial.getUnnormalizedLogProbability("B", "T"), TOLERANCE);
    assertEquals(0.0, initial.getUnnormalizedLogProbability("C", "F"), TOLERANCE);

    parametricFactor.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("B", "F"), 2.0);
    parametricFactor.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("B", "F"), -3.0);
    parametricFactor.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("A", "T"), 1.0);
    parametricFactor.incrementSufficientStatisticsFromAssignment(parameters, vars.outcomeArrayToAssignment("C", "T"), 2.0);

    Factor factor = parametricFactor.getModelFromParameters(parameters);
    assertEquals(1.0, factor.getUnnormalizedLogProbability("A", "T"), TOLERANCE);
    assertEquals(-1.0, factor.getUnnormalizedLogProbability("B", "F"), TOLERANCE);
    assertEquals(0.0, factor.getUnnormalizedLogProbability("C", "T"), TOLERANCE);

    TableFactorBuilder incrementBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    incrementBuilder.setWeight(4.0, "A", "F");
    incrementBuilder.setWeight(6.0, "C", "F");
    Factor increment = incrementBuilder.build(); 
    parametricFactor.incrementSufficientStatisticsFromMarginal(parameters, increment, Assignment.EMPTY, 3.0, 2.0);

    factor = parametricFactor.getModelFromParameters(parameters);
    assertEquals(-1.0, factor.getUnnormalizedLogProbability("B", "F"), TOLERANCE);
    assertEquals(1.0, factor.getUnnormalizedLogProbability("A", "T"), TOLERANCE);
    assertEquals(0.0, factor.getUnnormalizedLogProbability("C", "T"), TOLERANCE);
    assertEquals(0.0, factor.getUnnormalizedLogProbability("A", "F"), TOLERANCE);
    assertEquals(9.0, factor.getUnnormalizedLogProbability("C", "F"), TOLERANCE);

    TableFactor pointDist = TableFactor.unity(truthVar);
    parametricFactor.incrementSufficientStatisticsFromMarginal(parameters, pointDist,
        alphabetVar.outcomeArrayToAssignment("C"), 3, 2.0);
    factor = parametricFactor.getModelFromParameters(parameters);
    assertEquals(0, factor.getUnnormalizedLogProbability("C", "T"), TOLERANCE);
    assertEquals(10.5, factor.getUnnormalizedLogProbability("C", "F"), TOLERANCE);
  }

  public void testIncrementDense() {
    runTestAllFeatures(new DenseIndicatorLogLinearFactor(vars, false, null));
  }
  
  public void testIncrementSparse() {
    runTestAllFeatures(new DenseIndicatorLogLinearFactor(vars, true, null));
  }
  
  public void testIncrementDenseRestricted() {
    runTestRestrictedFeatures(new DenseIndicatorLogLinearFactor(vars, false, featureIndicators));
  }

  public void testIncrementSparseRestricted() {
    runTestRestrictedFeatures(new DenseIndicatorLogLinearFactor(vars, true, featureIndicators));
  }
}
