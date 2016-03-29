package com.jayantkrish.jklol.ccg.cli;

import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.util.SemanticParserExampleLoss;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.util.IoUtils;

public class TestSemanticParser extends AbstractCli {

  private OptionSpec<String> testData;
  private OptionSpec<String> model;
  
  private OptionSpec<String> errorJson;

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    testData = parser.accepts("testData").withRequiredArg().ofType(String.class).required();
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();

    // If provided, outputs a log of errors in JSON format to the given file.
    errorJson = parser.accepts("errorJson").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    List<CcgExample> testExamples = TrainSemanticParser.readCcgExamples(options.valueOf(testData));
    System.out.println("Read " + testExamples.size() + " test examples");

    CcgParser parser = IoUtils.readSerializedObject(options.valueOf(model), CcgParser.class);
    CcgCkyInference inferenceAlg = CcgCkyInference.getDefault(300);
    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);
    
    List<SemanticParserExampleLoss> exampleLosses = Lists.newArrayList();
    SemanticParserUtils.testSemanticParser(testExamples, parser, inferenceAlg, simplifier,
        comparator, exampleLosses, true);

    if (options.has(errorJson)) {
      SemanticParserExampleLoss.writeJsonToFile(options.valueOf(errorJson), exampleLosses);
    }
  }

  public static void main(String[] args) {
    new TestSemanticParser().run(args);
  }
}
