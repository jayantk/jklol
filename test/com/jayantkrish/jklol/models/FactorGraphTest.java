import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.Assignment;
import junit.framework.*;

import java.util.*;

public class FactorGraphTest extends TestCase {

    private FactorGraph f;

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

	f.addTableFactor(Arrays.asList(new String[] {"Var0", "Var2", "Var3"}));
	
	f.addTableFactor(Arrays.asList(new String[] {"Var2", "Var1"}));
    }

    public void testGetFactorsWithVariable() {
	assertEquals(2,
		f.getFactorsWithVariable(f.getVariableIndex("Var2")).size());

	assertEquals(1,
		f.getFactorsWithVariable(f.getVariableIndex("Var3")).size());
    }

    public void testGetSharedVariables() {
	List<Integer> shared = new ArrayList<Integer>(f.getSharedVariables(0, 1));
	assertEquals(1, shared.size());
	assertEquals(2, (int) shared.get(0));
    }

    public void testAssignmentIterator() {
	Iterator<Assignment> assignmentIter = f.assignmentIterator(Arrays.asList(new String[] {"Var2", "Var1", "Var3"}));
	
	List<Integer> varNumOrder = Arrays.asList(new Integer[] {1,2,3});

	Set<Assignment> returnedValues = new HashSet<Assignment>();
	while (assignmentIter.hasNext()) {
	    Assignment a = assignmentIter.next();
	    assertEquals(varNumOrder, a.getVarNumsSorted());
	    returnedValues.add(a);
	}

	assertEquals(18, returnedValues.size());
    }
    

}
