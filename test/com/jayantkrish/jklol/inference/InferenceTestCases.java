package com.jayantkrish.jklol.inference;

import java.util.Arrays;
import java.util.Iterator;

import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.InferenceHint;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
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
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0", "Var2", "Var3" })),
        SparseTensorBuilder.getFactory());
    factor1.setWeightList(Arrays.asList(new String[] { "T", "T", "T" }), 1.0);
    factor1.setWeightList(Arrays.asList(new String[] { "T", "F", "F" }), 1.0);
    factor1.setWeightList(Arrays.asList(new String[] { "U", "F", "F" }), 2.0);
    fg = fg.addFactor("factor1", factor1.build());

    TableFactorBuilder factor2 = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var2", "Var1" })),
        SparseTensorBuilder.getFactory());
    factor2.setWeightList(Arrays.asList(new String[] { "foo", "F" }), 2.0);
    factor2.setWeightList(Arrays.asList(new String[] { "foo", "T" }), 3.0);
    factor2.setWeightList(Arrays.asList(new String[] { "bar", "T" }), 2.0);
    factor2.setWeightList(Arrays.asList(new String[] { "bar", "F" }), 1.0);
    fg = fg.addFactor("factor2", factor2.build());

    TableFactorBuilder factor3 = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var3", "Var4" })),
        SparseTensorBuilder.getFactory());
    factor3.setWeightList(Arrays.asList(new String[] { "F", "U" }), 2.0);
    factor3.setWeightList(Arrays.asList(new String[] { "T", "U" }), 2.0);
    factor3.setWeightList(Arrays.asList(new String[] { "T", "F" }), 3.0);
    fg = fg.addFactor("factor3", factor3.build());

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
    FactorGraph f = basicFactorGraph();
    MaxMarginalTestCase testCase = new MaxMarginalTestCase(f, Assignment.EMPTY, 
        Assignment.fromSortedArrays(new int[] {0, 1, 2, 3, 4}, 
            new Object[] {"T", "foo", "T", "T", "F"}),
            f.getVariables().intersection(0, 2));
    testCase.addTest(new String[] {"T", "T"}, 9.0);
    testCase.addTest(new String[] {"T", "F"}, 4.0);
    testCase.addTest(new String[] {"U", "F"}, 8.0);
    testCase.addTest(new String[] {"U", "U"}, 0.0);
    return testCase;

    // These are the actual max marginals, in case I ever want to test them.
    /*
    testCase.addTest(6.0 / 18.0, new Integer[] { 1 }, "bar");
    testCase.addTest(9.0 / 18.0, new Integer[] { 1 }, "foo");
    */
  }
  
  public static MaxMarginalTestCase testConditionalMaxMarginals() {
    FactorGraph f = basicFactorGraph();
    MaxMarginalTestCase testCase = new MaxMarginalTestCase(f, 
        Assignment.fromSortedArrays(new int[] {2}, new Object[] {"F"}), 
        Assignment.fromSortedArrays(new int[] {0, 1, 2, 3, 4}, 
            new Object[] {"U", "foo", "F", "F", "U"}),
            f.getVariables().intersection(3, 4));
    
    // The graph becomes disjoint, so the max marginal here is somewhat unclear.
    // testCase.addTest(new String[] {"F", "U"}, 8.0);
    testCase.addTest(new String[] {"T", "U"}, 0.0);
    testCase.addTest(new String[] {"F", "F"}, 0.0);
    testCase.addTest(new String[] {"T", "T"}, 0.0);
    return testCase;
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
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0", "Var1" })),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "F", "F" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "T" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "F" }), 3.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "T" }), 4.0);
    fg = fg.addFactor("tf1", tf.build());

    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0", "Var2" })),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "F", "F" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "T" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "F" }), 3.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "T" }), 1.0);
    fg = fg.addFactor("tf2", tf.build());

    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0" })),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "F" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T" }), 1.0);
    fg = fg.addFactor("tf3", tf.build());

    return fg;
  }

  public static MarginalTestCase testNonCliqueTreeUnconditional() {
    MarginalTestCase testCase = new MarginalTestCase(nonCliqueTreeFactorGraph(), Assignment.EMPTY);
    testCase.addTest(28.0 / 60.0, new String[] { "Var0" }, "T");
    testCase.addTest(32.0 / 60.0, new String[] { "Var0" }, "F");

    return testCase;
  }
  
  public static FactorGraph productFactorGraph() {
    FactorGraph fg = new FactorGraph();

    fg = fg.addVariable("Var0", trueFalseVar);
    fg = fg.addVariable("Var1", trueFalseVar);
    fg = fg.addVariable("Var2", trueFalseVar);

    TableFactorBuilder tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0" })),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "F" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T" }), 1.0);
    fg = fg.addFactor("tf0", tf.build());

    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var1" })),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "F" }), 3.0);
    tf.setWeightList(Arrays.asList(new String[] { "T" }), 2.0);
    fg = fg.addFactor("tf1", tf.build());
    
    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var2" })),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "F" }), 1.0);
    tf.setWeightList(Arrays.asList(new String[] { "T" }), 1.0);
    fg = fg.addFactor("tf2", tf.build());
    
    return fg;
  }
  
  public static MarginalTestCase testProductFactorGraphUnconditional() {
    MarginalTestCase testCase = new MarginalTestCase(productFactorGraph(), Assignment.EMPTY);
    testCase.addTest(1.0 / 3.0, new String[] { "Var0" }, "T");
    testCase.addTest(2.0 / 3.0, new String[] { "Var0" }, "F");
    
    testCase.addTest(2.0 / 5.0, new String[] { "Var1" }, "T");
    testCase.addTest(3.0 / 5.0, new String[] { "Var1" }, "F");
    
    testCase.addTest(1.0 / 2.0, new String[] { "Var2" }, "T");
    testCase.addTest(1.0 / 2.0, new String[] { "Var2" }, "F");

    return testCase;
  }

  public static FactorGraph softConstraintFactorGraph() {
    FactorGraph fg = new FactorGraph();

    fg = fg.addVariable("Var0", trueFalseVar);
    fg = fg.addVariable("Var1", trueFalseVar);
    fg = fg.addVariable("Var2", trueFalseVar);

    TableFactorBuilder tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0" })),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "T" }), 9.0);
    tf.setWeightList(Arrays.asList(new String[] { "F" }), 1.0);
    fg = fg.addFactor("tf0", tf.build());

    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var1" })),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "T" }), 9.0);
    tf.setWeightList(Arrays.asList(new String[] { "F" }), 1.0);
    fg = fg.addFactor("tf1", tf.build());
    
    tf = softAndFactor(fg.getVariables().getVariablesByName("Var0", "Var1"),
        fg.getVariables().getVariablesByName("Var2"), -2.0);
    fg = fg.addFactor("tf2", tf.build());    
    return fg;
  }
  
  public static MarginalTestCase testSoftConstraintFactorGraph() {
    MarginalTestCase testCase = new MarginalTestCase(softConstraintFactorGraph(), Assignment.EMPTY);
    testCase.addTest(1.0 / 2.0, new String[] { "Var0" }, "T");
    testCase.addTest(1.0 / 2.0, new String[] { "Var0" }, "F");
    
    testCase.addTest(1.0 / 4.0, new String[] { "Var1" }, "T");
    testCase.addTest(3.0 / 4.0, new String[] { "Var1" }, "F");
    
    testCase.addTest(1.0 / 8.0, new String[] { "Var2" }, "T");
    testCase.addTest(7.0 / 8.0, new String[] { "Var2" }, "F");

    return testCase;
  }
  
  public static FactorGraph triangleFactorGraph() {
    FactorGraph fg = new FactorGraph();

    fg = fg.addVariable("Var0", trueFalseVar);
    fg = fg.addVariable("Var1", trueFalseVar);
    fg = fg.addVariable("Var2", trueFalseVar);

    TableFactorBuilder tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var0", "Var1"})),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "T", "T" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "F" }), 1.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "T" }), 1.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "F" }), 2.0);
    fg = fg.addFactor("tf0", tf.build());
    
    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var1", "Var2"})),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "T", "T" }), 3.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "F" }), 1.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "T" }), 1.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "F" }), 2.0);
    fg = fg.addFactor("tf1", tf.build());

    tf = new TableFactorBuilder(fg
        .getVariables().getVariablesByName(Arrays.asList(new String[] { "Var2", "Var0"})),
        SparseTensorBuilder.getFactory());
    tf.setWeightList(Arrays.asList(new String[] { "T", "T" }), 2.0);
    tf.setWeightList(Arrays.asList(new String[] { "T", "F" }), 1.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "T" }), 1.0);
    tf.setWeightList(Arrays.asList(new String[] { "F", "F" }), 2.0);
    fg = fg.addFactor("tf2", tf.build());
    
    // Probability table:
    // 0 1 2 <- var nums
    // F F F 8
    // F F T 2
    // F T F 2
    // F T T 3 
    // T F F 2
    // T F T 2
    // T T F 2
    // T T T 12
    // total 33
    return fg;
  }

  public static MaxMarginalTestCase testTriangleFactorGraphMaxMarginals() {
    FactorGraph f = triangleFactorGraph();
    MaxMarginalTestCase testCase = new MaxMarginalTestCase(f, Assignment.EMPTY, 
        f.getAllVariables().outcomeArrayToAssignment("T", "T", "T"), f.getAllVariables());
    return testCase;
  }
  
  public static MarginalTestCase testTriangleFactorGraphMarginals() {
    MarginalTestCase testCase = new MarginalTestCase(triangleFactorGraph(), Assignment.EMPTY);
    testCase.addTest(18.0 / 33.0, new String[] { "Var0" }, "T");
    testCase.addTest(15.0 / 33.0, new String[] { "Var0" }, "F");

    testCase.addTest(4.0 / 33.0, new String[] { "Var0", "Var1" }, "T", "F");
    testCase.addTest(14.0 / 33.0, new String[] { "Var0", "Var1" }, "T", "T");

    return testCase;
  }

  private static TableFactorBuilder softAndFactor(VariableNumMap inputs, VariableNumMap output,
      double violationLogWeight) {
    TableFactorBuilder tf = new TableFactorBuilder(inputs.union(output),
        SparseTensorBuilder.getFactory());
    Iterator<Assignment> iter = new AllAssignmentIterator(tf.getVars());
    
    double denom = 1.0 + Math.exp(violationLogWeight);
    double satisfiedWeight = 1.0 / denom;
    double violatedWeight = Math.exp(violationLogWeight) / denom;
    while (iter.hasNext()) {
      Assignment a = iter.next();
      
      boolean expectedTruthVal = !a.intersection(inputs).getValues().contains("F");
      boolean actualTruthVal = a.intersection(output).getValues().contains("T");
      boolean satisfied = expectedTruthVal == actualTruthVal;
      double weight = satisfied ? satisfiedWeight : violatedWeight; 
      
      System.out.println(a + " " + weight);
      tf.setWeight(a, weight);
    }
    return tf;
  }
}
