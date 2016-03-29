package com.jayantkrish.jklol.training;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.TensorBase;
import com.jayantkrish.jklol.util.Assignment;

public class StochasticGradientTrainerTest extends TestCase {

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
	  runTest(new StochasticGradientTrainer(100, 9, 1.0, true, false, Double.MAX_VALUE,
	      new DefaultLogFunction()));
	}

	public void testTrainL2() {
	  runTest(StochasticGradientTrainer.createWithL2Regularization(100, 3, 1, true, false,
	      Double.MAX_VALUE, 1, new DefaultLogFunction()));
	}
	
	public void testTrainStochasticL2() {
	  runTest(StochasticGradientTrainer.createWithStochasticL2Regularization(100, 3, 0.01, true, false,
	      Double.MAX_VALUE, 1, 0.1, new DefaultLogFunction()));
	}
	
	public void testTrainL2Clipped() {
	  runTest(StochasticGradientTrainer.createWithL2Regularization(100, 3, 1, true, false,
	      0.5, 1, new DefaultLogFunction()));
	}

	public void testTrainAdagrad() {
	  runTest(StochasticGradientTrainer.createAdagrad(100, 3, 0.01, true, false, Double.MAX_VALUE,
	      0, 0, new DefaultLogFunction()));
	}

	public void testTrainAdagradL2() {
	  runTest(StochasticGradientTrainer.createAdagrad(100, 3, 0.01, true, false, Double.MAX_VALUE,
	      1, 1, new DefaultLogFunction()));
	}

	public void testTrainAdagradStochasticL2() {
	  runTest(StochasticGradientTrainer.createAdagrad(100, 3, 0.01, true, false, Double.MAX_VALUE,
	      1, 0.1, new DefaultLogFunction()));
	}

	public void testL2Sag() {
	  runTest(new StochasticAverageGradientOptimizer(100, 0.01, new DefaultLogFunction()));
	}

	public void testTrainL1() {
	  runTest(StochasticGradientTrainer.createWithL1Regularization(100, 3, 0.01, true, false,
	      Double.MAX_VALUE, 0.1, new DefaultLogFunction()));
	}
	
	public void testTrainL2Averaged() {
	  runTest(StochasticGradientTrainer.createWithL2Regularization(100, 3, 0.01, true, true,
	      Double.MAX_VALUE, 1, new DefaultLogFunction()));
	}
	
	public void testTrainStochasticL2Averaged() {
	  runTest(StochasticGradientTrainer.createWithStochasticL2Regularization(100, 3, 0.01,
	      true, true, Double.MAX_VALUE, 1, 0.1, new DefaultLogFunction()));
	}

	public void testTrainL1Averaged() {
	  runTest(StochasticGradientTrainer.createWithL1Regularization(100, 3, 0.01, true, true,
	      Double.MAX_VALUE, 0.1, new DefaultLogFunction()));
	}

	private void runTest(GradientOptimizer trainer) {
	  		// These assignments should have positive weight for clique 1
		Set<Assignment> clique1PositiveAssignments = new HashSet<Assignment>();
		clique1PositiveAssignments.add(allVariables.getVariablesByName(clique1Names)
		    .outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "T"})));
		clique1PositiveAssignments.add(allVariables.getVariablesByName(clique1Names)
		    .outcomeToAssignment(Arrays.asList(new String[] {"F", "F", "F"})));

		Set<Assignment> clique2NegativeAssignments = new HashSet<Assignment>();
		clique2NegativeAssignments.add(allVariables.getVariablesByName(clique2Names)
		    .outcomeToAssignment(Arrays.asList(new String[] {"F", "T"})));

		LoglikelihoodOracle oracle = new LoglikelihoodOracle(logLinearModel, new JunctionTree());
		SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(), trainingData);
		
		System.out.println(logLinearModel.getParameterDescription(parameters));

		List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
		for (int i = 0; i < parameterList.size(); i++) {
		  DiscreteLogLinearFactor factor = (DiscreteLogLinearFactor) logLinearModel.getParametricFactors().get(i);
		  SufficientStatistics stats = parameterList.get(i);
		  
		  DiscreteFactor featureValues = factor.getFeatureValues();
		  VariableNumMap featureVariable = featureValues.getVars().removeAll(factor.getVars());
		  TensorBase weights = ((TensorSufficientStatistics) stats).get();
		  for (int j = 0; j < weights.size(); j++) {
		    
		    Assignment a = featureValues.conditional(featureVariable.intArrayToAssignment(new int[] {j}))
		        .outcomeIterator().next().getAssignment();
		    if (a.getVariableNums().size() == 3) {
		      assertTrue(clique1PositiveAssignments.contains(a) ||
		          weights.getByDimKey(j) < 0.0);
		    } else {
		      assertTrue(clique2NegativeAssignments.contains(a) ||
		          weights.getByDimKey(j) > -1.0);
		    }
		  }
		}
	}
}
