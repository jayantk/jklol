package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.ExpectationMaximization;
import com.jayantkrish.jklol.training.Lbfgs;

public class AlignmentModelTrainingTest extends TestCase {

  String[][] dataSet1 = new String[][] {{"is plano in texas", "(in:<e,<e,t>> plano:e texas:e)"},
      {"what plano in us", "(in:<e,<e,t>> plano:e us:e)"},
      {"texas in us", "(in:<e,<e,t>> texas:e us:e)"},
      {"us in plano", "(in:<e,<e,t>> us:e plano:e)"},
      {"does texas border plano ?", "(border:<e,<e,t>> texas:e plano:e)"},
      {"city in texas", "(lambda x (and:<t*,t> (city:<e,t> x) (in:<e,<e,t>> x texas:e)))"},
      {"major city in texas", "(lambda x (and:<t*,t> (major:<e,t> x) (city:<e,t> x) (in:<e,<e,t>> x texas:e)))"},
      {"state in us", "(lambda x (and:<t*,t> (state:<e,t> x) (in:<e,<e,t>> x us:e)))"},
      {"biggest city", "(argmax:<<e,t>,<<e,i>,e>> (lambda x (city:<e,t> x)) (lambda x (size:<e,i> x)))"},
      {"biggest state", "(argmax:<<e,t>,<<e,i>,e>> (lambda x (state:<e,t> x)) (lambda x (size:<e,i> x)))"},
      {"what city is the biggest in texas", "(argmax:<<e,t>,<<e,i>,e>> (lambda x (and:<t*,t> (city:<e,t> x) (in:<e,<e,t>> x texas:e))) (lambda x (size:<e,i> x)))"}
  };
  
  VariableNumMap wordVarPattern, expressionVarPattern;
  
  List<AlignmentExample> examples;
  TypeDeclaration typeDeclaration;
  
  public void setUp() {
    examples = parseData(dataSet1);
    
    Set<Expression2> allExpressions = Sets.newHashSet();
    for (AlignmentExample example : examples) {
      example.getTree().getAllExpressions(allExpressions);
    }
    
    typeDeclaration = ExplicitTypeDeclaration.getDefault();
  }

  public static List<AlignmentExample> parseData(String[][] data) {
    List<AlignmentExample> examples = Lists.newArrayList();
    for (int i = 0; i < data.length; i++) {
      ExpressionTree tree = ExpressionTree.fromExpression(ExpressionParser
        .expression2().parseSingleExpression(data[i][1]));
      List<String> words = Arrays.asList(data[i][0].split(" "));
      System.out.println(words);
      System.out.println(tree);
      examples.add(new AlignmentExample(words, tree));
    }
    return examples;
  }

  public void testTrainingCfg() {
    ParametricCfgAlignmentModel pam = ParametricCfgAlignmentModel.buildAlignmentModelWithNGrams(
        examples, 1, typeDeclaration, false);

    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(0.1);

    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);

    ExpectationMaximization em = new ExpectationMaximization(20, new DefaultLogFunction(10, false));
    SufficientStatistics trainedParameters2 = em.train(new CfgAlignmentEmOracle(pam, smoothing, null, true),
        initial, examples);
    
    trainedParameters2 = em.train(new CfgAlignmentEmOracle(pam, smoothing, null, false),
        trainedParameters2, examples);
    

    // TODO: put in an actual test here.
    System.out.println(pam.getParameterDescription(trainedParameters2, 50));
    CfgAlignmentModel model = pam.getModelFromParameters(trainedParameters2);
    for (AlignmentExample example : examples) {
      System.out.println(example.getWords());
      System.out.println(model.getBestAlignment(example));
    }
  }

  public void testTrainingCfgLoglinear() {
    ParametricCfgAlignmentModel pam = ParametricCfgAlignmentModel.buildAlignmentModelWithNGrams(
        examples, 1, typeDeclaration, true);

    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1.0);
    
    int numIterations = 1000;
    Lbfgs lbfgs = new Lbfgs(numIterations, 10, 1e-6, new DefaultLogFunction(numIterations - 1, false));

    ExpectationMaximization em = new ExpectationMaximization(50, new DefaultLogFunction());
    SufficientStatistics trainedParameters2 = em.train(new CfgAlignmentEmOracle(pam, null, lbfgs, false),
        initial, examples);

    // TODO: put in an actual test here.
    System.out.println(pam.getParameterDescription(trainedParameters2));
    CfgAlignmentModel model = pam.getModelFromParameters(trainedParameters2);
    for (AlignmentExample example : examples) {
      System.out.println(example.getWords());
      System.out.println(model.getBestAlignment(example));
    }
  }
}
