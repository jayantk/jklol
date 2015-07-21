package com.jayantkrish.jklol.ccg.cli;

import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgExactInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.lambda2.ConjunctionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.util.IoUtils;

public class TestSemanticParser extends AbstractCli {

  private OptionSpec<String> testData;
  private OptionSpec<String> model;
  
  private OptionSpec<Void> skipWords;

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    testData = parser.accepts("testData").withRequiredArg().ofType(String.class).required();
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    
    // FIXME: eliminate this option when refactoring the word skipping behavior.
    skipWords = parser.accepts("skipWords", "Allow the parser to skip words in the parse");
  }

  @Override
  public void run(OptionSet options) {
    List<CcgExample> testExamples = TrainSemanticParser.readCcgExamples(
        options.valueOf(testData), null, options.has(skipWords));
    System.out.println("Read " + testExamples.size() + " test examples");

    CcgParser parser = IoUtils.readSerializedObject(options.valueOf(model), CcgParser.class);
    CcgInference inferenceAlg = new CcgExactInference(null, -1L, Integer.MAX_VALUE, 1);
    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new ConjunctionReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    SemanticParserUtils.testSemanticParser(testExamples, parser, inferenceAlg, simplifier, comparator);
  }

  public static void main(String[] args) {
    new TestSemanticParser().run(args);
  }
}
