import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.loglinear.FeatureFunction;
import com.jayantkrish.jklol.models.loglinear.LogLinearParameters;
import com.jayantkrish.jklol.models.loglinear.IndicatorFeatureFunction;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.LogLinearModel;
import com.jayantkrish.jklol.models.loglinear.LogLinearModelBuilder;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;

public class StochasticGradientTrainerTest extends TestCase {

	LogLinearModel logLinearModel;
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

		clique1Names = Arrays.asList(new String[] {"Var0", "Var1", "Var2"});
		DiscreteLogLinearFactor l1 = builder.addLogLinearFactor(clique1Names);

		clique2Names = Arrays.asList(new String[] {"Var2", "Var3"});
		DiscreteLogLinearFactor l2 = builder.addLogLinearFactor(clique2Names);

		Iterator<Assignment> assignmentIter = l1.outcomeIterator();
		while (assignmentIter.hasNext()) {
			l1.addFeature(new IndicatorFeatureFunction(assignmentIter.next()));
		}
		assignmentIter = l2.outcomeIterator();
		while (assignmentIter.hasNext()) {
			l2.addFeature(new IndicatorFeatureFunction(assignmentIter.next()));
		}

		List<String> allVarNames = Arrays.asList(new String[] {"Var0", "Var1", "Var2", "Var3"});

		logLinearModel = builder.build();
		trainingData = new ArrayList<Assignment>();
		Assignment a1 = logLinearModel.outcomeToAssignment(allVarNames,
				Arrays.asList(new String[] {"T", "T", "T", "T"}));
		Assignment a2 = logLinearModel.outcomeToAssignment(allVarNames,
				Arrays.asList(new String[] {"T", "T", "T", "F"}));
		Assignment a3 = logLinearModel.outcomeToAssignment(allVarNames,
				Arrays.asList(new String[] {"F", "F", "F", "F"}));
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
		clique1PositiveAssignments.add(logLinearModel.outcomeToAssignment(clique1Names,
				Arrays.asList(new String[] {"T", "T", "T"})));
		clique1PositiveAssignments.add(logLinearModel.outcomeToAssignment(clique1Names,
				Arrays.asList(new String[] {"F", "F", "F"})));

		Set<Assignment> clique2NegativeAssignments = new HashSet<Assignment>();
		clique2NegativeAssignments.add(logLinearModel.outcomeToAssignment(clique2Names,
				Arrays.asList(new String[] {"F", "T"})));

		t.train(logLinearModel, trainingData);
		LogLinearParameters fs = logLinearModel.getFeatureSet();
		for (FeatureFunction feat : fs.getFeatures()) {
			// System.out.println(fs.getFeatureWeight(feat) + ": " + feat);
			Assignment a = feat.getNonzeroAssignments().next();
			if (a.getVarNumsSorted().size() == 3) {
				assertTrue(clique1PositiveAssignments.contains(a) ||
						fs.getFeatureWeight(feat) < 0.0);
			} else {
				assertTrue(clique2NegativeAssignments.contains(a) ||
						fs.getFeatureWeight(feat) > -1.0);
			}
		}
	}
}
