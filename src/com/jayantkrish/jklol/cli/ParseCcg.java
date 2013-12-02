package com.jayantkrish.jklol.cli;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgExactInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgParserUtils;
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.SupertaggingCcgParser;
import com.jayantkrish.jklol.ccg.SupertaggingCcgParser.CcgParseResult;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.CcgExactHashTableChart;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartCost;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.Supertagger;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.LogFunctions;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Parses input sentences using a trained CCG parser.
 * 
 * @author jayant
 */
public class ParseCcg extends AbstractCli {
  
  private OptionSpec<String> model;
  
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Integer> numParses;
  private OptionSpec<Long> maxParseTimeMillis;
  private OptionSpec<Integer> maxChartSize;
  private OptionSpec<Void> atomic;
  private OptionSpec<Void> pos;
  private OptionSpec<Void> printLf;
  private OptionSpec<Void> exactInference;
  
  private OptionSpec<String> testFile;
  private OptionSpec<String> syntaxMap;
  private OptionSpec<Void> useCcgBankFormat;
  private OptionSpec<Void> useGoldSyntacticTrees;
  private OptionSpec<Void> filterDependenciesCcgbank;

  private OptionSpec<String> supertagger;
  private OptionSpec<Double> multitagThresholds;
  
  public ParseCcg() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    model = parser.accepts("model", "File containing serialized CCG parser.").withRequiredArg()
        .ofType(String.class).required();
    // Optional arguments
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    numParses = parser.accepts("numParses").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    maxParseTimeMillis = parser.accepts("maxParseTimeMillis").withRequiredArg().ofType(Long.class).defaultsTo(-1L);
    maxChartSize = parser.accepts("maxChartSize").withRequiredArg().ofType(Integer.class).defaultsTo(Integer.MAX_VALUE);
    atomic = parser.accepts("atomic", "Only print parses whose root category is atomic (i.e., non-functional).");
    pos = parser.accepts("pos", "Treat input as POS-tagged text, in the format word/POS.");
    printLf = parser.accepts("printLf", "Print logical forms for the generated parses.");
    exactInference = parser.accepts("exactInference");

    testFile = parser.accepts("test", "If provided, running this program computes test error using " +
    		"the given file. Otherwise, this program parses a string provided on the command line. " +
        "The format of testFile is the same as expected by TrainCcg to train a CCG parser.")
        .withRequiredArg().ofType(String.class);
    syntaxMap = parser.accepts("syntaxMap").withRequiredArg().ofType(String.class);
    useCcgBankFormat = parser.accepts("useCcgBankFormat", "Reads the parses in testFile in CCGbank format.");
    useGoldSyntacticTrees = parser.accepts("useGoldSyntacticTrees");
    filterDependenciesCcgbank = parser.accepts("filterDependenciesCcgbank");

