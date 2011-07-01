import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.*;
import junit.framework.*;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class JunctionTreeTest extends TestCase {

    FactorGraph f;
    FactorGraph f2;
    JunctionTree t;
    JunctionTree t2;

    TableFactor factor1;
    TableFactor factor2;
    TableFactor factor3;

    public void setUp() {
	f = new FactorGraph();

	Variable<String> tfVar = new Variable<String>("Three values",
		Arrays.asList(new String[] {"T", "F", "U"}));

	Variable<String> otherVar = new Variable<String>("Two values",
		Arrays.asList(new String[] {"foo", "bar"}));

	f.addVariable("Var0", tfVar);
	f.addVariable("Var1", otherVar);
	f.addVariable("Var2", tfVar);
	f.addVariable("Var3", tfVar);
	f.addVariable("Var4", tfVar);

	factor1 = f.addTableFactor(Arrays.asList(new String[] {"Var0", "Var2", "Var3"}));
	factor1.setWeightList(Arrays.asList(new String[] {"T", "T", "T"}), 1.0);
	factor1.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 1.0);
	factor1.setWeightList(Arrays.asList(new String[] {"U", "F", "F"}), 2.0);
	
	factor2 = f.addTableFactor(Arrays.asList(new String[] {"Var2", "Var1"}));
	factor2.setWeightList(Arrays.asList(new String[] {"foo", "F"}), 2.0);
	factor2.setWeightList(Arrays.asList(new String[] {"foo", "T"}), 3.0);
	factor2.setWeightList(Arrays.asList(new String[] {"bar", "T"}), 2.0);
	factor2.setWeightList(Arrays.asList(new String[] {"bar", "F"}), 1.0);

	factor3 = f.addTableFactor(Arrays.asList(new String[] {"Var3", "Var4"}));
	factor3.setWeightList(Arrays.asList(new String[] {"F", "U"}), 2.0);
	factor3.setWeightList(Arrays.asList(new String[] {"T", "U"}), 2.0);
	factor3.setWeightList(Arrays.asList(new String[] {"T", "F"}), 3.0);
	t = new JunctionTree();
	t.setFactorGraph(f);

	f2 = new FactorGraph();

	f2.addVariable("Var0", tfVar);
	f2.addVariable("Var1", tfVar);
	f2.addVariable("Var2", tfVar);

	TableFactor tf = f2.addTableFactor(Arrays.asList(new String[] {"Var0", "Var1"}));
	tf.setWeightList(Arrays.asList(new String[] {"F", "U"}), 2.0);
	tf.setWeightList(Arrays.asList(new String[] {"T", "U"}), 2.0);
	tf.setWeightList(Arrays.asList(new String[] {"T", "F"}), 3.0);

	tf = f2.addTableFactor(Arrays.asList(new String[] {"Var0", "Var2"}));
	tf.setWeightList(Arrays.asList(new String[] {"F", "U"}), 2.0);
	tf.setWeightList(Arrays.asList(new String[] {"T", "U"}), 2.0);
	tf.setWeightList(Arrays.asList(new String[] {"T", "F"}), 3.0);

	tf = f2.addTableFactor(Arrays.asList(new String[] {"Var0"}));
	tf.setWeightList(Arrays.asList(new String[] {"F"}), 2.0);
	tf.setWeightList(Arrays.asList(new String[] {"T"}), 1.0);
	tf.setWeightList(Arrays.asList(new String[] {"U"}), 1.0);
	
	t2 = new JunctionTree();
	t2.setFactorGraph(f2);
    }

    public void testMarginals() {
	t.computeMarginals();

	Factor m = t.getMarginal(Arrays.asList(new Integer[] {1}));
	
	assertEquals(27.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"foo"})));

	assertEquals(16.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"bar"})));

	m = t.getMarginal(Arrays.asList(new Integer[] {0,2}));

	assertEquals(25.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));

	assertEquals(6.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));

	assertEquals(12.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"U", "F"})));

	assertEquals(0.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"U", "U"})));
    }

    public void testConditionals() {
	t.computeMarginals(f.outcomeToAssignment(Arrays.asList(new String[]{"Var2"}),
			Arrays.asList(new String[]{"F"})));

	Factor m = t.getMarginal(Arrays.asList(new Integer[] {1}));
	assertEquals(12.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"foo"})));
	assertEquals(6.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"bar"})));

	m = t.getMarginal(Arrays.asList(new Integer[] {3, 4}));	
	assertEquals(18.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"F", "U"})));
	assertEquals(0.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));
	
	m = t.getMarginal(Arrays.asList(new Integer[] {0, 2, 3}));
	assertEquals(0.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T", "T"})));
	assertEquals(6.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F", "F"})));
	assertEquals(12.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"U", "F", "F"})));
    }

    public void testMaxMarginals() {
	t.computeMaxMarginals();

	Factor m = t.getMarginal(Arrays.asList(new Integer[] {1}));	
	System.out.println(m);
	assertEquals(6.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"bar"})));
	assertEquals(9.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"foo"})));

	m = t.getMarginal(Arrays.asList(new Integer[] {0,2}));
	System.out.println(m);
	assertEquals(9.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "T"})));

	assertEquals(4.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T", "F"})));

	assertEquals(8.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"U", "F"})));

	assertEquals(0.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"U", "U"})));
    }


    public void testNonTreeStructured() {
	t2.computeMarginals();

	Factor m = t2.getMarginal(Arrays.asList(new Integer[] {0}));
	assertEquals(8.0, 
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"F"})));
	assertEquals(25.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T"})));
	assertEquals(0.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"U"})));
    }

    public void testOrdering() {
	t2 = new JunctionTree(new int[] {2, 1, 0});
	t2.setFactorGraph(f2);
	t2.computeMarginals();

	Factor m = t2.getMarginal(Arrays.asList(new Integer[] {0}));
	assertEquals(8.0, 
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"F"})));
	assertEquals(25.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"T"})));
	assertEquals(0.0,
		m.getUnnormalizedProbability(Arrays.asList(new String[] {"U"})));	
    }

}

    