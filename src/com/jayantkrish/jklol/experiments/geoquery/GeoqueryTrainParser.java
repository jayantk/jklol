package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.util.SemanticParserExampleLoss;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils.SemanticParserLoss;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.Pair;

public class GeoqueryTrainParser extends AbstractCli {
  
  private OptionSpec<String> trainingDataFolds;
  private OptionSpec<String> outputDir;
  
  private OptionSpec<String> foldNameOpt;
  private OptionSpec<Void> testOpt;

  // Configuration for the semantic parser.
  private OptionSpec<Integer> parserIterations;
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Double> l2Regularization;
  private OptionSpec<String> additionalLexicon;
  private OptionSpec<Void> factorLexiconEntries;
  private OptionSpec<Void> lexemeFeatures;
  private OptionSpec<Void> templateFeatures;
  
  public GeoqueryTrainParser() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingDataFolds = parser.accepts("trainingDataFolds").withRequiredArg().ofType(String.class).required();
    outputDir = parser.accepts("outputDir").withRequiredArg().ofType(String.class).required();
    
    // Optional option to only run one fold
    foldNameOpt = parser.accepts("foldName").withRequiredArg().ofType(String.class);
    testOpt = parser.accepts("test");
    
    parserIterations = parser.accepts("parserIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    l2Regularization = parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
    additionalLexicon = parser.accepts("additionalLexicon").withRequiredArg().ofType(String.class);
    factorLexiconEntries = parser.accepts("factorLexiconEntries");
    lexemeFeatures = parser.accepts("lexemeFeatures");
    templateFeatures = parser.accepts("templateFeatures");
  }

  @Override
  public void run(OptionSet options) {
    TypeDeclaration typeDeclaration = GeoqueryUtil.getTypeDeclaration();
    
    List<String> foldNames = Lists.newArrayList();
    List<List<AlignmentExample>> folds = Lists.newArrayList();
    GeoqueryInduceLexicon.readFolds(options.valueOf(trainingDataFolds), foldNames, folds,
        options.has(testOpt), typeDeclaration);
    
    List<String> additionalLexiconEntries = IoUtils.readLines(options.valueOf(additionalLexicon));

    List<String> foldsToRun = Lists.newArrayList();
    if (options.has(foldNameOpt)) {
      foldsToRun.add(options.valueOf(foldNameOpt));
    } else {
      foldsToRun.addAll(foldNames);
    }

    List<SemanticParserLoss> losses = Lists.newArrayList();
    for (String foldName : foldsToRun) {
      int foldIndex = foldNames.indexOf(foldName);

      List<AlignmentExample> heldOut = folds.get(foldIndex);
      List<AlignmentExample> trainingData = Lists.newArrayList();
      for (int j = 0; j < folds.size(); j++) {
        if (j == foldIndex) {
          continue;
        }
        trainingData.addAll(folds.get(j));
      }

      String outputDirString = options.valueOf(outputDir);
      String lexiconInputFilename = outputDirString + "/lexicon." + foldName + ".txt";
      String parserModelOutputFilename = outputDirString + "/parser." + foldName + ".ser";

      String trainingErrorOutputFilename = outputDirString + "/training_error." + foldName + ".json";
      String testErrorOutputFilename = outputDirString + "/test_error." + foldName + ".json";
      
      List<String> lexiconEntryLines = IoUtils.readLines(lexiconInputFilename);
      List<String> unknownLexiconEntryLines = Lists.newArrayList();

      if (options.has(factorLexiconEntries)) {
        lexiconEntryLines = factorLexiconEntries(lexiconEntryLines, typeDeclaration);
        String factoredLexiconFilename = outputDirString + "/factored_lexicon." + foldName + ".txt";
        IoUtils.writeLines(factoredLexiconFilename, lexiconEntryLines);
      }

      SemanticParserLoss loss = runFold(trainingData, heldOut,
          options.valueOf(parserIterations), options.valueOf(l2Regularization), options.valueOf(beamSize),
          options.has(lexemeFeatures), options.has(templateFeatures), additionalLexiconEntries,
          lexiconEntryLines, unknownLexiconEntryLines, trainingErrorOutputFilename, testErrorOutputFilename,
          parserModelOutputFilename);
      losses.add(loss);
    }
    
    SemanticParserLoss overall = new SemanticParserLoss(0, 0, 0, 0);
    for (int i = 0; i < losses.size(); i++) {
      System.out.println(foldNames.get(i));
      System.out.println("PRECISION: " + losses.get(i).getPrecision());
      System.out.println("RECALL: " + losses.get(i).getRecall());
      System.out.println("LEXICON RECALL: " + losses.get(i).getLexiconRecall());
      overall = overall.add(losses.get(i));
    }
    
    System.out.println("== Overall ==");
    System.out.println("PRECISION: " + overall.getPrecision());
    System.out.println("RECALL: " + overall.getRecall());
    System.out.println("LEXICON RECALL: " + overall.getLexiconRecall());
  }
  
