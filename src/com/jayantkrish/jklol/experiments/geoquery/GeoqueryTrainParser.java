package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
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
  }

  @Override
  public void run(OptionSet options) {
    List<String> foldNames = Lists.newArrayList();
    List<List<AlignmentExample>> folds = Lists.newArrayList();
    GeoqueryInduceLexicon.readFolds(options.valueOf(trainingDataFolds), foldNames, folds, options.has(testOpt));
    
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
        lexiconEntryLines = factorLexiconEntries(lexiconEntryLines);
        String factoredLexiconFilename = outputDirString + "/factored_lexicon." + foldName + ".txt";
        IoUtils.writeLines(factoredLexiconFilename, lexiconEntryLines);
      }

      SemanticParserLoss loss = runFold(trainingData, heldOut,
          options.valueOf(parserIterations), options.valueOf(l2Regularization), options.valueOf(beamSize),
          additionalLexiconEntries, lexiconEntryLines, unknownLexiconEntryLines,
          trainingErrorOutputFilename, testErrorOutputFilename,
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
  
  private List<String> factorLexiconEntries(List<String> lexiconEntryLines) {
    List<LexiconEntry> lexiconEntries = LexiconEntry.parseLexiconEntries(lexiconEntryLines);
    List<String> finalEntries = Lists.newArrayList();
    ExpressionSimplifier simplifier = GeoqueryUtil.getExpressionSimplifier();

    Multimap<List<String>, Lexeme> wordLexemeMap = HashMultimap.create();
    Multimap<List<Type>, Template> typeTemplateMap = HashMultimap.create();
    for (LexiconEntry entry : lexiconEntries) {
      Expression2 lf = entry.getCategory().getLogicalForm();

      if (lf.isConstant()) {
        // Skip entities.
        finalEntries.add(entry.toCsvString());
        continue;
      }
      
      Set<String> freeVars = StaticAnalysis.getFreeVariables(lf);
      freeVars.remove("and:<t*,t>");
      freeVars.remove("exists:<<e,t>,t>");
      
      SortedMap<Integer, String> locVarMap = Maps.newTreeMap();
      for (String freeVar : freeVars) {
        int[] indexes = StaticAnalysis.getIndexesOfFreeVariable(lf, freeVar);
        for (int i = 0; i < indexes.length; i++) {
          locVarMap.put(indexes[i], freeVar);
        }
      }
      
      List<Integer> locs = Lists.newArrayList(locVarMap.keySet());
      List<String> items = Lists.newArrayList(locVarMap.values());
      List<String> newVarNames = StaticAnalysis.getNewVariableNames(lf, locVarMap.size());
      Expression2 lfTemplateBody = lf;
      for (int i = 0; i < locs.size(); i++) {
        lfTemplateBody = lfTemplateBody.substitute(locs.get(i), newVarNames.get(i));
      }
      
      List<Expression2> lfTemplateElts = Lists.newArrayList();
      lfTemplateElts.add(Expression2.constant(StaticAnalysis.LAMBDA));
      lfTemplateElts.addAll(Expression2.constants(newVarNames));
      lfTemplateElts.add(lfTemplateBody);
      Expression2 lfTemplate = simplifier.apply(Expression2.nested(lfTemplateElts));
      
      // System.out.println(entry.getWords() + " " + lf);
      // System.out.println("  " + items);
      // System.out.println("  " + lfTemplate);
      
      Lexeme lexeme = new Lexeme(items);
      List<Type> typeSignature = lexeme.getTypeSignature(GeoqueryInduceLexicon.typeReplacements);
      Template template = new Template(entry.getCategory().getSyntax(), typeSignature, lfTemplate);
      wordLexemeMap.put(entry.getWords(), lexeme);
      typeTemplateMap.put(typeSignature, template);
    }
    
    for (List<String> words : wordLexemeMap.keySet()) {
      for (Lexeme lexeme : wordLexemeMap.get(words)) {
        // System.out.println(words + " " + lexeme.getPredicates());
        List<Type> typeSig = lexeme.getTypeSignature(GeoqueryInduceLexicon.typeReplacements);
        for (Template template : typeTemplateMap.get(typeSig)) {
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
      int parserIterations, double l2Regularization, int beamSize,
      List<String> additionalLexiconEntries, List<String> lexiconEntryLines, List<String> unknownLexiconEntryLines,
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
        GeoqueryUtil.FEATURE_ANNOTATION_NAME, featureGen.getFeatureDictionary(),
        LexiconEntry.parseLexiconEntries(additionalLexiconEntries));

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    CcgBeamSearchInference inferenceAlgorithm = new CcgBeamSearchInference(null, comparator, beamSize,
        -1, Integer.MAX_VALUE, 1, false);

    CcgParser ccgParser = GeoqueryInduceLexicon.trainSemanticParser(ccgTrainingExamples, lexiconEntryLines,
        unknownLexiconEntryLines, ruleEntries, featureFactory, inferenceAlgorithm, comparator,
        parserIterations, l2Regularization);

    IoUtils.serializeObjectToFile(ccgParser, parserModelOutputFilename);
    
    List<SemanticParserExampleLoss> trainingExampleLosses = Lists.newArrayList();    
    SemanticParserUtils.testSemanticParser(ccgTrainingExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, trainingExampleLosses);
    SemanticParserExampleLoss.writeJsonToFile(trainingErrorOutputFilename, trainingExampleLosses);

    List<CcgExample> ccgTestExamples = GeoqueryInduceLexicon.alignmentExamplesToCcgExamples(testData);
    ccgTestExamples = SemanticParserUtils.annotateFeatures(ccgTestExamples, featureGen, GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    List<SemanticParserExampleLoss> testExampleLosses = Lists.newArrayList();    
    SemanticParserLoss testLoss = SemanticParserUtils.testSemanticParser(ccgTestExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, testExampleLosses);
    SemanticParserExampleLoss.writeJsonToFile(testErrorOutputFilename, testExampleLosses);

    return testLoss;
  }
  
  public static void main(String[] args) {
    new GeoqueryTrainParser().run(args);
  }
  
  private static class Lexeme {
    private final List<String> predicates;
    
    public Lexeme(List<String> predicates) {
      this.predicates = ImmutableList.copyOf(predicates);
    }
    
    public List<String> getPredicates() {
      return predicates;
    }
    
    public List<Type> getTypeSignature(Map<String, String> typeReplacements) {
      List<Type> typeSig = Lists.newArrayList();
      for (String predicate : predicates) {
        typeSig.add(StaticAnalysis.inferType(Expression2.constant(predicate), typeReplacements));
      }
      return typeSig;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((predicates == null) ? 0 : predicates.hashCode());
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
      Lexeme other = (Lexeme) obj;
      if (predicates == null) {
        if (other.predicates != null)
          return false;
      } else if (!predicates.equals(other.predicates))
        return false;
      return true;
    }
  }
  
  private static class Template {
    private final HeadedSyntacticCategory syntax;

    private final List<Type> typeSignature;
    private final Expression2 lfTemplate;

    public Template(HeadedSyntacticCategory syntax, List<Type> typeSignature,
        Expression2 lfTemplate) {
      this.syntax = Preconditions.checkNotNull(syntax);
      this.typeSignature = ImmutableList.copyOf(typeSignature);
      this.lfTemplate = Preconditions.checkNotNull(lfTemplate);
    }
    
    public HeadedSyntacticCategory getSyntax() {
      return syntax;
    }

    public List<Type> getTypeSignature() {
      return typeSignature;
    }

    public Expression2 getLfTemplate() {
      return lfTemplate;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((lfTemplate == null) ? 0 : lfTemplate.hashCode());
      result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
      result = prime * result + ((typeSignature == null) ? 0 : typeSignature.hashCode());
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
      Template other = (Template) obj;
      if (lfTemplate == null) {
        if (other.lfTemplate != null)
          return false;
      } else if (!lfTemplate.equals(other.lfTemplate))
        return false;
      if (syntax == null) {
        if (other.syntax != null)
          return false;
      } else if (!syntax.equals(other.syntax))
        return false;
      if (typeSignature == null) {
        if (other.typeSignature != null)
          return false;
      } else if (!typeSignature.equals(other.typeSignature))
        return false;
      return true;
    }
  }
}
