package com.jayantkrish.jklol.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.models.loglinear.IndicatorFeatureFunction;
import com.jayantkrish.jklol.models.loglinear.LogLinearModelBuilder;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

public class StochasticGradientTrainerTest extends TestCase {

	ParametricFactorGraph logLinearModel;
	StochasticGradientTrainer t;    
	List<String> clique1Names;
	List<String> clique2Names;

	List<Assignment> trainingData;

	public void setUp() {
		LogLinearModelBuilder builder = new LogLinearModelBuilder();

		DiscreteVariable tfVar = new DiscreteVariable("TrueFalse",
				Arrays.asList(new String[] {"T", "F"}));

		builder.addDiscreteVariable("Var0", tfVar);
		builder.addDiscreteVariable("Var1", tfVar);
		builder.addDiscreteVariable("Var2", tfVar);
		builder.addDiscreteVariable("Var3", tfVar);

		clique1Names = Arrays.asList("Var0", "Var1", "Var2");
		VariableNumMap clique1Vars = builder.lookupVariables(clique1Names); 
		List<FeatureFunction> clique1Features = Lists.newArrayList();
		Iterator<Assignment> iter = new AllAssignmentIterator(clique1Vars);
		while (iter.hasNext()) {
		  clique1Features.add(new IndicatorFeatureFunction(iter.next()));
		}
		builder.addFactor(new DiscreteLogLinearFactor(clique1Vars, clique1Features));
		
		clique2Names = Arrays.asList("Var2", "Var3");
		VariableNumMap clique2Vars = builder.lookupVariables(clique2Names); 
		List<FeatureFunction> clique2Features = Lists.newArrayList();
		iter = new AllAssignmentIterator(clique2Vars);
		while (iter.hasNext()) {
		  clique2Features.add(new IndicatorFeatureFunction(iter.next()));
		}
		builder.addFactor(new DiscreteLogLinearFactor(clique2Vars, clique2Features));

		logLinearModel = builder.build();
		trainingData = new ArrayList<Assignment>();
		Assignment a1 = logLinearModel.getVariables()
		    .outcomeToAssignment(Arrays.asList("T", "T", "T", "T"));
		Assignment a2 = logLinearModel.getVariables()
		    .outcomeToAssignment(Arrays.asList("T", "T", "T", "F"));
		Assignment a3 = logLinearModel.getVariables()
		    .outcomeToAssignment(Arrays.asList("F", "F", "F", "F"));
		for (int i = 0; i < 3; i++) {
			trainingData.add(a1);
			trainingData.add(a2);
			trainingData.add(a3);
		}
		t = new StochasticGradientTrainer(new JunctionTree(), 10);
	}

	public void testTrain() {
		// These assignments should have positive weight for clique 1
		Set<Assignment> clique1PositiveAssignments = new HashSet<Assignment>();
		clique1PositiveAssignments.add(logLinearModel.lookupVariables(clique1Names)
		    .outcomeToAssignment(Arrays.asList(new String[] {"T", "T", "T"})));
		clique1PositiveAssignments.add(logLinearModel.lookupVariables(clique1Names)
		    .outcomeToAssignment(Arrays.asList(new String[] {"F", "F", "F"})));

		Set<Assignment> clique2NegativeAssignments = new HashSet<Assignment>();
		clique2NegativeAssignments.add(logLinearModel.lookupVariables(clique2Names)
		    .outcomeToAssignment(Arrays.asList(new String[] {"F", "T"})));

		SufficientStatistics parameters = t.train(logLinearModel, logLinearModel.getNewSufficientStatistics(), trainingData);

		List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
		for (SufficientStatistics stats : parameterList) {
		  List<FeatureFunction> features = stats.coerceToFeature().getFeatures();
		  double[] weights = stats.coerceToFeature().getWeights();
		  for (int i = 0; i < features.size(); i++) {
		    FeatureFunction feat = features.get(i);
		    Assignment a = feat.getNonzeroAssignments().next();
		    if (a.getVarNumsSorted().size() == 3) {
		      assertTrue(clique1PositiveAssignments.contains(a) ||
		          weights[i] < 0.0);
		    } else {
		      assertTrue(clique2NegativeAssignments.contains(a) ||
		          weights[i] > -1.0);
		    }    		    
		  }
		}
	}
}
