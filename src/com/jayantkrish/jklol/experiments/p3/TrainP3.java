package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.gi.GroundedParser;
import com.jayantkrish.jklol.ccg.gi.GroundedParserInference;
import com.jayantkrish.jklol.ccg.gi.GroundedParserLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.gi.GroundedParserPipelinedInference;
import com.jayantkrish.jklol.ccg.gi.ParametricGroundedParser;
import com.jayantkrish.jklol.ccg.gi.ValueGroundedParseExample;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.experiments.p3.KbParametricContinuationIncEval.KbContinuationIncEval;
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
    List<String> categoryList = IoUtils.readLines(options.valueOf(categories));
    List<String> relationList = IoUtils.readLines(options.valueOf(relations));
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
    ParametricIncEval evalFamily = getEval(lexiconLines, options.valuesOf(defs),
        categoryFeatureNames, relationFeatureNames, categoryList, relationList);
    ParametricGroundedParser family = new ParametricGroundedParser(ccgFamily, evalFamily);

    GroundedParserInference inf = new GroundedParserPipelinedInference(
        CcgCkyInference.getDefault(100), P3Utils.getSimplifier(), 10, 100, false);
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
      List<String> defFilenames, DiscreteVariable categoryFeatureNames,
      DiscreteVariable relationFeatureNames, List<String> categories,
      List<String> relations) {
    
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
    for (String cat : categories) {
      predicateNames.add(cat);
      families.add(categoryFamily);
      featureVars.add(input);
      labelAssignments.add(labelAssignment);
    }    
    for (String rel : relations) {
      predicateNames.add(rel);
      families.add(relationFamily);
      featureVars.add(input);
      labelAssignments.add(labelAssignment);
    }

    KbFeatureGenerator featureGen = new KbFeatureGenerator();
    
    return new KbParametricContinuationIncEval(predicateNames, families, featureVars,
        labelAssignments, featureGen, P3Utils.getIncEval(defFilenames));
  }

  public static void main(String[] args) {
    new TrainP3().run(args);
  }
}
