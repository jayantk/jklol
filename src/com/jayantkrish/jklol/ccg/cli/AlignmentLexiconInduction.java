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
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lexinduct.AlignedExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.AlignedExpressionTree.AlignedExpression;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentEmOracle;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentModel;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTokenFeatureGenerator;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.ParametricAlignmentModel;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.inference.JunctionTree;
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
  
  private OptionSpec<Integer> emIterations;
  
  private OptionSpec<Double> smoothingParam;
  private OptionSpec<Void> noTreeConstraint;
  private OptionSpec<Void> printSearchSpace;
  
  public AlignmentLexiconInduction() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    lexiconOutput = parser.accepts("lexiconOutput").withRequiredArg().ofType(String.class).required();
    
    // Optional arguments
    emIterations = parser.accepts("emIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    smoothingParam = parser.accepts("smoothing").withRequiredArg().ofType(Double.class).defaultsTo(1.0);
    noTreeConstraint = parser.accepts("noTreeConstraint");
    printSearchSpace = parser.accepts("printSearchSpace");
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

    List<AlignmentExample> examples = readTrainingData(options.valueOf(trainingData),
        typeReplacements);
    
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

    ParametricAlignmentModel pam = ParametricAlignmentModel.buildAlignmentModel(
        examples, !options.has(noTreeConstraint), false, vectorGenerator);
    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(options.valueOf(smoothingParam));
    
    SufficientStatistics initial = pam.getNewSufficientStatistics();
    initial.increment(1);

    ExpectationMaximization em = new ExpectationMaximization(options.valueOf(emIterations), new DefaultLogFunction());
    SufficientStatistics trainedParameters = em.train(new AlignmentEmOracle(pam, new JunctionTree(true), smoothing),
        initial, examples);

    PairCountAccumulator<String, AlignedExpression> alignments = PairCountAccumulator.create();
    AlignmentModel model = pam.getModelFromParameters(trainedParameters);
    int numTreesWithFullAlignments = 0;
    for (AlignmentExample example : examples) {
      System.out.println(example.getWords());
      // AlignedExpressionTree tree = model.getBestAlignment(example);
      AlignedExpressionTree tree = model.getBestAlignmentCfg(example);
      System.out.println(tree);

      alignments.incrementOutcomes(tree.getWordAlignments(), 1);
      
      if (tree.getSpanStarts().length > 0) {
        numTreesWithFullAlignments++;
      }
    }
    System.out.println("Aligned: " + numTreesWithFullAlignments + " / " + examples.size());

    List<LexiconEntry> lexiconEntries = generateCcgLexicon(alignments, typeReplacements);

    List<String> lines = Lists.newArrayList();
    for (LexiconEntry lexiconEntry : lexiconEntries) {
      lines.add(lexiconEntry.toCsvString());
    }
    Collections.sort(lines);
    IoUtils.writeLines(options.valueOf(lexiconOutput), lines);
  }

  private static List<LexiconEntry> generateCcgLexicon(PairCountAccumulator<String, AlignedExpression> alignments,
      Map<String, String> typeReplacements) {
    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    List<String> words = Lists.newArrayList(alignments.keySet());
    Collections.sort(words);
    for (String key : words) {
      System.out.println(key + " (" + alignments.getTotalCount(key) + ")");
      int wordLexEntryCount = 0;
      for (AlignedExpression value : alignments.getValues(key)) {
        // Assign each lexicon entry a unique predicate name for its
        // head / dependencies
        String head = key + "_" + wordLexEntryCount;
        
        // Generate separate syntactic categories for each unbound
        // argument.
        Type type = StaticAnalysis.inferType(value.getExpression(), typeReplacements);
        // TODO: hack for geoquery: replace unknown entries with type e
        type = Type.parseFrom(type.toString().replaceAll("unknown", "e"));
        Type returnType = type;
        List<Type> argumentTypes = Lists.newArrayList();
        for (int i = 0; i < value.getNumAppliedArgs(); i++) {
          argumentTypes.add(returnType.getArgumentType());
          returnType = returnType.getReturnType();
        }
        Collections.reverse(argumentTypes);

        // Build a syntactic category for the expression based on the 
        // number of arguments it accepted in the sentence. Simultaneously
        // generate its dependencies and head assignment.
        List<String> subjects = Lists.newArrayList();
        List<Integer> argumentNums = Lists.newArrayList();
        List<Integer> objects = Lists.newArrayList();
        List<Set<String>> assignments = Lists.newArrayList();
        assignments.add(Sets.newHashSet(head));
        HeadedSyntacticCategory syntax = HeadedSyntacticCategory.parseFrom("N:" + returnType + "{0}");
        for (int i = 0; i < value.getNumAppliedArgs(); i++) {
          HeadedSyntacticCategory argSyntax = HeadedSyntacticCategory
              .parseFrom("N:" + argumentTypes.get(i) + "{" + (i + 1) +"}");
          syntax = syntax.addArgument(argSyntax, Direction.BOTH, 0);

          subjects.add(head);
          argumentNums.add(i + 1);
          objects.add(i + 1);
          assignments.add(Collections.<String>emptySet());
        }

        CcgCategory ccgCategory = new CcgCategory(syntax, value.getExpression(), subjects,
            argumentNums, objects, assignments);
        LexiconEntry entry = new LexiconEntry(Arrays.asList(key), ccgCategory);
        lexiconEntries.add(entry);
        
        double count = alignments.getCount(key, value);
        System.out.println("  " + count + " : " + entry);
        
        wordLexEntryCount++;
      }
    }

    return lexiconEntries;
  }

  private static FeatureVectorGenerator<Expression2> buildFeatureVectorGenerator(
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

  private static List<AlignmentExample> readTrainingData(String trainingDataFile,
      Map<String, String> typeReplacements) {
    List<CcgExample> ccgExamples = TrainSemanticParser.readCcgExamples(trainingDataFile);
    List<AlignmentExample> examples = Lists.newArrayList();
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();

    int totalTreeSize = 0; 
    for (CcgExample ccgExample : ccgExamples) {
      ExpressionTree tree = ExpressionTree.fromExpression(ccgExample.getLogicalForm(), simplifier,
          typeReplacements, 0, 2);
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
