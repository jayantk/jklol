package com.jayantkrish.jklol.probdb;

import java.util.Arrays;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * Regression tests for running queries on databases.
 * 
 * @author jayantk
 */
public class QueryTest extends TestCase {
  DbAssignment db;
  VariableNumMap arg1Var, arg2Var, allVars;  
  TableAssignment letterTable, vowelTable, evenNumberTable, letterNumberTable;
  
  TableQuery letterQuery, vowelQuery, evenNumberQuery, letterNumberQuery;
  
  private static final String[] letterTableValues = new String[] {"a", "b", "c", "d", "e"};
  private static final String[] vowelTableValues = new String[] {"a", "1"};
  private static final String[] evenNumberValues = new String[] {"2", "4"};
  private static final String[] letterNumberTableValues = 
      new String[] {"a,1", "b,2", "c,3", "d,4"};
  
  public void setUp() {
    DiscreteVariable objects = new DiscreteVariable("objects", 
        Arrays.asList("a", "b", "c", "d", "e", "1", "2", "3", "4", "5"));
    
    arg1Var = VariableNumMap.singleton(0, "arg1", objects);
    arg2Var = VariableNumMap.singleton(1, "arg2", objects);
    allVars = arg1Var.union(arg2Var);
    letterTable = TableAssignment.fromDelimitedLines(arg1Var, Arrays.asList(letterTableValues));
    vowelTable = TableAssignment.fromDelimitedLines(arg1Var, Arrays.asList(vowelTableValues));
    evenNumberTable = TableAssignment.fromDelimitedLines(arg1Var, Arrays.asList(evenNumberValues)); 
    letterNumberTable = TableAssignment.fromDelimitedLines(allVars, 
        Arrays.asList(letterNumberTableValues));
    
    db = new DbAssignment(Arrays.asList("letters", "vowels", "evenNumbers", "letterNumbers"), 
        Arrays.asList(letterTable, vowelTable, evenNumberTable, letterNumberTable));
    
    letterQuery = new TableQuery("letters");
    vowelQuery = new TableQuery("vowels");
    evenNumberQuery = new TableQuery("evenNumbers");
    letterNumberQuery = new TableQuery("letterNumbers");
  }

  public void testTableQuery() {
    TableAssignment values = letterQuery.evaluate(db);
    assertTableEquals(letterTable, values);
  }

  public void testJoinQuery() {
    Query joinQuery = new JoinQuery(letterQuery, vowelQuery, new int[] {0});
    
    TableAssignment values = joinQuery.evaluate(db);
    TableAssignment expected = TableAssignment.fromDelimitedLines(arg1Var, Arrays.asList("a"));
    assertTableEquals(expected, values);
  }
  
  public void testJoinQuery2() {
    Query joinQuery = new JoinQuery(letterNumberQuery, vowelQuery, new int[] {0});

    TableAssignment values = joinQuery.evaluate(db);
    TableAssignment expected = TableAssignment.fromDelimitedLines(allVars, Arrays.asList("a,1"));
    assertTableEquals(expected, values);
  }
  
  public void testJoinQuery3() {
    Query joinQuery = new JoinQuery(letterNumberQuery, evenNumberQuery, new int[] {1});
    
    TableAssignment values = joinQuery.evaluate(db);
    TableAssignment expected = TableAssignment.fromDelimitedLines(allVars, Arrays.asList("b,2", "d,4"));
    assertTableEquals(expected, values);    
  }
  
  public void testExistentialQuery1() {
    Query query = new ExistentialQuery(letterNumberQuery, new int[] {0});
    
    TableAssignment values = query.evaluate(db);
    TableAssignment expected = TableAssignment.fromDelimitedLines(arg2Var,
        Arrays.asList("1", "2", "3", "4"));
    assertTableEquals(values, expected);
  }

  public void testExistentialQuery2() {
    Query query = new ExistentialQuery(letterNumberQuery, new int[] {1});

    TableAssignment values = query.evaluate(db);
    TableAssignment expected = TableAssignment.fromDelimitedLines(arg1Var,
        Arrays.asList("a", "b", "c", "d"));
    assertTableEquals(values, expected);
  }

  public void testExistentialQuery3() {
    Query query = new ExistentialQuery(letterNumberQuery, new int[] {0, 1});

    TableAssignment values = query.evaluate(db);
    assertTableEquals(TableAssignment.SATISFIABLE, values);
  }

  private void assertTableEquals(TableAssignment expected, TableAssignment actual) {
    assertEquals(expected.getVariables(), actual.getVariables());
    assertEquals(expected.getIndicators(), actual.getIndicators());
  }
}
