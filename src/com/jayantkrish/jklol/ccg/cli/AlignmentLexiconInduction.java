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
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexinduct.AlignedExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentModelInterface;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentEmOracle;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTokenFeatureGenerator;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.ParametricCfgAlignmentModel;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
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
  
  // TODO: this shouldn't be hard coded. Replace with 
  // an input unification lattice for types.
  private static final Map<String, String> typeReplacements = Maps.newHashMap();
  static {
    typeReplacements.put("lo", "e");
    typeReplacements.put("c", "e");
    typeReplacements.put("co", "e");
    typeReplacements.put("s", "e");
    typeReplacements.put("r", "e");
    typeReplacements.put("l", "e");
    typeReplacements.put("m", "e");
    typeReplacements.put("p", "e");
  }

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
    List<AlignmentExample> examples = readTrainingData(options.valueOf(trainingData));
    
    if (options.has(printSearchSpace)) { 
      for (AlignmentExample example : examples) {
        System.out.println(example.getWords());
        System.out.println(example.getTree());
      }
    }
    FeatureVectorGenerator<Expression2> vectorGenerator = buildFeatureVectorGenerator(examples,
        Collections.<String>emptyList());
    System.out.println("features: " + vectorGenerator.getFeatureDictionary().getValues());
    examples = applyFeatureVectorGenerator(vectorGenerator, examples);

    
    AlignmentModelInterface model = null;
    ParametricCfgAlignmentModel pam = ParametricCfgAlignmentModel.buildAlignmentModelWithNGrams(
        examples, vectorGenerator, options.valueOf(nGramLength), false, false);
    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(options.valueOf(smoothingParam));

    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);

    ExpectationMaximization em = new ExpectationMaximization(options.valueOf(emIterations), new DefaultLogFunction());
    SufficientStatistics trainedParameters = em.train(new CfgAlignmentEmOracle(pam, smoothing),
        initial, examples);

    if (options.has(printParameters)) {
      System.out.println(pam.getParameterDescription(trainedParameters));
    }

    model = pam.getModelFromParameters(trainedParameters);
    pam.getModelFromParameters(trainedParameters).printStuffOut();

    PairCountAccumulator<List<String>, LexiconEntry> alignments = generateLexiconFromAlignmentModel(
        model, examples, 1, typeReplacements);
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
  
  public static PairCountAccumulator<List<String>, LexiconEntry> generateLexiconFromAlignmentModel(
      AlignmentModelInterface model, Collection<AlignmentExample> examples, int lexiconNumParses,
      Map<String, String> typeReplacements) {
    PairCountAccumulator<List<String>, LexiconEntry> alignments = PairCountAccumulator.create();
    for (AlignmentExample example : examples) {
      AlignedExpressionTree tree = model.getBestAlignment(example);
      
      System.out.println(example.getWords());
      System.out.println(tree);

      for (LexiconEntry entry : tree.generateLexiconEntries(typeReplacements)) {
        alignments.incrementOutcome(entry.getWords(), entry, 1);
        System.out.println("   " + entry);
      }
      System.out.println("");

    }
    return alignments;
  }

  public static FeatureVectorGenerator<Expression2> buildFeatureVectorGenerator(
      List<AlignmentExample> examples, Collection<String> tokensToIgnore) {
    Set<Expression2> allExpressions = Sets.newHashSet();
    for (AlignmentExample example : examples) {
      example.getTree().getAllExpressions(allExpressions);
    }
    return DictionaryFeatureVectorGenerator.createFromData(allExpressions,
        new ExpressionTokenFeatureGenerator(tokensToIgnore), false);
  }

  public static List<AlignmentExample> applyFeatureVectorGenerator(
      FeatureVectorGenerator<Expression2> generator, List<AlignmentExample> examples) {
    List<AlignmentExample> newExamples = Lists.newArrayList();
    for (AlignmentExample example : examples) {
      ExpressionTree newTree = example.getTree().applyFeatureVectorGenerator(generator);
      newExamples.add(new AlignmentExample(example.getWords(), newTree));
    }
    return newExamples;
  }

  public static List<AlignmentExample> readTrainingData(String trainingDataFile) {
    List<CcgExample> ccgExamples = TrainSemanticParser.readCcgExamples(trainingDataFile);
    List<AlignmentExample> examples = Lists.newArrayList();

    System.out.println(trainingDataFile);
    int totalTreeSize = 0; 
    for (CcgExample ccgExample : ccgExamples) {
      ExpressionTree tree = expressionToExpressionTree(ccgExample.getLogicalForm());
      examples.add(new AlignmentExample(ccgExample.getSentence().getWords(), tree));

      totalTreeSize += tree.size();
    }
    System.out.println("Average tree size: " + (totalTreeSize / examples.size()));
    return examples;
  }

  public static ExpressionTree expressionToExpressionTree(Expression2 expression) {
    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    Set<String> constantsToIgnore = Sets.newHashSet("and:<t*,t>");

    return ExpressionTree.fromExpression(expression, simplifier, typeReplacements,
        constantsToIgnore, 0, 2, 3);
  }

  public static void main(String[] args) {
    new AlignmentLexiconInduction().run(args);
  }
}
