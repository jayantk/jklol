package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmLoss;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmSquareLoss;
import com.jayantkrish.jklol.cvsm.CvsmLoglikelihoodOracle.CvsmValueLoss;
import com.jayantkrish.jklol.cvsm.tree.CvsmTree;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.Pseudorandom;

/**
 * Regression tests for training compositional vector space models.
 * 
 * @author jayantk
 */
public class CvsmTrainingTest extends TestCase {

  private static final String[] lexicon = {
      "block,N{0},vec:block,0 block",
      "table,N{0},vec:table,0 table",
      "red,(N{1}/N{1}){0},(lambda $1 (logit (mul mat:red $1))),0 red,red 1 1",
      "on,((N{1}\\N{1}){0}/N{2}){0},(lambda $2 $1 (logit (plus (mul mat:on:1 $1) (mul mat:on:2 $2)))),0 on,on 1 1,on 2 2"
  };

  private static final String[] rules = {
      "FOO{0} FOO{0}"
  };

  private static final String[] vectorNames = {
      "vec:block",
      "vec:table",
      "vec:distribution",
      "vec:logistic",
      "vec:log",
  };

  private static final String[] matrixNames = {
      "mat:red",
      "mat:green",
      "weights:logreg"
  };

  private static final String[] tensor3Names = {
      "t3:on"
  };
  
  private static final String[] affineExamples = {
      "vec:block",
      "vec:table",
      "(op:matvecmul mat:red vec:block)",
      "(op:matvecmul mat:red vec:table)",
      "(op:matvecmul (op:matvecmul t3:on vec:block) vec:table)",
  };

  private static final double[][] affineTargets = {
      { 1, 1, 0 },
      { 0, 0, 1 },
      { 3, 1, -1 },
      { 2, 1, 2 },
      { 1, 2, 3 },
  };
  
  private static final String[] diagExamples = {
    "vec:block",
    "vec:table",
    "(op:matvecmul mat:red vec:block)",
    "(op:matvecmul mat:red vec:table)",
    "(op:matvecmul (op:matvecmul t3:on vec:block) vec:table)",
  };

  private static final double[][] diagTargets = {
    { 1, 1, 0 },
    { 0, 2, 1 },
    { 0.5, 0.75, 0 },
    { 0, 1.5, 2 },
    { 0, 1.5, 0 },
  };

  private static final String[] softmaxExamples = {
      "vec:block",
      "vec:table",
      "(op:softmax vec:distribution)",
      "(op:softmax (op:matvecmul weights:logreg vec:block))",
      "(op:softmax (op:matvecmul weights:logreg vec:table))",
  };

  private static final double[][] softmaxTargets = {
      { 1, 1, 0 },
      { 0, 0, 1 },
      { 0.25, 0.5, 0.25 },
      { 0.9, 0, 0.1 },
      { 0, 0.5, 0.5 },
  };

  private static final String[] logisticExamples = {
      "(op:logistic vec:logistic)"
  };

  private static final double[][] logisticTargets = {
      { 1.0, 0.75, 0.2 },
  };
  
  private static final String[] tanhExamples = {
      "(op:tanh vec:logistic)"
  };

  private static final double[][] tanhTargets = {
      { 1.0, 0.0, -0.5 },
  };
  
  private static final String[] logExamples = {
    "(op:log (op:logistic vec:log))"
  };
  
  private static final double[][] logTargets = {
    { 0.0, -0.5, -1.0 }
  };

  private static final String[] tprodExamples = {
      "vec:block",
      "vec:table",
      "(op:matvecmul (op:matvecmul t3:on vec:block) vec:block)",
      "(op:matvecmul (op:matvecmul t3:on vec:block) vec:table)",
      "(op:matvecmul (op:matvecmul t3:on vec:table) vec:block)",
      "(op:matvecmul (op:matvecmul t3:on vec:table) vec:table)",
  };

  private static final double[][] tprodTargets = {
      { 0, 1, 0 },
      { 1, 0, 0 },
      { 0, 0, 0 },
      { 1, 0, 0 },
      { 1, 0, 0 },
      { 0, 0, 0 },
  };

  private static final String[] valueExamples = {
    "(op:matvecmul -1 (op:log (op:logistic (op:matvecmul vec:block vec:table))))",
    "(op:matvecmul -1 (op:log (op:add 1 (op:matvecmul -1 (op:logistic (op:matvecmul vec:logistic vec:table))))))"
  };

  // These are irrelevant, since value training simply
  // aims to minimize the function value.
  private static final double[][] valueTargets = {
      { 0 }, { 0 },
  };

