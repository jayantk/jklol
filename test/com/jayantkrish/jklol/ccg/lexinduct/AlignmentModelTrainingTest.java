package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.ExpectationMaximization;

public class AlignmentModelTrainingTest extends TestCase {

  String[][] dataSet1 = new String[][] {{"plano in texas", "(in plano texas)"},
      {"in texas", "(lambda x (in x texas))"},
      {"in plano", "(lambda x (in x plano))"}};

  VariableNumMap wordVarPattern, expressionVarPattern;
  
  public static List<AlignmentExample> parseData(String[][] data) {
    List<AlignmentExample> examples = Lists.newArrayList();
    for (int i = 0; i < data.length; i++) {
      ExpressionTree tree = ExpressionTree.fromExpression(ExpressionParser
        .lambdaCalculus().parseSingleExpression(data[i][1]));
      List<String> words = Arrays.asList(data[i][0].split(" "));
      System.out.println(words);
      System.out.println(tree);
      examples.add(new AlignmentExample(words, tree));
    }
    return examples;
  }

  public void testTrainingSimple() {
    List<AlignmentExample> examples = parseData(dataSet1);
    ParametricAlignmentModel pam = ParametricAlignmentModel.buildAlignmentModel(examples, false, true);

    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(0.1);
    
    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);
    
    // MapReduceConfiguration.setMapReduceExecutor(new LocalMapReduceExecutor(1, 1));
    ExpectationMaximization em = new ExpectationMaximization(30, new DefaultLogFunction());
    SufficientStatistics trainedParameters = em.train(new AlignmentEmOracle(pam, new JunctionTree(), smoothing),
        initial, examples);

    // TODO: put in an actual test here.
    System.out.println(pam.getParameterDescription(trainedParameters, 30));

    pam = pam.updateUseTreeConstraint(true);
    SufficientStatistics trainedParameters2 = em.train(new AlignmentEmOracle(pam, new JunctionTree(), smoothing),
        trainedParameters, examples);
    
    // TODO: put in an actual test here.
    System.out.println(pam.getParameterDescription(trainedParameters2, 30));
    AlignmentModel model = pam.getModelFromParameters(trainedParameters2);
    for (AlignmentExample example : examples) {
      model.getBestAlignment(example);
    }
  }
}
