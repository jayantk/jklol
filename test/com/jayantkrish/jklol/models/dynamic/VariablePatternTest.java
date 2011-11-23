package com.jayantkrish.jklol.models.dynamic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.IntegerVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.VariablePattern.VariableMatch;

/**
 * Unit tests for {@link VariablePattern}.
 * 
 * @author jayantk
 */
public class VariablePatternTest extends TestCase {

  private VariablePattern twoVarPattern, emptyPattern, adjacentPattern;
  private VariableNumMap defaultVars;
  
  public void setUp() {
    DiscreteVariable tfVar = new DiscreteVariable("Three values",
        Arrays.asList(new String[] {"T", "F", "U"}));
    IntegerVariable intVar = new IntegerVariable();
    
    defaultVars = new VariableNumMap(Ints.asList(0, 1, 2, 3), 
        Arrays.asList("x0", "y0", "x1", "y1"),
        Arrays.<Variable>asList(tfVar, intVar, tfVar, intVar));
    
    twoVarPattern = new VariablePattern(Arrays.asList("x", "y"), 
        Arrays.<Variable>asList(tfVar, intVar), VariableNumMap.emptyMap());
    emptyPattern = new VariablePattern(Collections.<String>emptyList(), 
        Collections.<Variable>emptyList(), defaultVars.getVariablesByName("x0", "y0"));
  }
  
  public void testEmptyPattern() {
    List<VariableMatch> matches = emptyPattern.matchVariables(defaultVars);
    assertEquals(1, matches.size());
    assertEquals(defaultVars.getVariablesByName("x0", "y0"), matches.get(0).getVariables());
  }
}
