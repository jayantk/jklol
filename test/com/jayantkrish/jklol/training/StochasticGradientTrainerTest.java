import com.jayantkrish.jklol.models.*;
import com.jayantkrish.jklol.inference.*;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

import java.util.*;

import junit.framework.*;

public class StochasticGradientTrainerTest extends TestCase {

    LogLinearModel f;
    StochasticGradientTrainer t;    

    List<Assignment> trainingData;
    Set<Assignment> positiveFeatureAssignments;
    Assignment factor2LeastProbable;

    public void setUp() {
	f = new LogLinearModel();

	Variable<String> tfVar = new Variable<String>("TrueFalse",
		Arrays.asList(new String[] {"T", "F"}));

	f.addVariable("Var0", tfVar);
	f.addVariable("Var1", tfVar);
	f.addVariable("Var2", tfVar);
	f.addVariable("Var3", tfVar);

	List<String> clique1Names = Arrays.asList(new String[] {"Var0", "Var1", "Var2"});
	LogLinearFactor l1 = f.addLogLinearFactor(clique1Names);

	List<String> clique2Names = Arrays.asList(new String[] {"Var2", "Var3"});
	LogLinearFactor l2 = f.addLogLinearFactor(clique2Names);

	Iterator<Assignment> assignmentIter = f.assignmentIterator(clique1Names);
	while (assignmentIter.hasNext()) {
	    l1.addFeature(new IndicatorFeatureFunction(assignmentIter.next()));
	}
	assignmentIter = f.assignmentIterator(clique2Names);
	while (assignmentIter.hasNext()) {
	    l2.addFeature(new IndicatorFeatureFunction(assignmentIter.next()));
	}

	List<String> allVarNames = Arrays.asList(new String[] {"Var0", "Var1", "Var2", "Var3"});

	trainingData = new ArrayList<Assignment>();
	Assignment a1 = f.outcomeToAssignment(allVarNames,
		Arrays.asList(new String[] {"T", "T", "T", "T"}));
	Assignment a2 = f.outcomeToAssignment(allVarNames,
		Arrays.asList(new String[] {"T", "T", "T", "F"}));
	Assignment a3 = f.outcomeToAssignment(allVarNames,
		Arrays.asList(new String[] {"F", "F", "F", "F"}));
	for (int i = 0; i < 3; i++) {
	    trainingData.add(a1);
	    trainingData.add(a2);
	    trainingData.add(a3);
	}

	/*
	Set<Assignment> positiveFeatureAssignments = new HashSet<Assignment>();
	positiveFeatureAssignments.add(f.outcomeToAssignment(clique1Names,
			Arrays.asList(new String[] {"T", "T", "T"})));
	positiveFeatureAssignments.add(f.outcomeToAssignment(clique1Names,
			Arrays.asList(new String[] {"F", "F", "F"})));
	factor2LeastProbable = f.outcomeToAssignment(clique2Names,
		Arrays.asList(new String[] {"F", "T"}));
	*/

	t = new StochasticGradientTrainer(new JunctionTree(), 10);
    }

    public void testTrain() {
	t.train(f, trainingData);
	FeatureSet fs = f.getFeatureSet();
	for (FeatureFunction feat : fs.getFeatures()) {
	    // feat.getNonzeroAssignments().next()
	    System.out.println(fs.getFeatureWeight(feat) + ": " + feat);
	}
    }


}