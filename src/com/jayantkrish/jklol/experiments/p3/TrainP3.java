package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.experiments.p3.KbParametricContinuationIncEval.KbContinuationIncEval;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.p3.FunctionAssignment;
import com.jayantkrish.jklol.p3.P3BeamInference;
import com.jayantkrish.jklol.p3.P3Inference;
import com.jayantkrish.jklol.p3.P3LoglikelihoodOracle;
import com.jayantkrish.jklol.p3.P3Model;
import com.jayantkrish.jklol.p3.ParametricP3Model;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.Pseudorandom;

public class TrainP3 extends AbstractCli {

  private OptionSpec<String> trainingData;
  private OptionSpec<String> categoryFilename;
  private OptionSpec<String> relationFilename;
  private OptionSpec<String> exampleFilename;
  private OptionSpec<String> worldFilename;
  private OptionSpec<String> defs;
  
  private OptionSpec<String> categories;
  private OptionSpec<String> relations;
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
    categoryFilename = parser.accepts("categoryFilename").withRequiredArg()
        .ofType(String.class).required();
    relationFilename = parser.accepts("relationFilename").withRequiredArg()
        .ofType(String.class).required();
    exampleFilename = parser.accepts("exampleFilename").withRequiredArg()
        .ofType(String.class).required();
    worldFilename = parser.accepts("worldFilename").withRequiredArg()
        .ofType(String.class);
    defs = parser.accepts("defs").withRequiredArg().withValuesSeparatedBy(',')
        .ofType(String.class);
    
    categories = parser.accepts("categories").withRequiredArg()
        .ofType(String.class).required();
    relations = parser.accepts("relations").withRequiredArg()
        .ofType(String.class).required();
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
    IndexedList<String> categoryList = IndexedList.create(IoUtils.readLines(options.valueOf(categories)));
    IndexedList<String> relationList = IndexedList.create(IoUtils.readLines(options.valueOf(relations)));
    DiscreteVariable categoryFeatureNames = new DiscreteVariable("categoryFeatures",
        IoUtils.readLines(options.valueOf(categoryFeatures)));
    DiscreteVariable relationFeatureNames = new DiscreteVariable("relationFeatures",
        IoUtils.readLines(options.valueOf(relationFeatures)));
    
    DiscreteVariable lispTruthVar = new DiscreteVariable("lispTruthVar",
        Arrays.asList(ConstantValue.FALSE, ConstantValue.TRUE, ConstantValue.NIL));
    FeatureVectorGenerator<FunctionAssignment> catPredicateFeatureGen = new PredicateSizeFeatureVectorGenerator(
        4, lispTruthVar.getValueIndex(ConstantValue.TRUE));
    FeatureVectorGenerator<FunctionAssignment> relPredicateFeatureGen = new PredicateSizeFeatureVectorGenerator(
        4, lispTruthVar.getValueIndex(ConstantValue.TRUE));
    
    List<P3KbExample> examples = Lists.newArrayList();
    for (String trainingDataEnv : options.valuesOf(trainingData)) {
      examples.addAll(P3Utils.readTrainingData(trainingDataEnv, categoryFeatureNames,
          relationFeatureNames, catPredicateFeatureGen, relPredicateFeatureGen, lispTruthVar,
          options.valueOf(categoryFilename), options.valueOf(relationFilename),
          options.valueOf(exampleFilename), options.valueOf(worldFilename), categoryList, relationList));
    }

    Collections.shuffle(examples, Pseudorandom.get());
    
    List<String> lexiconLines = IoUtils.readLines(options.valueOf(lexicon));
    ParametricCcgParser ccgFamily  = getCcgParser(lexiconLines);
    ParametricIncEval evalFamily = getEval(lexiconLines, options.valuesOf(defs),
        categoryFeatureNames, relationFeatureNames, catPredicateFeatureGen.getFeatureDictionary(),
        relPredicateFeatureGen.getFeatureDictionary(), categoryList.items(), relationList.items());
    ParametricP3Model family = new ParametricP3Model(ccgFamily, evalFamily);

    P3Inference inf = new P3BeamInference(
        CcgCkyInference.getDefault(100), P3Utils.getSimplifier(), 10, 100, false);
    P3LoglikelihoodOracle oracle = new P3LoglikelihoodOracle(family, inf);
    GradientOptimizer trainer = createGradientOptimizer(examples.size());
    
    SufficientStatistics initialParameters = oracle.initializeGradient();
    SufficientStatistics parameters = trainer.train(oracle, initialParameters, examples);
    P3Model parser = family.getModelFromParameters(parameters);
    IoUtils.serializeObjectToFile(parser.getCcgParser(), options.valueOf(parserOut));
    IoUtils.serializeObjectToFile(((KbContinuationIncEval) parser.getEval()).getKbModel(),
        options.valueOf(kbModelOut));
    
    System.out.println(family.getParameterDescription(parameters));
  }
  
  private static ParametricCcgParser getCcgParser(List<String> lexiconLines) {
    List<String> unkLexiconLines = Collections.emptyList();
    List<String> ruleLines = Lists.newArrayList("FOO{0} BAR{0}");
    
    // Find any entity lexicon entries
    List<LexiconEntry> entityEntries = Lists.newArrayList();
    for (String lexiconLine : lexiconLines) {
      LexiconEntry entry = LexiconEntry.parseLexiconEntry(lexiconLine);
      Set<String> headAssignment = entry.getCategory().getAssignment().get(0);

      if (headAssignment.contains("entity")) {
        entityEntries.add(entry);
      }
    }
    
    if (entityEntries.size() == 0) {
      entityEntries = null;
    }
    
    return ParametricCcgParser.parseFromLexicon(lexiconLines, unkLexiconLines,
        ruleLines, new P3CcgFeatureFactory(false, true, entityEntries), null, false, null,
        true);
  }

  private static ParametricIncEval getEval(List<String> lexiconLines,
      List<String> defFilenames, DiscreteVariable categoryFeatureNames,
      DiscreteVariable relationFeatureNames, DiscreteVariable categoryPredicateFeatureNames,
      DiscreteVariable relationPredicateFeatureNames, List<String> categories,
      List<String> relations) {
    
    // Set up the per-predicate classifiers
    
    IndexedList<String> predicateNames = IndexedList.create();
    List<DiscreteVariable> eltFeatureVars = Lists.newArrayList();
    List<DiscreteVariable> predFeatureVars = Lists.newArrayList();
    
    predicateNames.addAll(categories);
    eltFeatureVars.addAll(Collections.nCopies(categories.size(), categoryFeatureNames));
    predFeatureVars.addAll(Collections.nCopies(categories.size(), categoryPredicateFeatureNames));
    
    predicateNames.addAll(relations);
    eltFeatureVars.addAll(Collections.nCopies(relations.size(), relationFeatureNames));
    predFeatureVars.addAll(Collections.nCopies(relations.size(), relationPredicateFeatureNames));
    
    return new KbParametricContinuationIncEval(predicateNames, eltFeatureVars, predFeatureVars,
        P3Utils.getIncEval(defFilenames));
  }

  public static void main(String[] args) {
    new TrainP3().run(args);
  }
}