  private List<String> factorLexiconEntries(List<String> lexiconEntryLines, TypeDeclaration typeDeclaration) {
    List<LexiconEntry> lexiconEntries = LexiconEntry.parseLexiconEntries(lexiconEntryLines);
    List<String> finalEntries = Lists.newArrayList();
    ExpressionSimplifier simplifier = GeoqueryUtil.getExpressionSimplifier();

    Multimap<List<String>, Lexeme> wordLexemeMap = HashMultimap.create();
    Multimap<List<Type>, LexiconEntryTemplate> typeTemplateMap = HashMultimap.create();
    for (LexiconEntry entry : lexiconEntries) {
      Expression2 lf = entry.getCategory().getLogicalForm();

      if (lf.isConstant()) {
        // Skip entities.
        finalEntries.add(entry.toCsvString());
        continue;
      }

      Pair<Lexeme, LexiconEntryTemplate> factoredEntry = GeoqueryUtil.factorLexiconEntry(entry, simplifier);
      wordLexemeMap.put(entry.getWords(), factoredEntry.getLeft());
      LexiconEntryTemplate template = factoredEntry.getRight();
      typeTemplateMap.put(template.getTypeSignature(), template);
    }

    for (List<String> words : wordLexemeMap.keySet()) {
      for (Lexeme lexeme : wordLexemeMap.get(words)) {
        // System.out.println(words + " " + lexeme.getPredicates());
        List<Type> typeSig = lexeme.getTypeSignature(typeDeclaration);
        for (LexiconEntryTemplate template : typeTemplateMap.get(typeSig)) {
          List<Expression2> app = Lists.newArrayList();
          app.add(template.getLfTemplate());
          app.addAll(Expression2.constants(lexeme.getPredicates()));
          Expression2 populatedLf = simplifier.apply(Expression2.nested(app));
          // System.out.println("  " + template.getSyntax() + " " + populatedLf + " " + template.getLfTemplate());

          // Create a dependency for each argument of the syntactic category.
          String head = populatedLf.toString().replaceAll(" ", "_");
          HeadedSyntacticCategory syntax = template.getSyntax();
          List<String> subjects = Lists.newArrayList();
          List<Integer> argumentNums = Lists.newArrayList();
          List<Integer> objects = Lists.newArrayList();
          List<Set<String>> assignments = Lists.newArrayList();
          assignments.add(Sets.newHashSet(head));
          List<HeadedSyntacticCategory> argumentCats = Lists.newArrayList(syntax.getArgumentTypes());
          Collections.reverse(argumentCats);
          for (int i = 0; i < argumentCats.size(); i++) {
            subjects.add(head);
            argumentNums.add(i + 1);
            objects.add(argumentCats.get(i).getHeadVariable());
          }

          for (int i = 0; i < syntax.getUniqueVariables().length - 1; i++) {
            assignments.add(Collections.<String>emptySet());
          }

          CcgCategory ccgCategory = new CcgCategory(syntax, populatedLf, subjects,
              argumentNums, objects, assignments);
          LexiconEntry entry = new LexiconEntry(words, ccgCategory);
          
          finalEntries.add(entry.toCsvString());
        }
      }
    }

    return finalEntries;
  }
  
  private SemanticParserLoss runFold(List<AlignmentExample> trainingData, List<AlignmentExample> testData,
      int parserIterations, double l2Regularization, int beamSize, boolean useLexemeFeatures,
      boolean useTemplateFeatures, List<String> additionalLexiconEntries,
      List<String> lexiconEntryLines, List<String> unknownLexiconEntryLines,
      String trainingErrorOutputFilename, String testErrorOutputFilename,
      String parserModelOutputFilename) {
    // Initialize CCG parser components.
    List<CcgExample> ccgTrainingExamples = GeoqueryInduceLexicon.alignmentExamplesToCcgExamples(trainingData);
    List<String> ruleEntries = Arrays.asList("\"DUMMY{0} DUMMY{0}\",\"(lambda $L $L)\"");

    // Generate a dictionary of string context features.
    List<StringContext> contexts = StringContext.getContextsFromExamples(ccgTrainingExamples);
    FeatureVectorGenerator<StringContext> featureGen = DictionaryFeatureVectorGenerator
        .createFromData(contexts, new GeoqueryFeatureGenerator(), true);

    ccgTrainingExamples = SemanticParserUtils.annotateFeatures(ccgTrainingExamples, featureGen,
        GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    CcgFeatureFactory featureFactory = new GeoqueryFeatureFactory(true, true,
        useLexemeFeatures, useTemplateFeatures, GeoqueryUtil.FEATURE_ANNOTATION_NAME,
        featureGen.getFeatureDictionary(),
        LexiconEntry.parseLexiconEntries(additionalLexiconEntries));

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    CcgCkyInference inferenceAlgorithm = CcgCkyInference.getDefault(beamSize);

    CcgParser ccgParser = GeoqueryInduceLexicon.trainSemanticParser(ccgTrainingExamples, lexiconEntryLines,
        unknownLexiconEntryLines, ruleEntries, featureFactory, inferenceAlgorithm, comparator,
        parserIterations, l2Regularization);

    IoUtils.serializeObjectToFile(ccgParser, parserModelOutputFilename);
    
    List<SemanticParserExampleLoss> trainingExampleLosses = Lists.newArrayList();    
    SemanticParserUtils.testSemanticParser(ccgTrainingExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, trainingExampleLosses, true);
    SemanticParserExampleLoss.writeJsonToFile(trainingErrorOutputFilename, trainingExampleLosses);

    List<CcgExample> ccgTestExamples = GeoqueryInduceLexicon.alignmentExamplesToCcgExamples(testData);
    ccgTestExamples = SemanticParserUtils.annotateFeatures(ccgTestExamples, featureGen, GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    List<SemanticParserExampleLoss> testExampleLosses = Lists.newArrayList();    
    SemanticParserLoss testLoss = SemanticParserUtils.testSemanticParser(ccgTestExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, testExampleLosses, true);
    SemanticParserExampleLoss.writeJsonToFile(testErrorOutputFilename, testExampleLosses);

    return testLoss;
  }
  
  public static void main(String[] args) {
    new GeoqueryTrainParser().run(args);
  }
}
