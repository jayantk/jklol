package com.jayantkrish.jklol.models.dynamic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link VariableNumPattern}.
 * 
 * @author jayantk
 */
public class VariableNumPatternTest extends TestCase {
  
  private VariableNumMap fixedVars, plate1Vars, plate2Vars;
  private DynamicVariableSet vars;
  private VariableNumMap instantiatedVars;
  
  private VariableNumPattern fixedPattern, oneVarPattern, offsetPattern;
  private VariableNumMap oneVarPatternVars, offsetPatternVars;
  
  public void setUp() {
    DiscreteVariable varType = DiscreteVariable.sequence("discreteVariable", 2);
    fixedVars = new VariableNumMap(Ints.asList(2, 7), Arrays.asList("foo", "bar"),
        Arrays.asList(varType, varType));
    
    plate1Vars = new VariableNumMap(Ints.asList(0, 4), Arrays.asList("p1_1", "p1_2"),
        Arrays.asList(varType, varType));
    plate2Vars = new VariableNumMap(Ints.asList(1, 4), Arrays.asList("p2_1", "p2_2"),
        Arrays.asList(varType, varType));
    
    vars = DynamicVariableSet.fromVariables(fixedVars);
    vars = vars.addPlate("plate1", DynamicVariableSet.fromVariables(plate1Vars), 100);
    vars = vars.addPlate("plate2", DynamicVariableSet.fromVariables(plate2Vars), 100);
    
    DynamicAssignment plate1Assignment = DynamicAssignment.createPlateAssignment("plate1",
        Collections.nCopies(3, Assignment.EMPTY));
    DynamicAssignment plate2Assignment = DynamicAssignment.createPlateAssignment("plate2",
        Collections.nCopies(5, Assignment.EMPTY));
    instantiatedVars = vars.instantiateVariables(plate1Assignment.union(plate2Assignment));
    
    fixedPattern = VariableNumPattern.fromTemplateVariables(VariableNumMap.EMPTY, 
        fixedVars.intersection(2), vars);
    oneVarPattern = VariableNumPattern.fromTemplateVariables(VariableNumMap.singleton(0, "plate2/?(0)/p2_1", varType), 
        fixedVars.intersection(2), vars);
    oneVarPatternVars = fixedVars.intersection(2).union(
        VariableNumMap.singleton(0, "plate2/?(0)/p2_1", varType)); 
    
    offsetPatternVars = new VariableNumMap(Ints.asList(2, 3), 
        Arrays.asList("plate1/?(0)/p1_2", "plate1/?(-1)/p1_1"), Arrays.asList(varType, varType));
    offsetPattern = VariableNumPattern.fromTemplateVariables(offsetPatternVars,
        VariableNumMap.EMPTY, vars);
  }
  
  public void testMatchVariablesFixedOnly() {
    List<VariableMatch> matches = fixedPattern.matchVariables(instantiatedVars);
    assertEquals(1, matches.size());
    assertEquals(fixedVars.intersection(2), matches.get(0).getMatchedVariables());
  }
  
  public void testMatchVariables1() {
    List<VariableMatch> matches = oneVarPattern.matchVariables(instantiatedVars);
    assertEquals(5, matches.size());
    
    for (VariableMatch match : matches) {
      VariableNumMap matchedVars = match.getMatchedVariables();
      assertEquals(2, matchedVars.size());
      
      int otherVar = matchedVars.removeAll(2).getOnlyVariableNum();
      assertEquals(0, (int) match.getMappingToTemplate().getReplacementIndex(otherVar));
      assertEquals(oneVarPatternVars, match.getMappingToTemplate().apply(matchedVars));
    }
  }
  
  public void testMatchVariables2() {
    List<VariableMatch> matches = offsetPattern.matchVariables(instantiatedVars);
    assertEquals(2, matches.size());
    
    for (VariableMatch match : matches) {
      VariableNumMap matchedVars = match.getMatchedVariables();
      assertEquals(2, matchedVars.size());
      assertEquals(offsetPatternVars, match.getMappingToTemplate().apply(matchedVars));
    }
  }
  
  public void testMatchVariables3() {
    assertEquals(1, offsetPattern.matchVariables(instantiatedVars.removeAll(
        instantiatedVars.getVariableByName("plate1/1/p1_1"))).size());
  }
  
  public void testMatchEmpty() {
    assertEquals(0, fixedPattern.matchVariables(instantiatedVars.removeAll(2)).size());
    assertEquals(0, oneVarPattern.matchVariables(instantiatedVars.removeAll(2)).size());
  }
}
