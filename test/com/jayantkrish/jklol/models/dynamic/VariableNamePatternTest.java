package com.jayantkrish.jklol.models.dynamic;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;

/**
 * Unit tests for {@link VariableNamePattern}.
 * 
 * @author jayantk
 */
public class VariableNamePatternTest extends TestCase {

  private VariablePattern twoVarPattern, emptyPattern, noMatchPattern, offsetPattern;
  private VariableNumMap templateVars, offsetVars, defaultVars, testVars;
  
  public void setUp() {
    DiscreteVariable tfVar = new DiscreteVariable("Three values",
        Arrays.asList(new String[] {"T", "F", "U"}));
    ObjectVariable intVar = new ObjectVariable(Integer.class);
    
    templateVars = new VariableNumMap(Ints.asList(5, 6), 
        Arrays.asList("x-?(0)", "y-?(0)"), Arrays.<Variable>asList(tfVar, intVar));
    offsetVars = new VariableNumMap(Ints.asList(5, 6), 
        Arrays.asList("x-?(0)", "x-?(1)"), Arrays.<Variable>asList(tfVar, intVar));
    defaultVars = new VariableNumMap(Ints.asList(0, 1, 2, 3, 4), 
        Arrays.asList("x-0", "y-0", "x-1", "y-1", "z"),
        Arrays.<Variable>asList(tfVar, intVar, tfVar, intVar, tfVar));    
    testVars = new VariableNumMap(Ints.asList(0, 1, 2, 3, 4), 
        Arrays.asList("x-0", "y-0", "x-1", "y-2", "z-0"),
        Arrays.<Variable>asList(tfVar, intVar, tfVar, intVar, tfVar));
    
    twoVarPattern = VariableNamePattern.fromTemplateVariables(templateVars, 
        defaultVars.getVariablesByName("z"));
    noMatchPattern = VariableNamePattern.fromTemplateVariables(templateVars, 
        templateVars.getVariablesByName("x-?(0)"));
    offsetPattern = VariableNamePattern.fromTemplateVariables(offsetVars, 
        VariableNumMap.EMPTY);
    emptyPattern = VariableNamePattern.fromTemplateVariables(VariableNumMap.EMPTY, 
        defaultVars.getVariablesByName("x-0", "y-0", "z"));
  }

  public void testMatchVariablesEmpty() {
    List<VariableMatch> matches = emptyPattern.matchVariables(defaultVars);
    assertEquals(1, matches.size());
    assertEquals(defaultVars.getVariablesByName("x-0", "y-0", "z"), 
        matches.get(0).getMatchedVariables());
  }
  
  public void testMatchVariablesNoMatch() {
    List<VariableMatch> matches = twoVarPattern.matchVariables(testVars);
    assertEquals(1, matches.size());
    assertEquals(defaultVars.getVariablesByName("x-0", "y-0", "z"), 
        matches.get(0).getMatchedVariables());
  }
  
  public void testMatchVariablesNoMatch2() {
    List<VariableMatch> matches = noMatchPattern.matchVariables(defaultVars);
    assertEquals(0, matches.size());
  }
  
  public void testMatchVariables() {
    List<VariableMatch> matches = twoVarPattern.matchVariables(defaultVars);
    assertEquals(2, matches.size());
    assertEquals(defaultVars.getVariablesByName("x-0", "y-0", "z"), 
        matches.get(0).getMatchedVariables());
    assertEquals(defaultVars.getVariablesByName("x-1", "y-1", "z"), 
        matches.get(1).getMatchedVariables());
  }
  
  public void testMatchVariablesOffset() {
    List<VariableMatch> matches = offsetPattern.matchVariables(defaultVars);
    assertEquals(1, matches.size());
    assertEquals(defaultVars.getVariablesByName("x-0", "x-1"), 
        matches.get(0).getMatchedVariables());
  }
}
