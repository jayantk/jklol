package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class LbfgsTest extends TestCase {
	ParametricFactorGraph logLinearModel;
	List<String> clique1Names;
	List<String> clique2Names;
	VariableNumMap allVariables;

	List<Example<DynamicAssignment, DynamicAssignment>> trainingData;

	public void setUp() {
		ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addVariable("Var0", tfVar);
		builder.addVariable("Var1", tfVar);
		builder.addVariable("Var2", tfVar);
		builder.addVariable("Var3", tfVar);
		allVariables = builder.getVariables();

		clique1Names = Arrays.asList("Var0", "Var1", "Var2");
		builder.addUnreplicatedFactor("f0", DiscreteLogLinearFactor
		    .createIndicatorFactor(builder.getVariables().getVariablesByName(clique1Names)));
		
		clique2Names = Arrays.asList("Var2", "Var3");
		builder.addUnreplicatedFactor("f1", DiscreteLogLinearFactor
		    .createIndicatorFactor(builder.getVariables().getVariablesByName(clique2Names)));

		logLinearModel = builder.build();
		trainingData = Lists.newArrayList();
		DynamicAssignment a1 = logLinearModel.getVariables()
		    .fixedVariableOutcomeToAssignment(Arrays.asList("T", "T", "T", "T"));
		DynamicAssignment a2 = logLinearModel.getVariables()
		    .fixedVariableOutcomeToAssignment(Arrays.asList("T", "T", "T", "F"));
		DynamicAssignment a3 = logLinearModel.getVariables()
		    .fixedVariableOutcomeToAssignment(Arrays.asList("F", "F", "F", "F"));
		for (int i = 0; i < 3; i++) {
			trainingData.add(Example.create(DynamicAssignment.EMPTY, a1));
			trainingData.add(Example.create(DynamicAssignment.EMPTY, a2));
			trainingData.add(Example.create(DynamicAssignment.EMPTY, a3));
		}
	}
	
	public void testTrainUnregularized() {
	  Lbfgs lbfgs = new Lbfgs(100, 10, 0.0, new DefaultLogFunction(1, false));

	  LoglikelihoodOracle oracle = new LoglikelihoodOracle(logLinearModel, new JunctionTree());
		SufficientStatistics parameters = lbfgs.train(oracle, oracle.initializeGradient(), trainingData);
		
		System.out.println(logLinearModel.getParameterDescription(parameters));
	}
}
