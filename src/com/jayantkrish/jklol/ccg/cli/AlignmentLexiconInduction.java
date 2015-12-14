package com.jayantkrish.jklol.ccg.cli;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexinduct.AlignedExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentEmOracle;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentModel;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.ParametricCfgAlignmentModel;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.ExpectationMaximization;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.PairCountAccumulator;

public class AlignmentLexiconInduction extends AbstractCli {
  
  private OptionSpec<String> trainingData;
  private OptionSpec<String> lexiconOutput;
  private OptionSpec<String> modelOutput;
  
  private OptionSpec<Integer> emIterations;
  
  private OptionSpec<Double> smoothingParam;
  private OptionSpec<Integer> nGramLength;
  private OptionSpec<Void> printSearchSpace;
  private OptionSpec<Void> printParameters;
  
  public AlignmentLexiconInduction() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    lexiconOutput = parser.accepts("lexiconOutput").withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("modelOutput").withRequiredArg().ofType(String.class).required();
    
    // Optional arguments
    emIterations = parser.accepts("emIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    smoothingParam = parser.accepts("smoothing").withRequiredArg().ofType(Double.class).defaultsTo(1.0);
    nGramLength = parser.accepts("nGramLength").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    printSearchSpace = parser.accepts("printSearchSpace");
    printParameters = parser.accepts("printParameters");
  }

  @Override
  public void run(OptionSet options) {    
    // TODO: this shouldn't be hard coded. Replace with 
    // an input unification lattice for types.
    Map<String, String> typeReplacements = Maps.newHashMap();
    typeReplacements.put("lo", "e");
    typeReplacements.put("c", "e");
    typeReplacements.put("co", "e");
    typeReplacements.put("s", "e");
    typeReplacements.put("r", "e");
    typeReplacements.put("l", "e");
    typeReplacements.put("m", "e");
    typeReplacements.put("p", "e");
    TypeDeclaration typeDeclaration = new ExplicitTypeDeclaration(typeReplacements);

    List<AlignmentExample> examples = readTrainingData(options.valueOf(trainingData), typeDeclaration);
    
    if (options.has(printSearchSpace)) { 
      for (AlignmentExample example : examples) {
        System.out.println(example.getWords());
        System.out.println(example.getTree());
      }
    }
    
    ParametricCfgAlignmentModel pam = ParametricCfgAlignmentModel.buildAlignmentModelWithNGrams(
        examples, options.valueOf(nGramLength), typeDeclaration, false);
    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(options.valueOf(smoothingParam));

    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);

    ExpectationMaximization em = new ExpectationMaximization(options.valueOf(emIterations),
        new DefaultLogFunction());
    SufficientStatistics trainedParameters = em.train(
        new CfgAlignmentEmOracle(pam, smoothing, null, false), initial, examples);

    if (options.has(printParameters)) {
      System.out.println(pam.getParameterDescription(trainedParameters));
    }

    CfgAlignmentModel model = pam.getModelFromParameters(trainedParameters);

    PairCountAccumulator<List<String>, LexiconEntry> alignments = model.generateLexicon(
        examples, 1, typeDeclaration);
    for (List<String> words : alignments.keySet()) {
      System.out.println(words);
      for (LexiconEntry entry : alignments.getValues(words)) {
        System.out.println("  " + alignments.getCount(words, entry) + " " + entry);
      }
    }

    Collection<LexiconEntry> allEntries = alignments.getKeyValueMultimap().values();
    List<String> lines = Lists.newArrayList();
    for (LexiconEntry lexiconEntry : allEntries) {
      lines.add(lexiconEntry.toCsvString());
    }
    Collections.sort(lines);
    IoUtils.writeLines(options.valueOf(lexiconOutput), lines);

    IoUtils.serializeObjectToFile(model, options.valueOf(modelOutput));
  }
  
  public static List<AlignmentExample> readTrainingData(String trainingDataFile, TypeDeclaration typeDeclaration) {
    List<CcgExample> ccgExamples = TrainSemanticParser.readCcgExamples(trainingDataFile);
    List<AlignmentExample> examples = Lists.newArrayList();

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    Set<String> constantsToIgnore = Sets.newHashSet("and:<t*,t>");
    
    System.out.println(trainingDataFile);
    int totalTreeSize = 0; 
    for (CcgExample ccgExample : ccgExamples) {
      ExpressionTree tree = ExpressionTree.fromExpression(ccgExample.getLogicalForm(),
          simplifier, typeDeclaration, constantsToIgnore, 0, 2, 3);
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
