package com.jayantkrish.jklol.inference;

import java.util.Arrays;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.InferenceHint;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.util.Assignment;

/**
 * InferenceTestCases contains a series of tests for inference algorithms. Tests
 * are represented as {@link MarginalTestCase}s which assert expected outcomes
 * for certain given {@code FactorGraph}s.
 * 
 * @author jayant
 */
public class InferenceTestCases {

  private static final Variable threeValueVar = new DiscreteVariable("Three values",
      Arrays.asList(new String[] { "T", "F", "U" }));
  private static final Variable trueFalseVar = new DiscreteVariable("True/False",
      Arrays.asList(new String[] { "T", "F" }));

  /**
   * A factor graph with several discrete variables and lots of zero weights.
   * The factor graph shape is already a clique tree.
   * 
   * @return
   */
  public static FactorGraph basicFactorGraph() {
    FactorGraph fg = new FactorGraph();

    DiscreteVariable otherVar = new DiscreteVariable("Two values",
        Arrays.asList(new String[] { "foo", "bar" }));

    fg = fg.addVariable("Var0", threeValueVar);
    fg = fg.addVariable("Var1", otherVar);
    fg = fg.addVariable("Var2", threeValueVar);
    fg = fg.addVariable("Var3", threeValueVar);
    fg = fg.addVariable("Var4", threeValueVar);
    
    TableFactorBuilder factor1 = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0", "Var2", "Var3" })));
    factor1.setWeightList(Arrays.asList(new String[] { "T", "T", "T" }), 1.0);
    factor1.setWeightList(Arrays.asList(new String[] { "T", "F", "F" }), 1.0);
    factor1.setWeightList(Arrays.asList(new String[] { "U", "F", "F" }), 2.0);
    fg = fg.addFactor(factor1.build());

    TableFactorBuilder factor2 = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var2", "Var1" })));
    factor2.setWeightList(Arrays.asList(new String[] { "foo", "F" }), 2.0);
    factor2.setWeightList(Arrays.asList(new String[] { "foo", "T" }), 3.0);
    factor2.setWeightList(Arrays.asList(new String[] { "bar", "T" }), 2.0);
    factor2.setWeightList(Arrays.asList(new String[] { "bar", "F" }), 1.0);
    fg = fg.addFactor(factor2.build());

    TableFactorBuilder factor3 = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var3", "Var4" })));
    factor3.setWeightList(Arrays.asList(new String[] { "F", "U" }), 2.0);
    factor3.setWeightList(Arrays.asList(new String[] { "T", "U" }), 2.0);
    factor3.setWeightList(Arrays.asList(new String[] { "T", "F" }), 3.0);
    fg = fg.addFactor(factor3.build());

    fg = fg.addInferenceHint(new InferenceHint(new int[] {3, 1, 2}));
    return fg;
  }

  public static MarginalTestCase testBasicUnconditional() {
    MarginalTestCase testCase = new MarginalTestCase(basicFactorGraph(), Assignment.EMPTY);
    // Test the marginal on variable 1
    testCase.addTest(27.0 / 43.0, new String[] { "Var1" }, "foo");
    testCase.addTest(16.0 / 43.0, new String[] { "Var1" }, "bar");
    // Test the marginal on variables 0, 2
    testCase.addTest(25.0 / 43.0, new String[] { "Var0", "Var2" }, "T", "T");
    testCase.addTest(6.0 / 43.0, new String[] { "Var0", "Var2" }, "T", "F");
    testCase.addTest(12.0 / 43.0, new String[] { "Var0", "Var2" }, "U", "F");
    testCase.addTest(0.0, new String[] { "Var0", "Var2" }, "U", "U");

    return testCase;
  }

  public static MarginalTestCase testBasicConditional() {
    FactorGraph f = basicFactorGraph();
    Assignment conditional = f.outcomeToAssignment(Arrays.asList(new String[] { "Var2" }),
        Arrays.asList(new String[] { "F" }));
    MarginalTestCase testCase = new MarginalTestCase(f, conditional);

    testCase.addTest(12.0 / 18.0, new String[] { "Var1" }, "foo");
    testCase.addTest(6.0 / 18.0, new String[] { "Var1" }, "bar");

    testCase.addTest(1.0, new String[] { "Var3", "Var4" }, "F", "U");
    testCase.addTest(0.0, new String[] { "Var3", "Var4" }, "T", "F");

    testCase.addTest(0.0, new String[] { "Var0", "Var3" }, "T", "T");
    testCase.addTest(6.0 / 18.0, new String[] { "Var0", "Var3" }, "T", "F");
    testCase.addTest(12.0 / 18.0, new String[] { "Var0", "Var3" }, "U", "F");

    return testCase;
  }

  public static MaxMarginalTestCase testBasicMaxMarginals() {
    MaxMarginalTestCase testCase = new MaxMarginalTestCase(basicFactorGraph(), Assignment.EMPTY, 
        new Assignment(Arrays.asList(new Integer[] {0, 1, 2, 3, 4}), 
            Arrays.asList(new Object[] {"T", "foo", "T", "T", "F"})));
    return testCase;

    // These are the actual max marginals, in case I ever want to test them.
    /*
    testCase.addTest(6.0 / 18.0, new Integer[] { 1 }, "bar");
    testCase.addTest(9.0 / 18.0, new Integer[] { 1 }, "foo");

    testCase.addTest(9.0 / 18.0, new Integer[] { 0, 2 }, "T", "T");
    testCase.addTest(4.0 / 18.0, new Integer[] { 0, 2 }, "T", "F");
    testCase.addTest(8.0 / 18.0, new Integer[] { 0, 2 }, "U", "F");
    testCase.addTest(0.0 / 18.0, new Integer[] { 0, 2 }, "U", "U");
    */
  }

  /**
   * A factor graph with several discrete variables and lots of zero weights.
   * The factor graph shape is not a minimal clique tree, but must be made into
   * a clique tree.
   * 
   * @return
   */
  public static FactorGraph nonCliqueTreeFactorGraph() {
    FactorGraph fg = new FactorGraph();

    fg = fg.addVariable("Var0", trueFalseVar);
    fg = fg.addVariable("Var1", trueFalseVar);
    fg = fg.addVariable("Var2", trueFalseVar);

    TableFactorBuilder tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0", "Var1" })));
    tf.setWeightList(Arrays.asList(new String[] { "F", "F" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "T" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "F" }), 3.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "T" }), 4.0);
    fg = fg.addFactor(tf.build());

    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0", "Var2" })));
    tf.setWeightList(Arrays.asList(new String[] { "F", "F" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "T" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "F" }), 3.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "T" }), 1.0);
    fg = fg.addFactor(tf.build());

    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0" })));
    tf.setWeightList(Arrays.asList(new String[] { "F" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T" }), 1.0);
    fg = fg.addFactor(tf.build());

    return fg;
  }

  public static MarginalTestCase testNonCliqueTreeUnconditional() {
    MarginalTestCase testCase = new MarginalTestCase(nonCliqueTreeFactorGraph(), Assignment.EMPTY);
    testCase.addTest(28.0 / 60.0, new String[] { "Var0" }, "T");
    testCase.addTest(32.0 / 60.0, new String[] { "Var0" }, "F");

    return testCase;
  }
}
