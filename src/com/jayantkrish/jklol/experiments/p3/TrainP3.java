package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.gi.GroundedParser;
import com.jayantkrish.jklol.ccg.gi.GroundedParserInference;
import com.jayantkrish.jklol.ccg.gi.GroundedParserLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.gi.GroundedParserPipelinedInference;
import com.jayantkrish.jklol.ccg.gi.ParametricGroundedParser;
import com.jayantkrish.jklol.ccg.gi.ValueGroundedParseExample;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.experiments.p3.KbParametricContinuationIncEval.KbContinuationIncEval;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbEval.WrappedBuiltinFunction;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainP3 extends AbstractCli {

  private OptionSpec<String> trainingData;
  private OptionSpec<String> environment;
  private OptionSpec<String> defs;
  private OptionSpec<String> genDefs;
  
  private OptionSpec<String> categoryFeatures;
  private OptionSpec<String> relationFeatures;
  
  private OptionSpec<String> lexicon;
  
  private OptionSpec<String> parserOut;
  private OptionSpec<String> kbModelOut;
  
  public TrainP3() {
    super(CommonOptions.MAP_REDUCE, CommonOptions.STOCHASTIC_GRADIENT);
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    trainingData = parser.accepts("trainingData").withRequiredArg().withValuesSeparatedBy(',')
        .ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class);
    defs = parser.accepts("defs").withRequiredArg().ofType(String.class);
    genDefs = parser.accepts("gendefs").withRequiredArg().ofType(String.class);
    
    categoryFeatures = parser.accepts("categoryFeatures").withRequiredArg()
        .ofType(String.class).required();
    relationFeatures = parser.accepts("relationFeatures").withRequiredArg()
        .ofType(String.class).required();
    
    lexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();
    
    parserOut = parser.accepts("parserOut").withRequiredArg().ofType(String.class).required();
    kbModelOut = parser.accepts("kbModelOut").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    DiscreteVariable categoryFeatureNames = new DiscreteVariable("categoryFeatures",
        IoUtils.readLines(options.valueOf(categoryFeatures)));
    DiscreteVariable relationFeatureNames = new DiscreteVariable("relationFeatures",
        IoUtils.readLines(options.valueOf(relationFeatures)));
    
    List<ValueGroundedParseExample> examples = Lists.newArrayList();
    for (String trainingDataEnv : options.valuesOf(trainingData)) {
      examples.addAll(P3Utils.readTrainingData(trainingDataEnv, categoryFeatureNames,
          relationFeatureNames));
    }
    
    Collections.shuffle(examples);
    
    List<String> lexiconLines = IoUtils.readLines(options.valueOf(lexicon));
    ParametricCcgParser ccgFamily  = getCcgParser(lexiconLines);
    ParametricIncEval evalFamily = getEval(lexiconLines, options.valueOf(environment),
        options.valueOf(defs), options.valueOf(genDefs), categoryFeatureNames,
        relationFeatureNames);
    ParametricGroundedParser family = new ParametricGroundedParser(ccgFamily, evalFamily);

    GroundedParserInference inf = new GroundedParserPipelinedInference(
        CcgCkyInference.getDefault(100), getSimplifier(), 10, 100, false);
    GroundedParserLoglikelihoodOracle oracle = new GroundedParserLoglikelihoodOracle(family, inf);
    GradientOptimizer trainer = createGradientOptimizer(examples.size());
    
    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    GroundedParser parser = family.getModelFromParameters(parameters);
    IoUtils.serializeObjectToFile(parser.getCcgParser(), options.valueOf(parserOut));
    IoUtils.serializeObjectToFile(((KbContinuationIncEval) parser.getEval()).getKbModel(),
        options.valueOf(kbModelOut));
    
    // System.out.println(family.getParameterDescription(parameters));
  }
  
  private static ParametricCcgParser getCcgParser(List<String> lexiconLines) {
    List<String> unkLexiconLines = Collections.emptyList();
    List<String> ruleLines = Lists.newArrayList("FOO{0} BAR{0}");
    return ParametricCcgParser.parseFromLexicon(lexiconLines, unkLexiconLines,
        ruleLines, new DefaultCcgFeatureFactory(false, true), null, false, null, true);
    
  }
  
  private static ParametricIncEval getEval(List<String> lexiconLines,
      String envFilename, String defFilename, String generatedDefFilename,
      DiscreteVariable categoryFeatureNames, DiscreteVariable relationFeatureNames) {
    // Build an SExpression defining all category and relation
    // predicates found in the lexicon.
    Collection<LexiconEntry> lexicon = LexiconEntry.parseLexiconEntries(lexiconLines);
    TypeDeclaration typeDeclaration = new ExplicitTypeDeclaration(Collections.emptyMap());
    Type catType = Type.parseFrom("<e,t>");
    Type relType = Type.parseFrom("<e,<e,t>>");
    
    Set<String> cats = Sets.newHashSet();
    Set<String> rels = Sets.newHashSet();
    for (LexiconEntry l : lexicon) {
      Expression2 lf = l.getCategory().getLogicalForm();
      Set<String> freeVars = StaticAnalysis.getFreeVariables(lf);
      
      for (String freeVar : freeVars) {
        Type varType = typeDeclaration.getType(freeVar);
        if (varType.equals(catType)) {
          cats.add(freeVar);
        } else if (varType.equals(relType)) {
          rels.add(freeVar);
        }
      }
    }
    
    List<String> generatedDefs = Lists.newArrayList();
    for (String cat : cats) {
      generatedDefs.add("(define " + cat + " (make-category \"" + cat + "\"))\n");
    }
    for (String rel : rels) {
      generatedDefs.add("(define " + rel + " (make-relation \"" + rel + "\"))\n");
    }
    
    IoUtils.writeLines(generatedDefFilename, generatedDefs);
    
    IndexedList<String> symbolTable = AmbEval.getInitialSymbolTable(); 
    AmbEval ambEval = new AmbEval(symbolTable);
    Environment env = AmbEval.getDefaultEnvironment(symbolTable);
    env.bindName("get-entities", new WrappedBuiltinFunction(new P3Functions.GetEntities()), symbolTable);
    env.bindName("kb-get", new WrappedBuiltinFunction(new P3Functions.KbGet()), symbolTable);
    env.bindName("kb-set", new WrappedBuiltinFunction(new P3Functions.KbSet()), symbolTable);
    env.bindName("list-to-set", new WrappedBuiltinFunction(new P3Functions.ListToSet()), symbolTable);
    SExpression envProg = LispUtil.readProgram(Arrays.asList(envFilename), symbolTable);
    ambEval.eval(envProg, env, null);
    
    ExpressionSimplifier simplifier = getSimplifier();
    
    SExpression defs = LispUtil.readProgram(Arrays.asList(defFilename, generatedDefFilename),
        symbolTable);

    Expression2 lfConversion = ExpressionParser.expression2().parse("(lambda (x) (list-to-set-c (get-denotation x)))");
    ContinuationIncEval incEval = new ContinuationIncEval(ambEval, env, simplifier, defs, lfConversion);
    
    // Set up the per-predicate classifiers
    ObjectVariable tensorVar = new ObjectVariable(Tensor.class);
    DiscreteVariable outputVar = new DiscreteVariable("true", Arrays.asList(true));
    VariableNumMap input = VariableNumMap.singleton(0, "input", tensorVar);
    VariableNumMap output = VariableNumMap.singleton(1, "output", outputVar);
    Assignment labelAssignment = output.outcomeArrayToAssignment(true);
    ParametricLinearClassifierFactor categoryFamily = new ParametricLinearClassifierFactor(
        input, output, VariableNumMap.EMPTY, categoryFeatureNames, null, false);
    ParametricLinearClassifierFactor relationFamily = new ParametricLinearClassifierFactor(
        input, output, VariableNumMap.EMPTY, relationFeatureNames, null, false);
    
    IndexedList<String> predicateNames = IndexedList.create();
    List<ParametricLinearClassifierFactor> families = Lists.newArrayList();
    List<VariableNumMap> featureVars = Lists.newArrayList();
    List<Assignment> labelAssignments = Lists.newArrayList();
    for (String cat : cats) {
      predicateNames.add(cat);
      families.add(categoryFamily);
      featureVars.add(input);
      labelAssignments.add(labelAssignment);
    }    
    for (String rel : rels) {
      predicateNames.add(rel);
      families.add(relationFamily);
      featureVars.add(input);
      labelAssignments.add(labelAssignment);
    }

    KbFeatureGenerator featureGen = new KbFeatureGenerator();
    
    return new KbParametricContinuationIncEval(predicateNames, families, featureVars,
        labelAssignments, featureGen, incEval);
  }
  
  private static ExpressionSimplifier getSimplifier() {
    return new ExpressionSimplifier(Arrays.<ExpressionReplacementRule>asList(
        new LambdaApplicationReplacementRule(),
        new VariableCanonicalizationReplacementRule(),
        new CommutativeReplacementRule("and:<t*,t>")));
  }
  

  public static void main(String[] args) {
    new TrainP3().run(args);
  }
}