    supertagger = parser.accepts("supertagger").withRequiredArg().ofType(String.class);
    multitagThresholds = parser.accepts("multitagThreshold").withRequiredArg().ofType(Double.class).withValuesSeparatedBy(',');
  }

  @Override
  public void run(OptionSet options) {
    // Read the parser.
    CcgParser ccgParser = IoUtils.readSerializedObject(options.valueOf(model), CcgParser.class);

    // Configure inference options
    CcgInference inferenceAlgorithm = null;
    if (options.has(exactInference)) {
      inferenceAlgorithm = new CcgExactInference(null, options.valueOf(maxParseTimeMillis),
          options.valueOf(maxChartSize));
    } else {
      inferenceAlgorithm = new CcgBeamSearchInference(null, options.valueOf(beamSize),
          options.valueOf(maxParseTimeMillis), options.valueOf(maxChartSize), true);
    }
    
    if (options.has(testFile)) {
      // Parse all test examples.
      List<CcgExample> testExamples = TrainCcg.readTrainingData(
          options.valueOf(testFile), false, options.has(useCcgBankFormat), options.valueOf(syntaxMap));
      System.out.println(testExamples.size() + " test examples after filtering.");

      Supertagger tagger = null;
      double[] tagThresholds = new double[0];
      if (options.has(supertagger)) {
        Preconditions.checkState(options.has(multitagThresholds));
        tagger = IoUtils.readSerializedObject(options.valueOf(supertagger), Supertagger.class);
        tagThresholds = Doubles.toArray(options.valuesOf(multitagThresholds));
      }

      LogFunctions.getLogFunction().notifyIterationStart(0);
      SupertaggingCcgParser supertaggingParser = new SupertaggingCcgParser(ccgParser,
          inferenceAlgorithm, tagger, tagThresholds);
      CcgLoss loss = runTestSetEvaluation(testExamples, supertaggingParser,
          options.has(useGoldSyntacticTrees), options.has(filterDependenciesCcgbank));
      LogFunctions.getLogFunction().notifyIterationEnd(0);
      System.out.println(loss);
    } else {
      // Parse a string from the command line.
      List<String> input = Lists.newArrayList(options.nonOptionArguments());
      List<String> sentenceToParse = Lists.newArrayList();
      List<String> posTags = Lists.newArrayList();
      if (options.has(pos)) {
        parsePosTaggedInput(input, sentenceToParse, posTags);
      } else {
        sentenceToParse = input;
        posTags = Collections.nCopies(sentenceToParse.size(), ParametricCcgParser.DEFAULT_POS_TAG);
      }
      SupertaggedSentence sentence = ListSupertaggedSentence.createWithUnobservedSupertags(
          sentenceToParse, posTags);

      List<CcgParse> parses = ccgParser.beamSearch(sentence, options.valueOf(beamSize));
      printCcgParses(parses, options.valueOf(numParses), options.has(atomic), options.has(printLf));
    }
  }
  
  public static void parsePosTaggedInput(List<String> input, List<String> wordAccumulator,
      List<String> posAccumulator) {
    for (String token : input) {
      String[] chunks = token.split("/");
      wordAccumulator.add(chunks[0]);
      posAccumulator.add(chunks[1]);
    }
  }

  public static void main(String[] args) {
    new ParseCcg().run(args);
  }
  
  public static void printCcgParses(List<CcgParse> parses, int numParses, boolean onlyPrintAtomic, boolean printLf) {
    int numPrinted = 0;
    for (int i = 0; i < parses.size() && numPrinted < numParses; i++) {
      if (!onlyPrintAtomic || parses.get(i).getSyntacticCategory().isAtomic()) {
        if (numPrinted > 0) {
          System.out.println("---");
        }
        System.out.println("HEAD: " + parses.get(i).getSemanticHeads());
        System.out.println("SYN: " + parses.get(i).getSyntacticParse());

        if (printLf) {
          Expression logicalForm = parses.get(i).getLogicalForm();
          if (logicalForm != null) {
            logicalForm = logicalForm.simplify();
          }
          System.out.println("LF: " + logicalForm);
        }

        System.out.println("DEPS: " + parses.get(i).getAllDependencies());
        System.out.println("LEX: " + parses.get(i).getSpannedLexiconEntries());
        System.out.println("PROB: " + parses.get(i).getSubtreeProbability());
        numPrinted++;
      }
    }
  }

  public static CcgLoss runTestSetEvaluation(Collection<CcgExample> testExamples, 
      SupertaggingCcgParser ccgParser, boolean useCcgbankDerivations, boolean filterDependenciesCcgbank) {
    CcgLossMapper mapper = new CcgLossMapper(ccgParser, useCcgbankDerivations, filterDependenciesCcgbank);
    CcgLossReducer reducer = new CcgLossReducer();
    return MapReduceConfiguration.getMapReduceExecutor().mapReduce(testExamples, mapper, reducer);
  }
  
  public static CcgLoss computeLoss(CcgParseResult parseResult, CcgExample example,
      CcgParser parser, boolean filterDependenciesCcgbank) {
    CcgParse parse = parseResult.getParse();
    List<DependencyStructure> parseDeps = null;
    if (filterDependenciesCcgbank) {
      parseDeps = getDependenciesCcgbank(parse);
    } else {
      parseDeps = parse.getAllDependencies();
    }

    List<LabeledDep> predictedDeps = dependenciesToLabeledDeps(
        parseDeps, parse.getSyntacticParse()); 
    List<LabeledDep> trueDeps = dependenciesToLabeledDeps(example.getDependencies(),
        example.getSyntacticParse());

    List<String> words = example.getSentence().getWords();
    System.out.println("Predicted: ");
    for (LabeledDep dep : predictedDeps) {
      if (trueDeps.contains(dep)) {
        System.out.println(dep.toString(words));
      } else {
        System.out.println(dep.toString(words) + "\tINCORRECT");
      }
    }

    System.out.println("Missing true dependencies:");
    for (LabeledDep dep : trueDeps) {
      if (!predictedDeps.contains(dep)) {
        System.out.println(dep.toString(words));
      }
    }

    // Compute the correct / incorrect labeled dependencies for
    // the current example.
    Set<LabeledDep> incorrectDeps = Sets.newHashSet(predictedDeps);
    incorrectDeps.removeAll(trueDeps);
    Set<LabeledDep> correctDeps = Sets.newHashSet(predictedDeps);
    correctDeps.retainAll(trueDeps);
    int correct = correctDeps.size();
    int falsePositive = predictedDeps.size() - correctDeps.size();
    int falseNegative = trueDeps.size() - correctDeps.size();
    System.out.println();
    double precision = ((double) correct) / (correct + falsePositive);
    double recall = ((double) correct) / (correct + falseNegative);
    System.out.println("Labeled Precision: " + precision);
    System.out.println("Labeled Recall: " + recall);

    // Update the labeled dependency score accumulators for the
    // whole data set.
    int labeledTp = correct;
    int labeledFp = falsePositive;
    int labeledFn = falseNegative;

    // Compute the correct / incorrect unlabeled dependencies.
    Set<LabeledDep> unlabeledPredicted = stripDependencyLabels(predictedDeps);
    Set<LabeledDep> unlabeledTrueDeps = stripDependencyLabels(trueDeps);
    incorrectDeps = Sets.newHashSet(unlabeledPredicted);
    incorrectDeps.removeAll(unlabeledTrueDeps);
    correctDeps = Sets.newHashSet(unlabeledPredicted);
    correctDeps.retainAll(unlabeledTrueDeps);
    correct = correctDeps.size();
    falsePositive = unlabeledPredicted.size() - correctDeps.size();
    falseNegative = unlabeledTrueDeps.size() - correctDeps.size();
    precision = ((double) correct) / (correct + falsePositive);
    recall = ((double) correct) / (correct + falseNegative);
    System.out.println("Unlabeled Precision: " + precision);
    System.out.println("Unlabeled Recall: " + recall);

    int unlabeledTp = correct;
    int unlabeledFp = falsePositive;
    int unlabeledFn = falseNegative;
    
    // Compute the accuracies of the lexical categories.
    int correctSyntacticCategories = 0, supertaggerErrors = 0, lexiconErrors = 0, parserErrors = 0;
    SupertaggedSentence sentence = parseResult.getSentence();
    List<SyntacticCategory> predictedSyntacticCategories = Lists.newArrayList();
    words = example.getSentence().getWords();
    List<String> posTags = example.getSentence().getPosTags();
    for (LexiconEntry entry : parse.getSpannedLexiconEntries()) {
      predictedSyntacticCategories.add(entry.getCategory().getSyntax().getSyntax().discardFeaturePassingMarkup());
    }
    List<SyntacticCategory> actualSyntacticCategories = example.getSyntacticParse().getAllSpannedLexiconEntries();
    Preconditions.checkArgument(predictedSyntacticCategories.size() == actualSyntacticCategories.size());
    for (int i = 0; i < predictedSyntacticCategories.size(); i++) {
      SyntacticCategory predicted = predictedSyntacticCategories.get(i);
      SyntacticCategory actual = actualSyntacticCategories.get(i);
      if (predicted.equals(actual)) {
        correctSyntacticCategories++;
      } else {
        StringBuilder sb = new StringBuilder();
        sb.append("Incorrect category: " + i + " " + words.get(i) + " -> " + predicted 
            + " correct: " + actual + " ");
        // Attribute the mistake to either (1) the supertagger, (2) the parser's 
        // internal weights or (3) a missing lexicon entry.
        List<SyntacticCategory> supertags = HeadedSyntacticCategory.convertToCcgbank(sentence.getSupertags().get(i));
        List<SyntacticCategory> possibleLexiconEntries = Lists.newArrayList();
        for (LexiconEntry lexiconEntry : parser.getLexicon().getLexiconEntriesWithUnknown(words.get(i), posTags.get(i))) {
          possibleLexiconEntries.add(lexiconEntry.getCategory().getSyntax().getSyntax().discardFeaturePassingMarkup());
        }
        if (!supertags.contains(actual)) {
          supertaggerErrors++;
          sb.append ("SUPERTAG");
        } else if (!possibleLexiconEntries.contains(actual)) {
          lexiconErrors++;
          sb.append ("LEXICON");
        } else {
          parserErrors++;
          sb.append ("PARSER");
        }
        System.out.println(sb.toString());
      }
    }
    System.out.println("Syntactic Category Accuracy: " + (((double) correctSyntacticCategories) / actualSyntacticCategories.size()));

    return new CcgLoss(labeledTp, labeledFp, labeledFn, unlabeledTp, unlabeledFp, unlabeledFn,
        correctSyntacticCategories, actualSyntacticCategories.size(), supertaggerErrors,
        lexiconErrors, parserErrors, 1, 1);
  }

  private static List<DependencyStructure> getDependenciesCcgbank(CcgParse parse) {
    SyntacticCategory toCat = SyntacticCategory.parseFrom("((S[to]\\NP)/(S[b]\\NP))");
    
    Set<SyntacticCategory> beLightVerbCats = Sets.newHashSet();
    String[] beLightVerbStrings = new String[] {"((S[dcl]\\NP)/(S[ng]\\NP))",                                                                                                        
        "((S[dcl]\\NP)/(S[pt]\\NP))", "((S[dcl]\\NP)/(S[b]\\NP))"};
    for (String lightVerb : beLightVerbStrings) {
      beLightVerbCats.add(SyntacticCategory.parseFrom(lightVerb));
    }

    Set<Integer> beIndexesToCheck = Sets.newHashSet();
    CcgSyntaxTree syntaxTree = parse.getSyntacticParse();
    List<DependencyStructure> filteredDependencies = Lists.newArrayList();
    for (DependencyStructure dep : parse.getAllDependencies()) {
      int headIndex = dep.getHeadWordIndex();
      SyntacticCategory cat = syntaxTree.getLexiconEntryForWordIndex(headIndex);
      if (cat.equals(toCat) && dep.getArgIndex() == 1) {
        continue;
      }
      
      for (SyntacticCategory beLightVerbCat : beLightVerbCats) {
        if (beLightVerbCat.equals(cat) && dep.getArgIndex() == 2) {
          beIndexesToCheck.add(dep.getObjectWordIndex());
        }
      }
      
      filteredDependencies.add(dep);
    }
    
    Set<String> beForms = Sets.newHashSet("be", "being", "been");
    List<DependencyStructure> toReCheck = filteredDependencies;
    filteredDependencies = Lists.newArrayList();
    List<String> words = parse.getSpannedWords();
    for (DependencyStructure dep : toReCheck) {
      int depHeadIndex = dep.getHeadWordIndex();
      if (beIndexesToCheck.contains(depHeadIndex) && beForms.contains(words.get(depHeadIndex).toLowerCase()) && dep.getArgIndex() == 1) {
        continue;
      }

      filteredDependencies.add(dep);
    }
    return filteredDependencies;
  }

  /*
   * Maps dependencies produced by the parser into the dependencies required for
   * evaluation.
   */
  private static List<LabeledDep> dependenciesToLabeledDeps(Collection<DependencyStructure> deps,
      CcgSyntaxTree syntaxTree) {
    List<LabeledDep> labeledDeps = Lists.newArrayList();
    for (DependencyStructure dep : deps) {
      int headIndex = dep.getHeadWordIndex();
      labeledDeps.add(new LabeledDep(dep.getHeadWordIndex(), dep.getObjectWordIndex(),
          syntaxTree.getLexiconEntryForWordIndex(headIndex).discardFeaturePassingMarkup(), dep.getArgIndex()));
    }
    return labeledDeps;
  }

  /*
   * Removes the syntactic category and argument number from labeled dependencies,
   * converting them into unlabeled dependencies.
   */
  private static Set<LabeledDep> stripDependencyLabels(Collection<LabeledDep> dependencies) {
    Set<LabeledDep> deps = Sets.newHashSet();
    for (LabeledDep oldDep : dependencies) {
      deps.add(new LabeledDep(oldDep.getHeadWordIndex(), oldDep.getArgWordIndex(), null, -1));
    }
    return deps;
  }

  public static class CcgLoss {
    private final int labeledTruePositives;
    private final int labeledFalsePositives;
    private final int labeledFalseNegatives;

    private final int unlabeledTruePositives;
    private final int unlabeledFalsePositives;
    private final int unlabeledFalseNegatives;
    
    private final int correctSyntacticCategories;
    private final int totalSyntacticCategories;
    private final int supertaggerErrors;
    private final int lexiconErrors;
    private final int parserErrors;

    private final int numExamplesParsed;
    private final int numExamples;

    public CcgLoss(int labeledTruePositives, int labeledFalsePositives, int labeledFalseNegatives,
        int unlabeledTruePositives, int unlabeledFalsePositives, int unlabeledFalseNegatives,
        int correctSyntacticCategories, int totalSyntacticCategories, int supertaggerErrors,
        int lexiconErrors, int parserErrors, int numExamplesParsed, int numExamples) {
      this.labeledTruePositives = labeledTruePositives;
      this.labeledFalsePositives = labeledFalsePositives;
      this.labeledFalseNegatives = labeledFalseNegatives;

      this.unlabeledTruePositives = unlabeledTruePositives;
      this.unlabeledFalsePositives = unlabeledFalsePositives;
      this.unlabeledFalseNegatives = unlabeledFalseNegatives;
      
      this.correctSyntacticCategories = correctSyntacticCategories;
      this.totalSyntacticCategories = totalSyntacticCategories;
      this.supertaggerErrors = supertaggerErrors;
      this.lexiconErrors = lexiconErrors;
      this.parserErrors = parserErrors;

      this.numExamplesParsed = numExamplesParsed;
      this.numExamples = numExamples;
    }

    /**
     * Gets labeled dependency precision, which is the percentage of
     * predicted labeled dependencies present in the gold standard
     * parse. Labeled dependencies are word-word dependencies with a
     * specified argument slot.
     * 
     * @return
     */
    public double getLabeledDependencyPrecision() {
      return ((double) labeledTruePositives) / (labeledTruePositives + labeledFalsePositives);
    }

    /**
     * Gets labeled dependency recall, which is the percentage of the
     * gold standard labeled dependencies present in the predicted
     * parse. Labeled dependencies are word-word dependencies with a
     * specified argument slot.
     * 
     * @return
     */
    public double getLabeledDependencyRecall() {
      return ((double) labeledTruePositives) / (labeledTruePositives + labeledFalseNegatives);
    }

    public double getLabeledDependencyFScore() {
      double precision = getLabeledDependencyPrecision();
      double recall = getLabeledDependencyRecall();
      return (2 * precision * recall) / (precision + recall);
    }

    /**
     * Gets unlabeled dependency precision, which is the percentage of
     * predicted unlabeled dependencies present in the gold standard
     * parse. Unlabeled dependencies are word-word dependencies,
     * ignoring the precise argument slot.
     * 
     * @return
     */
    public double getUnlabeledDependencyPrecision() {
      return ((double) unlabeledTruePositives) / (unlabeledTruePositives + unlabeledFalsePositives);
    }

    /**
     * Gets unlabeled dependency recall, which is the percentage of
     * the gold standard unlabeled dependencies present in the
     * predicted parse. Unlabeled dependencies are word-word
     * dependencies, ignoring the precise argument slot.
     * 
     * @return
     */
    public double getUnlabeledDependencyRecall() {
      return ((double) unlabeledTruePositives) / (unlabeledTruePositives + unlabeledFalseNegatives);
    }

    public double getUnlabeledDependencyFScore() {
      double precision = getUnlabeledDependencyPrecision();
      double recall = getUnlabeledDependencyRecall();
      return (2 * precision * recall) / (precision + recall);
    }
    
    public double getSyntacticCategoryAccuracy() {
      return ((double) correctSyntacticCategories) / totalSyntacticCategories;
    }

    /**
     * Gets the fraction of examples in the test set for which a CCG
     * parse was produced.
     * 
     * @return
     */
    public double getCoverage() {
      return ((double) numExamplesParsed) / numExamples;
    }

    /**
     * Gets the number of examples in the test set.
     * 
     * @return
     */
    public int getNumExamples() {
      return numExamples;
    }
    
    public CcgLoss add(CcgLoss loss) {
      return new CcgLoss(labeledTruePositives + loss.labeledTruePositives, labeledFalsePositives + loss.labeledFalsePositives,
          labeledFalseNegatives + loss.labeledFalseNegatives, unlabeledTruePositives + loss.unlabeledTruePositives,
          unlabeledFalsePositives + loss.unlabeledFalsePositives, unlabeledFalseNegatives + loss.unlabeledFalseNegatives, 
          correctSyntacticCategories + loss.correctSyntacticCategories, totalSyntacticCategories + loss.totalSyntacticCategories,
          supertaggerErrors + loss.supertaggerErrors, lexiconErrors + loss.lexiconErrors, parserErrors + loss.parserErrors,
          numExamplesParsed + loss.numExamplesParsed, numExamples + loss.numExamples);
    }

    @Override
    public String toString() {
      int totalCategoryErrors = supertaggerErrors + lexiconErrors + parserErrors;
      
      double supertaggerPct = ((double) supertaggerErrors) / totalCategoryErrors;
      double lexiconPct = ((double) lexiconErrors) / totalCategoryErrors;
      double parserPct = ((double) parserErrors) / totalCategoryErrors;
      
      return "Labeled Precision: " + getLabeledDependencyPrecision() + " (" + labeledTruePositives + "/" + (labeledTruePositives + labeledFalsePositives) + ")" 
          + "\nLabeled Recall: " + getLabeledDependencyRecall() + " (" + labeledTruePositives + "/" + (labeledTruePositives + labeledFalseNegatives) + ")"
          + "\nLabeled F Score: " + getLabeledDependencyFScore()
          + "\nUnlabeled Precision: " + getUnlabeledDependencyPrecision() + " (" + unlabeledTruePositives + "/" + (unlabeledTruePositives + unlabeledFalsePositives) + ")" 
          + "\nUnlabeled Recall: " + getUnlabeledDependencyRecall()  + " (" + unlabeledTruePositives + "/" + (unlabeledTruePositives + unlabeledFalseNegatives) + ")"
          + "\nUnlabeled F Score: " + getUnlabeledDependencyFScore()
          + "\nSyntactic Category Accuracy: " + getSyntacticCategoryAccuracy() + " (" + correctSyntacticCategories + "/" + totalSyntacticCategories + ")"
          + "\n  -- Errors from supertagger: " + supertaggerErrors + " (" + supertaggerPct + ")"
          + "\n  -- Errors from missing lexicon entries: " + lexiconErrors + " (" + lexiconPct + ")"
          + "\n  -- Errors from parser internal weights: " + parserErrors  + " (" + parserPct + ")"
          + "\nCoverage: " + getCoverage();
    }
  }

  public static class CcgLossMapper extends Mapper<CcgExample, CcgLoss> {
    private final SupertaggingCcgParser parser;
    private final boolean useCcgbankDerivation;
    private final boolean filterDependenciesCcgbank;
    private final LogFunction log;

    public CcgLossMapper(SupertaggingCcgParser parser, boolean useCcgbankDerivation,
        boolean filterDependenciesCcgbank) {
      this.parser = Preconditions.checkNotNull(parser);
      this.useCcgbankDerivation = useCcgbankDerivation;
      this.filterDependenciesCcgbank = filterDependenciesCcgbank;
      this.log = LogFunctions.getLogFunction();
    }

    @Override
    public CcgLoss map(CcgExample example) {
      CcgParseResult parse = null;
      SyntacticChartCost filter = null;
      if (useCcgbankDerivation) {
        filter = SyntacticChartCost.createAgreementCost(example.getSyntacticParse());
      }
      log.startTimer("parse_sentence");
      parse = parser.parse(example.getSentence(), filter);
      log.stopTimer("parse_sentence");
      System.out.println("SENT: " + example.getSentence().getWords());

      if (parse != null) {
        printCcgParses(Arrays.asList(parse.getParse()), 1, false, false);
        return computeLoss(parse, example, parser.getParser(), filterDependenciesCcgbank);
      } else {
        System.out.println("NO ANALYSIS: " + example.getSentence().getWords());
        
        if (useCcgbankDerivation) {
          // Provide a deeper analysis of why parsing failed.
          CcgChart chart = new CcgExactHashTableChart(example.getSentence(), Integer.MAX_VALUE);
          parser.getParser().parseCommon(chart, example.getSentence(), filter, null, -1);
          CcgParserUtils.analyzeParseFailure(example.getSyntacticParse(), chart,
              parser.getParser().getSyntaxVarType(), "Parse failure", 0);
        }

        return new CcgLoss(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
      }
    }
  }

  public static class CcgLossReducer extends SimpleReducer<CcgLoss> {
    @Override
    public CcgLoss getInitialValue() {
      return new CcgLoss(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public CcgLoss reduce(CcgLoss item, CcgLoss accumulated) {
      return accumulated.add(item);
    }
  }

  private static class LabeledDep {
    private final int headWordIndex;
    private final int argWordIndex;

    private final SyntacticCategory syntax;
    private final int argNum;

    public LabeledDep(int headWordIndex, int argWordIndex, SyntacticCategory syntax, int argNum) {
      this.headWordIndex = headWordIndex;
      this.argWordIndex = argWordIndex;
      this.syntax = syntax;
      this.argNum = argNum;
    }

    public int getHeadWordIndex() {
      return headWordIndex;
    }

    public int getArgWordIndex() {
      return argWordIndex;
    }

    public SyntacticCategory getSyntax() {
      return syntax;
    }

    public int getArgNum() {
      return argNum;
    }
    
    public String toString(List<String> sentenceWords) {
      String headWord = sentenceWords.get(headWordIndex);
      String argWord = sentenceWords.get(argWordIndex);
      return headWord + "," + headWordIndex + "," + syntax + "," + argNum + "," + argWordIndex + "," + argWord;
    }

    @Override
    public String toString() {
      return headWordIndex + "," + syntax + "," + argNum + "," + argWordIndex;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + argNum;
      result = prime * result + argWordIndex;
      result = prime * result + headWordIndex;
      result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      LabeledDep other = (LabeledDep) obj;
      if (argNum != other.argNum)
        return false;
      if (argWordIndex != other.argWordIndex)
        return false;
      if (headWordIndex != other.headWordIndex)
        return false;
      if (syntax == null) {
        if (other.syntax != null)
          return false;
      } else if (!syntax.equals(other.syntax))
        return false;
      return true;
    }
  }
}
