package com.jayantkrish.jklol.models.dynamic;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link DynamicVariableSet}.
 * 
 * @author jayantk
 */
public class DynamicVariableSetTest extends TestCase {

  DynamicVariableSet twoVar, threeVar, oneLevel;
  DynamicAssignment twoVarAssignment, threeVarAssignment, oneLevelAssignment1, oneLevelAssignment2;
  
  @SuppressWarnings("unchecked")
  public void setUp() {
    Variable var = new DiscreteVariable("tf", Arrays.asList("T", "F"));
    twoVar = DynamicVariableSet.fromVariables(VariableNumMap.fromVariableNames(
        Arrays.asList("x0", "x1"), Arrays.asList(var, var)));
    threeVar = DynamicVariableSet.fromVariables(VariableNumMap.fromVariableNames(
        Arrays.asList("v0", "v1", "v2"), Arrays.asList(var, var, var)));
    
    oneLevel = twoVar.addPlate("plate1", threeVar).addPlate("plate2", twoVar);
      
    twoVarAssignment = twoVar.outcomeToAssignment("T", "F");
    threeVarAssignment = threeVar.outcomeToAssignment("F", "T", "F");
    
    oneLevelAssignment1 = oneLevel.plateOutcomeToAssignment(Arrays.asList(threeVarAssignment, threeVarAssignment), 
        Arrays.asList(twoVarAssignment, DynamicAssignment.EMPTY, twoVarAssignment));
    oneLevelAssignment2 = oneLevel.plateOutcomeToAssignment(Arrays.asList(DynamicAssignment.EMPTY, DynamicAssignment.EMPTY), 
        Arrays.asList(DynamicAssignment.EMPTY, DynamicAssignment.EMPTY, DynamicAssignment.EMPTY));
  }
  
  public void testInstantiateVariablesSimple() {
    VariableNumMap vars = twoVar.instantiateVariables(twoVarAssignment);
    assertEquals(Arrays.asList("x0", "x1"), vars.getVariableNames());
  }
  
  public void testInstantiateVariablesRecursive() {
    VariableNumMap vars1 = oneLevel.instantiateVariables(oneLevelAssignment1);
    VariableNumMap vars2 = oneLevel.instantiateVariables(oneLevelAssignment2);
    assertEquals(14, vars1.size());
    assertEquals(14, vars2.size());    
    assertEquals(vars1, vars2);    
  }

  public void testToAssignment() {
    VariableNumMap vars1 = oneLevel.instantiateVariables(oneLevelAssignment1);
    Assignment assignment = oneLevel.toAssignment(oneLevelAssignment1);
    assertTrue(vars1.isValidAssignment(assignment));
    
    assertEquals("F", assignment.getValue(vars1.getVariableByName("plate2/0/x1")));
    assertFalse(assignment.contains(vars1.getVariableByName("plate2/1/x1")));
  }
  
  public void testToDynamicAssignment() {
    VariableNumMap vars1 = oneLevel.instantiateVariables(oneLevelAssignment1);
    Assignment assignment = oneLevel.toAssignment(oneLevelAssignment1);
    
    assertEquals(oneLevelAssignment1, oneLevel.toDynamicAssignment(assignment, vars1));
    assertEquals(oneLevelAssignment2, oneLevel.toDynamicAssignment(Assignment.EMPTY, vars1));
  }
}