  private static final int NUM_DIMS = 3;

  private ParametricCcgParser family;
  private CcgParser parser;

  private CvsmFamily cvsmFamily, lowRankCvsmFamily, diagCvsmFamily;

  public void setUp() {
    family = ParametricCcgParser.parseFromLexicon(Arrays.asList(lexicon), Arrays.asList(rules),
        new DefaultCcgFeatureFactory(null, true), null, true, null, false, false);
    parser = family.getModelFromParameters(family.getNewSufficientStatistics());

    DiscreteVariable dimType = DiscreteVariable.sequence("seq", NUM_DIMS);
    VariableNumMap vectorVars = VariableNumMap.singleton(0, "dim-0", dimType);
    VariableNumMap matrixVars = new VariableNumMap(Ints.asList(0, 1),
        Arrays.asList("dim-0", "dim-1"), Arrays.asList(dimType, dimType));
    VariableNumMap t3Vars = new VariableNumMap(Ints.asList(0, 1, 2),
        Arrays.asList("dim-0", "dim-1", "dim-2"), Arrays.asList(dimType, dimType, dimType));

    IndexedList<String> tensorNames = IndexedList.create();
    List<LrtFamily> tensorDims = Lists.newArrayList();
    List<LrtFamily> lrtTensorDims = Lists.newArrayList();
    List<LrtFamily> diagTensorDims = Lists.newArrayList();
    for (int i = 0; i < vectorNames.length; i++) {
      tensorNames.add(vectorNames[i]);
      tensorDims.add(new TensorLrtFamily(vectorVars));
      lrtTensorDims.add(new TensorLrtFamily(vectorVars));
      diagTensorDims.add(new TensorLrtFamily(vectorVars));
    }

    for (int i = 0; i < matrixNames.length; i++) {
      tensorNames.add(matrixNames[i]);
      tensorDims.add(new TensorLrtFamily(matrixVars));
      lrtTensorDims.add(new OpLrtFamily(matrixVars, 2));
      diagTensorDims.add(new OpLrtFamily(matrixVars, 0));
    }

    for (int i = 0; i < tensor3Names.length; i++) {
      tensorNames.add(tensor3Names[i]);
      tensorDims.add(new TensorLrtFamily(t3Vars));
      lrtTensorDims.add(new OpLrtFamily(t3Vars, 4));
      diagTensorDims.add(new OpLrtFamily(t3Vars, 0));
    }

    cvsmFamily = new CvsmFamily(tensorNames, tensorDims);
    lowRankCvsmFamily = new CvsmFamily(tensorNames, lrtTensorDims);
    diagCvsmFamily = new CvsmFamily(tensorNames, diagTensorDims);
  }

  public void testParse() {
    List<CcgParse> parses = parser.beamSearch(ListSupertaggedSentence.createWithUnobservedSupertags(
        Arrays.asList("red", "block", "on", "table"), Collections.nCopies(4, ParametricCcgParser.DEFAULT_POS_TAG)), 10);

    System.out.println(parses.get(0).getLogicalForm().simplify());
  }

  public void testCvsmAffineTraining() {
    runCvsmTrainingTest(parseExamples(affineExamples, affineTargets), cvsmFamily, new CvsmSquareLoss(), 5000);
  }
  
  public void testLowRankCvsmAffineTraining() {
    runCvsmTrainingTest(parseExamples(affineExamples, affineTargets), lowRankCvsmFamily, new CvsmSquareLoss(), 5000);
  }
  
  public void testCvsmDiagTraining() {
    runCvsmTrainingTest(parseExamples(diagExamples, diagTargets), cvsmFamily, new CvsmSquareLoss(), 5000);
  }

  public void testLowRankCvsmDiagTraining() {
    runCvsmTrainingTest(parseExamples(diagExamples, diagTargets), lowRankCvsmFamily, new CvsmSquareLoss(), -1);
  }
  
  public void testDiagCvsmDiagTraining() {
    runCvsmTrainingTest(parseExamples(diagExamples, diagTargets), diagCvsmFamily, new CvsmSquareLoss(), -1);
  }

  public void testCvsmSoftmaxTraining() {
    runCvsmTrainingTest(parseExamples(softmaxExamples, softmaxTargets), cvsmFamily, new CvsmSquareLoss(), -1);
  }
  
  public void testLowRankCvsmSoftmaxTraining() {
    runCvsmTrainingTest(parseExamples(softmaxExamples, softmaxTargets), lowRankCvsmFamily, new CvsmSquareLoss(), 5000);
  }

