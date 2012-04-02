package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.cfg.BeamSearchCfgFactor.OovMapper;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.evaluation.FactorGraphPredictor.SimpleFactorGraphPredictor;
import com.jayantkrish.jklol.evaluation.Predictor;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.WrapperVariablePattern;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.training.Trainer;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Regression test for performing parameter estimation for a context-free grammar using 
 * {@code BeamSearchCfgFactor}.
 * 
 * @author jayant
 */
public class BeamSearchCfgFactorTest extends TestCase {
  
  ParametricFactorGraph cfgModel;
  VariableNumMap x, y;
  VariableNumMap parent, left, right, terminal, ruleType;
  List<Example<Assignment, Assignment>> trainingData;
  
  private static final String[] NONTERMINALS = new String[] {"S", "N", "V", "NP", "VP", "DT", "JJ"};
  private static final String[] TERMINALS = new String[] {"the", "black", "cat", "jumped", "drank", "fence", "<OOV>"};
  
  private static final String[] TRAINING_DATA = new String[] {
    "(S (NP (DT the) (NP (JJ black) (N cat))) (V jumped))",
    "(S (NP (DT the) (NP (JJ black) (N cat))) (VP (V jumped) (NP (DT the) (N fence))))",
    "(S (NP (DT the) (NP (JJ black) (N cat))) (VP (V drank) (N <OOV>)))"
  };
    
  private static final String[] TEST_DATA = new String[] {
    "(S (NP (DT the) (NP (JJ black) (N cat))) (V jumped))",
    "(S (NP (DT the) (NP (JJ black) (N cat))) (VP (V jumped) (NP (DT the) (N fence))))",
    "(S (NP (DT the) (NP (JJ black) (N cat))) (VP (V drank) (N milk)))",
    "(S (NP (DT the) (N milk)) (V jumped))"
  };
  
  public void setUp() {
    // Define the CFG grammar using a special set of variables.
    Variable parseNodeVariable = new DiscreteVariable("parseNode", Arrays.asList(NONTERMINALS));
    Variable emptyVariable = new DiscreteVariable("empty", Arrays.asList("RULE"));
    left = new VariableNumMap(Arrays.asList(0), Arrays.asList("left"), Arrays.asList(parseNodeVariable));
    right = new VariableNumMap(Arrays.asList(1), Arrays.asList("right"), Arrays.asList(parseNodeVariable));
    parent = new VariableNumMap(Arrays.asList(3), Arrays.asList("parent"), Arrays.asList(parseNodeVariable));
    ruleType = new VariableNumMap(Arrays.asList(4), Arrays.asList("ruleType"), Arrays.asList(emptyVariable));
    VariableNumMap nonterminalVars = VariableNumMap.unionAll(left, right, parent, ruleType);
    ParametricFactor nonterminalFactor = DiscreteLogLinearFactor.createIndicatorFactor(nonterminalVars);
    
    List<List<String>> allTerminals = Lists.newArrayList(Iterables.transform(Arrays.asList(TERMINALS),
        new Function<String, List<String>>() {
      @Override
      public List<String> apply(String x) {
        return Arrays.asList(x.split(" "));
      }
    }));
    Variable terminalNodeVariable = new DiscreteVariable("terminalNode", allTerminals);
    terminal = new VariableNumMap(Arrays.asList(2), Arrays.asList("terminal"), Arrays.asList(terminalNodeVariable));
    VariableNumMap terminalVars = VariableNumMap.unionAll(terminal, parent, ruleType);
    ParametricFactor terminalFactor = DiscreteLogLinearFactor.createIndicatorFactor(terminalVars);
    
    // Construct a factor graph containing the cfg grammar. 
    ObjectVariable parseTreeVariable = new ObjectVariable(ParseTree.class);
    ObjectVariable inputVariable = new ObjectVariable(List.class);

    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    builder.addVariable("x", inputVariable);
    builder.addVariable("y", parseTreeVariable);
    x = builder.getVariables().getVariablesByName("x");
    y = builder.getVariables().getVariablesByName("y");

    OovMapper oovMapper = new OovMapper("<OOV>", Sets.newHashSet(Arrays.asList(TERMINALS)));
    ParametricCfgFactor cfgFactor = new ParametricCfgFactor(parent, left, right, terminal, ruleType, 
        y, x, nonterminalFactor, terminalFactor, oovMapper, Predicates.alwaysTrue(), 10, false);
    builder.addFactor(cfgFactor, new WrapperVariablePattern(x.union(y)));
    cfgModel = builder.build();
    
    // Create some training data.
    trainingData = Lists.newArrayList();
    for (int i = 0; i < TRAINING_DATA.length; i++) {
      ParseTree outputTree = parseTreeFromString(TRAINING_DATA[i]);
      List<Object> terminals = outputTree.getTerminalProductions();

      Assignment input = x.outcomeArrayToAssignment(terminals);
      Assignment output = y.outcomeArrayToAssignment(outputTree);
      trainingData.add(Example.create(input, output));
    }
  }
  
  private ParseTree parseTreeFromString(String parseTreeString) {
    String[] elements = partitionString(parseTreeString);
    String curPos = elements[0];
    if (elements.length == 2) {
      String terminalNode = elements[1];
      return new ParseTree(curPos, "RULE", Arrays.<Object>asList(terminalNode), 1.0);
    } else {
      ParseTree leftTree = parseTreeFromString(elements[1]);
      ParseTree rightTree = parseTreeFromString(elements[2]);
      return new ParseTree(curPos, "RULE", leftTree, rightTree, 1.0);
    }
  }
  
  private String[] partitionString(String parseTreeString) {
    // Strip off leading/trailing parentheses
    String str = parseTreeString.substring(1, parseTreeString.length() - 1);
    int parenCount = 0;
    int lastSplitIndex = 0;
    List<String> chunks = Lists.newArrayList();
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) == ' ' && parenCount == 0) {
        chunks.add(str.substring(lastSplitIndex, i));
        lastSplitIndex = i + 1;
      } else if (str.charAt(i) == '(') {
        parenCount++;
      } else if (str.charAt(i) == ')') {
        parenCount--;
      }
    }
    chunks.add(str.substring(lastSplitIndex));
    return chunks.toArray(new String[] {});
  }
  
  public void testLogLinearTraining() {
    Predictor<Assignment, Assignment> predictor = runTrainerTest(new StochasticGradientTrainer(
        new JunctionTree(), 5, new DefaultLogFunction(), 1.0, 0.0));
    
    for (int i = 0; i < TEST_DATA.length; i++) {
      ParseTree expected = parseTreeFromString(TEST_DATA[i].replaceAll("milk", "<OOV>"));
      Assignment actual = predictor.getBestPrediction(x.outcomeArrayToAssignment(
          parseTreeFromString(TEST_DATA[i]).getTerminalProductions()));
      assertEquals(expected, actual.getOnlyValue());
    }
  }
  
  private Predictor<Assignment, Assignment> runTrainerTest(Trainer trainer) {
    SufficientStatistics parameters = trainer.trainFixed(cfgModel,
        cfgModel.getNewSufficientStatistics(), trainingData);
    FactorGraph trainedModel = cfgModel.getFactorGraphFromParameters(parameters)
        .getFactorGraph(DynamicAssignment.EMPTY);
    SimpleFactorGraphPredictor predictor = new SimpleFactorGraphPredictor(
        trainedModel, y, new JunctionTree());
    return predictor;
  }
}
