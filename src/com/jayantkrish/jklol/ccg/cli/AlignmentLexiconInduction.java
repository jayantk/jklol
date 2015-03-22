package com.jayantkrish.jklol.ccg.cli;

import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
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
  
  private OptionSpec<Integer> emIterations;
  
  private OptionSpec<Double> smoothingParam;
  private OptionSpec<Void> noTreeConstraint;
  private OptionSpec<Void> sparseCpt;
  private OptionSpec<Void> printSearchSpace;
  
  public AlignmentLexiconInduction() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    
    // Optional arguments
    emIterations = parser.accepts("emIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    smoothingParam = parser.accepts("smoothing").withRequiredArg().ofType(Double.class).defaultsTo(1.0);
    noTreeConstraint = parser.accepts("noTreeConstraint");
    sparseCpt = parser.accepts("sparseCpt");
    printSearchSpace = parser.accepts("printSearchSpace");
  }

  @Override
  public void run(OptionSet options) {
    List<AlignmentExample> examples = readTrainingData(options.valueOf(trainingData));
    
    if (options.has(printSearchSpace)) { 
      for (AlignmentExample example : examples) {
        System.out.println(example.getWords());
        System.out.println(example.getTree());
      }
    }

    ParametricAlignmentModel pam = ParametricAlignmentModel.buildAlignmentModel(
        examples, !options.has(noTreeConstraint), options.has(sparseCpt));
    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(options.valueOf(smoothingParam));
    
    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);

    ExpectationMaximization em = new ExpectationMaximization(options.valueOf(emIterations), new DefaultLogFunction());
    SufficientStatistics trainedParameters = em.train(new AlignmentEmOracle(pam, new JunctionTree(true), smoothing),
        initial, examples);

    // System.out.println(pam.getParameterDescription(trainedParameters, 300));

    AlignmentModel model = pam.getModelFromParameters(trainedParameters);
    for (AlignmentExample example : examples) {
      System.out.println(example.getWords());
      model.getBestAlignment(example);
    }
  }

  private static List<AlignmentExample> readTrainingData(String trainingDataFile) {
    List<CcgExample> ccgExamples = TrainSemanticParser.readCcgExamples(trainingDataFile);
    List<AlignmentExample> examples = Lists.newArrayList();
    Set<ConstantExpression> constantsDontCount = Sets.newHashSet();
    constantsDontCount.add(new ConstantExpression("and:<t*,t>"));
    
    int totalTreeSize = 0; 
    for (CcgExample ccgExample : ccgExamples) {
      ExpressionTree tree = ExpressionTree.fromExpression(ccgExample.getLogicalForm(), constantsDontCount);
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
