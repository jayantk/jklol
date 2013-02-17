package com.jayantkrish.jklol.gwt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.core.client.JsArrayMixed;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.IndexedPredicate;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.probdb.DbAssignment;
import com.jayantkrish.jklol.probdb.JoinQuery;
import com.jayantkrish.jklol.probdb.Query;
import com.jayantkrish.jklol.probdb.TableAssignment;
import com.jayantkrish.jklol.probdb.TableQuery;

public class NoExportHelpers {

  public static CcgParse parseCcg(String input) {
    String[] lexicon = {"block,N{0},0 pred:block", "blocks,N{0},0 pred:block",
        "one,N{0},0 pred:block", "ones,N{0},0 pred:block",
        "object,N{0},0 pred:block", "objects,N{0},0 pred:block",
        "triangle,N{0},0 pred:triangle", "triangles,N{0},0 pred:triangle", "cube,N{0},0 pred:cube", 
        "cubes,N{0},0 pred:cube", "ground,N{0},0 pred:ground",
        "red,(N{1}/N{1}){0},0 pred:red,pred:red 1 1", "green,(N{1}/N{1}){0},0 pred:lime,pred:lime 1 1",
        "blue,(N{1}/N{1}){0},0 pred:blue,pred:blue 1 1", "yellow,(N{1}/N{1}){0},0 pred:yellow,pred:yellow 1 1",
        "pink,(N{1}/N{1}){0},0 pred:deeppink,pred:deeppink 1 1", "orange,(N{1}/N{1}){0},0 pred:orange,pred:orange 1 1",
        "green,N{0},0 pred:green", "the,(N{1}/N{1}){0},0 pred:true,pred:true 1 1", "a,(N{1}/N{1}){0},0 pred:true,pred:true 1 1",
        "near,((N{1}\\N{1}){0}/N{2}){0},0 pred:near,pred:near 1 1,pred:near 2 2",
        "near,((S{1}/(S{1}\\N{0}){1}){0}/N{2}){0},0 pred:near,pred:near 2 2",
        "near,(PP{0}/N{1}){0},0 pred:near,pred:near 2 1",
        "is,((S{0}\\N{1}){0}/N{2}){0},0 pred:equals,pred:equals 1 1,pred:equals 2 2"};
    
    String[] rules = {"FOO{0} FOO{1} FOO{1}", "FOO{0} FOO{0}"};

    ParametricCcgParser ccgFamily = ParametricCcgParser.parseFromLexicon(
        Arrays.asList(lexicon), Arrays.asList(rules), null, null, false, null, false);
    CcgParser parser = ccgFamily.getModelFromParameters(ccgFamily.getNewSufficientStatistics());
    
    List<String> words = Arrays.asList(input.split(" "));
    List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    List<CcgParse> parses = parser.beamSearch(words, posTags, 10);
    return parses.get(0);
  }
  
  public static int[] runDatabaseQuery(Query query, int[] entities, String[] tableNames, JsArrayMixed tables) {
    String[] entityStrings = new String[entities.length];
    for (int i = 0; i < entities.length; i++) {
      entityStrings[i] = Integer.toString(entities[i]);
    }
    DiscreteVariable entityVar = new DiscreteVariable("entities", Arrays.asList(entityStrings));
    VariableNumMap var = VariableNumMap.singleton(0, "entityVar1", entityVar);

    List<TableAssignment> tableAssignments = Lists.newArrayList();
    for (int i = 0; i < tableNames.length; i++) {
      JsArrayInteger jsArray = tables.getObject(i).cast();
      String[] array = new String[jsArray.length()];
      for (int j = 0; j < jsArray.length(); j++) {
        array[j] = Integer.toString(jsArray.get(j));
      }
      TableAssignment table = TableAssignment.fromDelimitedLines(var, Arrays.asList(array));
      tableAssignments.add(table);
    }
    DbAssignment db = new DbAssignment(Arrays.asList(tableNames), tableAssignments);

    TableAssignment result = query.evaluate(db);
    List<List<Object>>tuples = result.getTuples();
    int[] returnValues = new int[tuples.size()];
    for (int i = 0; i < tuples.size(); i++) {
      List<Object> tuple = tuples.get(i);
      Preconditions.checkState(tuple.size() == 1);
      returnValues[i] = Integer.parseInt((String) tuple.get(0));
    }
    return returnValues;
  }

  public static Query convertParseToQuery(CcgParse parse) {
    if (parse.isTerminal()) {
      Set<IndexedPredicate> heads = parse.getSemanticHeads();
      return new TableQuery(Iterables.getOnlyElement(heads).getHead());
    } else {
      Query leftQuery = convertParseToQuery(parse.getLeft());
      Query rightQuery = convertParseToQuery(parse.getRight());
      
      List<DependencyStructure> nodeDeps = parse.getNodeDependencies();
      Preconditions.checkState(nodeDeps.size() == 1);
      DependencyStructure dep = nodeDeps.get(0);
      
      Query headQuery = leftQuery;
      Query childQuery = rightQuery;
      
      return new JoinQuery(headQuery, childQuery, new int[] {0});
    }
  }
}
