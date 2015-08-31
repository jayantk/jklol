package com.jayantkrish.jklol.models.bayesnet;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.Lbfgs;
import com.jayantkrish.jklol.util.Assignment;

public class LogLinearCptFactorTest extends TestCase {

  LogLinearCptFactor f;  
  SufficientStatistics parameters;
  VariableNumMap vars;
  
  String[][] assignmentArray = new String[][] {{ "T", "T" },
      { "T", "T"},
      { "T", "F", },
      { "F", "F", },
      { "F", "T", }};
  List<Assignment> assignments;
  
  public void setUp() {
    DiscreteVariable v = new DiscreteVariable("Two values",
        Arrays.asList("T", "F" ));
    
    vars = new VariableNumMap(Arrays.asList(2, 3),
        Arrays.asList("v2", "v3"),
        Arrays.asList(v, v ));
    
    DiscreteLogLinearFactor factor = DiscreteLogLinearFactor.createIndicatorFactor(vars); 
    
    f = new LogLinearCptFactor(factor, VariableNumMap.EMPTY, new Lbfgs(10, 10, 0.0001, new DefaultLogFunction()));

    assignments = Lists.newArrayList(); 
    for (int i = 0; i < assignmentArray.length; i++) {
      Assignment assignment = f.getVars().outcomeToAssignment(assignmentArray[i]);
      assignments.add(assignment);
    }
  }

  public void testIncrementFromAssignment() {
    parameters = f.getNewSufficientStatistics();
    for (Assignment a : assignments) {
      f.incrementSufficientStatisticsFromAssignment(parameters, parameters, a, 1);
    }
    
    Factor predicted = f.getModelFromParameters(parameters);
    assertEquals(2.0 / 5, predicted.getUnnormalizedProbability("T", "T"), 0.001);
    assertEquals(1.0 / 5, predicted.getUnnormalizedProbability("T", "F"), 0.001);
  }
}
