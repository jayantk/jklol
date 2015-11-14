package com.jayantkrish.jklol.cfg;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

/**
 * Regression test for performing parameter estimation for a context-free grammar using 
 * {@code BeamSearchCfgFactor}.
 * 
 * @author jayant
 */
public class ParametricCfgParserTest extends TestCase {
  
  ParametricCfgParser cfgFactor;
  VariableNumMap parent, left, right, terminal, ruleType;
  List<CfgExample> trainingData;
  
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
    left = VariableNumMap.singleton(0, "left", parseNodeVariable);
    right = VariableNumMap.singleton(1, "right", parseNodeVariable);
    parent = VariableNumMap.singleton(3, "parent", parseNodeVariable);
    ruleType =VariableNumMap.singleton(4, "ruleType", emptyVariable);
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
    
    cfgFactor = new ParametricCfgParser(parent, left, right, terminal, ruleType, 
        nonterminalFactor, terminalFactor, false);
    
    // Create some training data.
    trainingData = Lists.newArrayList();
    for (int i = 0; i < TRAINING_DATA.length; i++) {
      CfgParseTree outputTree = parseTreeFromString(TRAINING_DATA[i], 0);
      List<Object> terminals = outputTree.getTerminalProductions();

      trainingData.add(new CfgExample(terminals, outputTree));
    }
  }
  
  private CfgParseTree parseTreeFromString(String parseTreeString, int wordInd) {
    String[] elements = partitionString(parseTreeString);
    String curPos = elements[0];
    if (elements.length == 2) {
      String terminalNode = elements[1];
      return new CfgParseTree(curPos, "RULE", Arrays.<Object>asList(terminalNode), 1.0, wordInd, wordInd);
    } else {
      CfgParseTree leftTree = parseTreeFromString(elements[1], wordInd);
      wordInd = leftTree.getSpanEnd() + 1;
      CfgParseTree rightTree = parseTreeFromString(elements[2], wordInd);
      return new CfgParseTree(curPos, "RULE", leftTree, rightTree, 1.0);
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
    CfgParser parser= runTrainerTest(cfgFactor, new CfgLoglikelihoodOracle(cfgFactor)); 
    
    for (int i = 0; i < TEST_DATA.length; i++) {
      CfgParseTree expected = parseTreeFromString(TEST_DATA[i].replaceAll("milk", "<OOV>"), 0);
      CfgParseTree predicted = parser.beamSearch(expected.getTerminalProductions(), 10).get(0);
      assertEquals(expected, predicted);
    }
  }

  private CfgParser runTrainerTest(ParametricCfgParser cfgModel,
      GradientOracle<CfgParser, CfgExample> oracle) {

    StochasticGradientTrainer trainer = new StochasticGradientTrainer(
        10 * TRAINING_DATA.length, 1, 1.0, true, false, new DefaultLogFunction());

    SufficientStatistics parameters = trainer.train(oracle,
        oracle.initializeGradient(), trainingData);
    return cfgModel.getModelFromParameters(parameters);
  }
}
