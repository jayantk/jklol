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
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.SupertaggingCcgParser;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartFilter;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.supertag.Supertagger;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.Mapper;
import com.jayantkrish.jklol.parallel.Reducer.SimpleReducer;
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
      inferenceAlgorithm = new CcgExactInference(null, options.valueOf(maxParseTimeMillis));
    } else {
      inferenceAlgorithm = new CcgBeamSearchInference(null, options.valueOf(beamSize), options.valueOf(maxParseTimeMillis));
    }
    
    if (options.has(testFile)) {
      // Parse all test examples.
      List<CcgExample> testExamples = TrainCcg.readTrainingData(options.valueOf(testFile),
          false, options.has(useCcgBankFormat), options.valueOf(syntaxMap));
      System.out.println(testExamples.size() + " test examples after filtering.");

      Supertagger tagger = null;
      double[] tagThresholds = new double[0];
      if (options.has(supertagger)) {
        Preconditions.checkState(options.has(multitagThresholds));
        tagger = IoUtils.readSerializedObject(options.valueOf(supertagger), Supertagger.class);
        tagThresholds = Doubles.toArray(options.valuesOf(multitagThresholds));
      }

      SupertaggingCcgParser supertaggingParser = new SupertaggingCcgParser(ccgParser,
          inferenceAlgorithm, tagger, tagThresholds);
      CcgLoss loss = runTestSetEvaluation(testExamples, supertaggingParser, options.has(useGoldSyntacticTrees),
          options.has(filterDependenciesCcgbank));
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

      List<CcgParse> parses = ccgParser.beamSearch(sentenceToParse, posTags, options.valueOf(beamSize));
      printCcgParses(parses, options.valueOf(numParses), options.has(atomic), options.has(printLf));
    }
    System.exit(0);
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
  
  public static CcgLoss computeLoss(CcgParse parse, CcgExample example, boolean filterDependenciesCcgbank) {
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

    System.out.println("Predicted: ");
    for (LabeledDep dep : predictedDeps) {
      if (trueDeps.contains(dep)) {
        System.out.println(dep);
      } else {
        System.out.println(dep + "\tINCORRECT");
      }
    }

    System.out.println("Missing true dependencies:");
    for (LabeledDep dep : trueDeps) {
      if (!predictedDeps.contains(dep)) {
        System.out.println(dep);
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

    return new CcgLoss(labeledTp, labeledFp, labeledFn, unlabeledTp, unlabeledFp, unlabeledFn,
        1, 1);
  }
  
  private static List<DependencyStructure> getDependenciesCcgbank(CcgParse parse) {
    SyntacticCategory toCat = SyntacticCategory.parseFrom("((S[to]\\NP)/(S[b]\\NP))");
    CcgSyntaxTree syntaxTree = parse.getSyntacticParse();
    List<DependencyStructure> filteredDependencies = Lists.newArrayList();
    for (DependencyStructure dep : parse.getAllDependencies()) {
      int headIndex = dep.getHeadWordIndex();
      SyntacticCategory cat = syntaxTree.getLexiconEntryForWordIndex(headIndex);
      if (cat.equals(toCat) && dep.getArgIndex() == 1) {
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
          syntaxTree.getLexiconEntryForWordIndex(headIndex).getWithoutFeatures(), dep.getArgIndex()));
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

    private final int numExamplesParsed;
    private final int numExamples;

    public CcgLoss(int labeledTruePositives, int labeledFalsePositives, int labeledFalseNegatives,
        int unlabeledTruePositives, int unlabeledFalsePositives, int unlabeledFalseNegatives,
        int numExamplesParsed, int numExamples) {
      this.labeledTruePositives = labeledTruePositives;
      this.labeledFalsePositives = labeledFalsePositives;
      this.labeledFalseNegatives = labeledFalseNegatives;

      this.unlabeledTruePositives = unlabeledTruePositives;
      this.unlabeledFalsePositives = unlabeledFalsePositives;
      this.unlabeledFalseNegatives = unlabeledFalseNegatives;

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
          numExamplesParsed + loss.numExamplesParsed, numExamples + loss.numExamples);
    }

    @Override
    public String toString() {
      return "Labeled Precision: " + getLabeledDependencyPrecision() + "\nLabeled Recall: "
          + getLabeledDependencyRecall() + "\nLabeled F Score: " + getLabeledDependencyFScore()
          + "\nUnlabeled Precision: " + getUnlabeledDependencyPrecision() + "\nUnlabeled Recall: "
          + getUnlabeledDependencyRecall() + "\nUnlabeled F Score: " + getUnlabeledDependencyFScore()
          + "\nCoverage: " + getCoverage();
    }
  }
  
  public static class CcgLossMapper extends Mapper<CcgExample, CcgLoss> {
    
    private final SupertaggingCcgParser parser;
    private final boolean useCcgbankDerivation;
    private final boolean filterDependenciesCcgbank;

    public CcgLossMapper(SupertaggingCcgParser parser, boolean useCcgbankDerivation,
        boolean filterDependenciesCcgbank) {
      this.parser = Preconditions.checkNotNull(parser);
      this.useCcgbankDerivation = useCcgbankDerivation;
      this.filterDependenciesCcgbank = filterDependenciesCcgbank;
    }

    @Override
    public CcgLoss map(CcgExample example) {
      CcgParse parse = null;
      if (useCcgbankDerivation) {
        SyntacticChartFilter filter = new SyntacticChartFilter(example.getSyntacticParse());
        parse = parser.parse(example.getWords(), example.getPosTags(), filter);
      } else {
        parse = parser.parse(example.getWords(), example.getPosTags());
      }
      System.out.println("SENT: " + example.getWords());

      if (parse != null) {
        printCcgParses(Arrays.asList(parse), 1, false, false);
        return computeLoss(parse, example, filterDependenciesCcgbank);
      } else {
        System.out.println("NO ANALYSIS: " + example.getWords());
        return new CcgLoss(0, 0, 0, 0, 0, 0, 0, 1);
      }
    }
  }
  
  public static class CcgLossReducer extends SimpleReducer<CcgLoss> {
    @Override
    public CcgLoss getInitialValue() {
      return new CcgLoss(0, 0, 0, 0, 0, 0, 0, 0);
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
