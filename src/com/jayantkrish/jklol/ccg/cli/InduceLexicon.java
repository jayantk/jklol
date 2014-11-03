package com.jayantkrish.jklol.ccg.cli;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.lambda.MapTypeContext;
import com.jayantkrish.jklol.ccg.lambda.TypeContext;
import com.jayantkrish.jklol.ccg.lexinduct.BatchLexiconInduction;
import com.jayantkrish.jklol.ccg.lexinduct.UnificationLexiconInductionStrategy;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.util.IoUtils;

public class InduceLexicon extends AbstractCli {

  private OptionSpec<String> trainingData;
  private OptionSpec<String> modelOutput;
  private OptionSpec<String> typeDeclarations;

  private OptionSpec<Integer> beamSize;
  private OptionSpec<Integer> lexiconInductionIterations;

  public InduceLexicon() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    typeDeclarations = parser.accepts("typeDeclarations").withRequiredArg().ofType(String.class).required();

    // Optional options
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    lexiconInductionIterations = parser.accepts("lexiconInductionIterations").withRequiredArg()
        .ofType(Integer.class).defaultsTo(10);
  }

  @Override
  public void run(OptionSet options) {
    TypeContext context = MapTypeContext.readTypeDeclarations(IoUtils.readLines(
        options.valueOf(typeDeclarations)));

    List<CcgExample> trainingExamples = TrainSemanticParser.readCcgExamples(
        options.valueOf(trainingData));
    System.out.println("Read " + trainingExamples.size() + " training examples");

    // The CCG parser doesn't like being initialized without rules.
    List<CcgBinaryRule> binaryRules = Lists.newArrayList(CcgBinaryRule.parseFrom(
        "NOT_A_CAT{0} NOT_A_CAT{0} NOT_A_CAT{0},,OTHER,NOT_A_DEP 0 0"));
    List<CcgUnaryRule> unaryRules = Lists.newArrayList(CcgUnaryRule.parseFrom(
        "NOT_A_CAT{0} NOT_A_CAT{0}"));
    
    CcgInference inference = new CcgBeamSearchInference(null, options.valueOf(beamSize),
        -1, Integer.MAX_VALUE, 1, false);
    GradientOptimizer trainer = createGradientOptimizer(trainingExamples.size());
    BatchLexiconInduction lexiconInduction = new BatchLexiconInduction(
        options.valueOf(lexiconInductionIterations), true, false, false,
        new DefaultCcgFeatureFactory(null, false), binaryRules, unaryRules, trainer,
        new UnificationLexiconInductionStrategy(inference, context), new DefaultLogFunction());

    CcgParser parser = lexiconInduction.induceLexicon(trainingExamples);
    IoUtils.serializeObjectToFile(parser, options.valueOf(modelOutput));
  }

  public static void main(String[] args) {
    new InduceLexicon().run(args);
  }
}
