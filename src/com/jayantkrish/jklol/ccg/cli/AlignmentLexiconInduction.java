package com.jayantkrish.jklol.ccg.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentEmOracle;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentModel;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.ParametricAlignmentModel;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.ExpectationMaximization;

public class AlignmentLexiconInduction extends AbstractCli {
  
  private OptionSpec<String> trainingData;
  
  private OptionSpec<Void> noTreeConstraint;

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    
    // Optional arguments
    noTreeConstraint = parser.accepts("noTreeConstraint");
  }

  @Override
  public void run(OptionSet options) {
    List<AlignmentExample> examples = readTrainingData(options.valueOf(trainingData));
    
    for (AlignmentExample example : examples) {
      System.out.println(example.getWords());
      System.out.println(example.getTree());
    }

    ParametricAlignmentModel pam = ParametricAlignmentModel.buildAlignmentModel(
        examples, !options.has(noTreeConstraint));
    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(0.1);
    
    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);

    ExpectationMaximization em = new ExpectationMaximization(10, new DefaultLogFunction());
    SufficientStatistics trainedParameters = em.train(new AlignmentEmOracle(pam, new JunctionTree(true), smoothing),
        initial, examples);

    System.out.println(pam.getParameterDescription(trainedParameters, 300));

    AlignmentModel model = pam.getModelFromParameters(trainedParameters);
    for (AlignmentExample example : examples) {
      System.out.println(example.getWords());
      model.getBestAlignment(example);
    }
  }

  private static List<AlignmentExample> readTrainingData(String trainingDataFile) {
    List<CcgExample> ccgExamples = TrainSemanticParser.readCcgExamples(trainingDataFile);
    List<AlignmentExample> examples = Lists.newArrayList();
    
    int totalTreeSize = 0; 
    for (CcgExample ccgExample : ccgExamples) {
      ExpressionTree tree = ExpressionTree.fromExpression(ccgExample.getLogicalForm());
      examples.add(new AlignmentExample(ccgExample.getSentence().getWords(), tree));

      totalTreeSize += tree.size();
    }
    System.out.println("Average tree size: " + (totalTreeSize / examples.size()));
    return examples;
  }

  public static void main(String[] args) {
    new AlignmentLexiconInduction().run(args);
  }
}
