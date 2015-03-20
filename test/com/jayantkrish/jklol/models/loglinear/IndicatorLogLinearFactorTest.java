package com.jayantkrish.jklol.models.loglinear;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class IndicatorLogLinearFactorTest extends TestCase {
  
  private IndicatorLogLinearFactor factor;
  private ParametricFactorGraph factorGraph;
  
  public void setUp() {
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
        Arrays.asList(new String[] {"T", "F"}));

    builder.addVariable("X0", tfVar);
    builder.addVariable("X1", tfVar);
    builder.addVariable("Y", tfVar);

    VariableNumMap vars = builder.getVariables().getVariablesByName("X0", "X1", "Y");
    
    TableFactorBuilder sparsityBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    sparsityBuilder.setWeight(1.0, "T", "T", "T");
    sparsityBuilder.setWeight(1.0, "F", "T", "T");
    sparsityBuilder.setWeight(1.0, "T", "F", "T");
    sparsityBuilder.setWeight(1.0, "T", "T", "F");
    
    factor = new IndicatorLogLinearFactor(vars, sparsityBuilder.build());
		builder.addUnreplicatedFactor("f0", factor);

		factorGraph = builder.build();
  }
  
  public void testIncrementFromMarginal() {
    SufficientStatistics stats = factor.getNewSufficientStatistics();
    
    Factor marginal = factor.getModelFromParameters(stats);
    
    factor.incrementSufficientStatisticsFromMarginal(stats, stats, marginal, Assignment.EMPTY, 1.0, 1.0);

    // TODO: implement a test case.
  }

}
