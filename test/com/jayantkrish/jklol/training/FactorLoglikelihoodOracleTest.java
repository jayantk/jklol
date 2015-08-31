package com.jayantkrish.jklol.training;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class FactorLoglikelihoodOracleTest extends TestCase {

  ParametricFactor factor;
  Factor target1,target2;
  VariableNumMap var0, var1;
  
  public void setUp() {
    DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));
    DiscreteVariable abcVar = new DiscreteVariable("Abc",
				Arrays.asList(new String[] {"A", "B", "C"}));
    
    var0 = VariableNumMap.singleton(0, "tf", tfVar);
    var1 = VariableNumMap.singleton(1, "abc", abcVar);
    VariableNumMap vars = var0.union(var1);

    factor = IndicatorLogLinearFactor.createDenseFactor(vars);
    target1 = TableFactor.pointDistribution(vars, vars.outcomeArrayToAssignment("T", "A"));
    target2 = TableFactor.pointDistribution(vars, vars.outcomeArrayToAssignment("T", "A"),
        vars.outcomeArrayToAssignment("F", "B"), vars.outcomeArrayToAssignment("T", "C"),
        vars.outcomeArrayToAssignment("F", "C"));
  }
  
  public void testLbfgs() {
    Lbfgs lbfgs = new Lbfgs(10, 10, 0.001, new DefaultLogFunction(1, false));
    FactorLoglikelihoodOracle oracle = new FactorLoglikelihoodOracle(factor, target1, VariableNumMap.EMPTY);
    SufficientStatistics parameters = lbfgs.train(oracle, factor.getNewSufficientStatistics(),
        Arrays.asList((Void) null));
    
    Factor predicted = factor.getModelFromParameters(parameters);
    Factor predictedMarginal = predicted.product(1.0 / predicted.getTotalUnnormalizedProbability());
    
    // Inner product of the weights should be very close to 1
    double innerProd = predictedMarginal.product(target1).getTotalUnnormalizedProbability();
    assertEquals(1.0, innerProd, 0.01);
  }
  
  public void testLbfgsConditional() {
    Lbfgs lbfgs = new Lbfgs(10, 10, 0.001, new DefaultLogFunction(1, false));
    FactorLoglikelihoodOracle oracle = new FactorLoglikelihoodOracle(factor, target2, var1);
    SufficientStatistics parameters = lbfgs.train(oracle, factor.getNewSufficientStatistics(),
        Arrays.asList((Void) null));

    Factor predicted = factor.getModelFromParameters(parameters);
    Factor predictedMarginal = predicted.product(predicted.marginalize(var0).inverse());
    Factor targetMarginal = target2.product(target2.marginalize(var0).inverse());

    // Difference between the weights should be very close to 0
    double delta = predictedMarginal.add(targetMarginal.product(-1.0)).getTotalUnnormalizedProbability();
    assertEquals(0.0, delta, 0.01);
  }
}
