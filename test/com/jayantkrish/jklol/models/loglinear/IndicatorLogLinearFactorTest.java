package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class IndicatorLogLinearFactorTest extends TestCase {
  
  private VariableNumMap vars; 
  private IndicatorLogLinearFactor factor;
  
  public void setUp() {
    DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
        Arrays.asList(new String[] {"T", "F"}));

    vars = new VariableNumMap(Ints.asList(0, 1, 2), Arrays.asList("X0", "X1", "Y"),
        Arrays.asList(tfVar, tfVar, tfVar));
    
    TableFactorBuilder sparsityBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    sparsityBuilder.setWeight(1.0, "T", "T", "T");
    sparsityBuilder.setWeight(1.0, "F", "T", "T");
    sparsityBuilder.setWeight(1.0, "T", "F", "T");
    sparsityBuilder.setWeight(1.0, "T", "T", "F");
    
    factor = new IndicatorLogLinearFactor(vars, sparsityBuilder.build());
  }
  
  public void testIncrementFromMarginal() {
    SufficientStatistics stats = factor.getNewSufficientStatistics();
    
    Factor marginal = factor.getModelFromParameters(stats);
    
    factor.incrementSufficientStatisticsFromMarginal(stats, stats, marginal, Assignment.EMPTY, 1.0, 1.0);

    Factor f = factor.getModelFromParameters(stats);
    assertEquals(Math.exp(1.0), f.getUnnormalizedProbability(vars.outcomeArrayToAssignment("T", "T", "T")));
    assertEquals(0.0, f.getUnnormalizedProbability(vars.outcomeArrayToAssignment("F", "F", "F")));
  }

}