  public void testCvsmLogisticTraining() {
    runCvsmTrainingTest(parseExamples(logisticExamples, logisticTargets), cvsmFamily, new CvsmSquareLoss(), -1);
  }
  
  public void testLowRankCvsmLogisticTraining() {
    runCvsmTrainingTest(parseExamples(logisticExamples, logisticTargets), lowRankCvsmFamily, new CvsmSquareLoss(), -1);
  }
  
  public void testCvsmTanhTraining() {
    runCvsmTrainingTest(parseExamples(tanhExamples, tanhTargets), cvsmFamily, new CvsmSquareLoss(), -1);
  }
  
  public void testLowRankCvsmTanhTraining() {
    runCvsmTrainingTest(parseExamples(tanhExamples, tanhTargets), lowRankCvsmFamily, new CvsmSquareLoss(), -1);
  }
  
  public void testCvsmLogTraining() {
    runCvsmTrainingTest(parseExamples(logExamples, logTargets), cvsmFamily, new CvsmSquareLoss(), -1);
  }
  
  public void testLowRankCvsmLogTraining() {
    runCvsmTrainingTest(parseExamples(logExamples, logTargets), lowRankCvsmFamily, new CvsmSquareLoss(), -1);
  }

  public void testCvsmTensorTraining() {
    List<CvsmExample> cvsmTensorExamples = parseExamples(tprodExamples, tprodTargets);
    runCvsmTrainingTest(cvsmTensorExamples, cvsmFamily, new CvsmSquareLoss(), -1);
  }
  
  public void testLowRankCvsmTensorTraining() {
    List<CvsmExample> cvsmTensorExamples = parseExamples(tprodExamples, tprodTargets);
    runCvsmTrainingTest(cvsmTensorExamples, lowRankCvsmFamily, new CvsmSquareLoss(), 15000);
  }
  
  public void testCvsmValueTraining() {
    List<CvsmExample> examples = parseExamples(valueExamples, valueTargets);
    runCvsmTrainingTest(examples, cvsmFamily, new CvsmValueLoss(), 1000);
  }

  private static void runCvsmTrainingTest(List<CvsmExample> cvsmExamples,
      CvsmFamily cvsmFamily, CvsmLoss loss, int iterations) {
    if (iterations == -1) { iterations = 1000; } 

    CvsmLoglikelihoodOracle oracle = new CvsmLoglikelihoodOracle(cvsmFamily, loss);

    List<StochasticGradientTrainer> trainers = Arrays.asList(
        StochasticGradientTrainer.createWithL2Regularization(iterations, 1, 1.0, true, false, 0.0, new NullLogFunction()),
        StochasticGradientTrainer.createAdagrad(iterations, 1, 1.0, true, false, 0.0, 0.0, new NullLogFunction()));

    for (StochasticGradientTrainer trainer : trainers) {
      SufficientStatistics initialParameters = cvsmFamily.getNewSufficientStatistics();
      initializeParameters(cvsmFamily, initialParameters);

      SufficientStatistics parameters = trainer.train(oracle,
          initialParameters, cvsmExamples);

      System.out.println(cvsmFamily.getParameterDescription(parameters));

      Cvsm cvsm = cvsmFamily.getModelFromParameters(parameters);

      for (CvsmExample example : cvsmExamples) {
        CvsmTree tree = cvsm.getInterpretationTree(example.getLogicalForm());
        CvsmTree augmentedTree = loss.augmentTreeWithLoss(tree, cvsm, example.getTargets());

        double squareLoss = augmentedTree.getLoss();
        System.out.println(example.getLogicalForm() + " loss: " + squareLoss);
        assertTrue(squareLoss <= 0.1);
      }
    }
  }

  private static void initializeParameters(CvsmFamily family, SufficientStatistics parameters) {
    // We can initialize matrix and tensor parameters to the identity using:
    // family.initializeParametersToIdentity(parameters);
    Pseudorandom.get().setSeed(0L);
    parameters.perturb(0.1);
  }

  private static List<CvsmExample> parseExamples(String[] examples, double[][] targets) {
    Preconditions.checkArgument(examples.length == targets.length);
    ExpressionParser<Expression> exp = ExpressionParser.lambdaCalculus();

    List<CvsmExample> cvsmExamples = Lists.newArrayList();
    for (int i = 0; i < examples.length; i++) {
      Tensor target = new DenseTensor(new int[] { 0 }, new int[] { targets[i].length }, targets[i]);

      cvsmExamples.add(new CvsmExample(exp.parseSingleExpression(examples[i]), target, null));
    }

    return cvsmExamples;
  }
}
