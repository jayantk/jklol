package com.jayantkrish.jklol.experiments.p3;

import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.gi.GroundedCcgParse;
import com.jayantkrish.jklol.ccg.gi.GroundedParser;
import com.jayantkrish.jklol.ccg.gi.GroundedParserInference;
import com.jayantkrish.jklol.ccg.gi.GroundedParserPipelinedInference;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.experiments.p3.KbParametricContinuationIncEval.KbContinuationIncEval;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class TestP3 extends AbstractCli {

  private OptionSpec<String> testData;
  private OptionSpec<String> categoryFilename;
  private OptionSpec<String> relationFilename;
  private OptionSpec<String> exampleFilename;
  private OptionSpec<String> defs;

  private OptionSpec<String> categories;
  private OptionSpec<String> relations;
  private OptionSpec<String> categoryFeatures;
  private OptionSpec<String> relationFeatures;
  
  private OptionSpec<String> parserOpt;
  private OptionSpec<String> kbModelOpt;

  public TestP3() {
    super();
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    testData = parser.accepts("testData").withRequiredArg().withValuesSeparatedBy(',')
        .ofType(String.class).required();
    categoryFilename = parser.accepts("categoryFilename").withRequiredArg()
        .ofType(String.class).required();
    relationFilename = parser.accepts("relationFilename").withRequiredArg()
        .ofType(String.class).required();
    exampleFilename = parser.accepts("exampleFilename").withRequiredArg()
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

    parserOpt = parser.accepts("parser").withRequiredArg().ofType(String.class).required();
    kbModelOpt = parser.accepts("kbModel").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    IndexedList<String> categoryList = IndexedList.create(IoUtils.readLines(options.valueOf(categories)));
    IndexedList<String> relationList = IndexedList.create(IoUtils.readLines(options.valueOf(relations)));
    DiscreteVariable categoryFeatureNames = new DiscreteVariable("categoryFeatures",
        IoUtils.readLines(options.valueOf(categoryFeatures)));
    DiscreteVariable relationFeatureNames = new DiscreteVariable("relationFeatures",
        IoUtils.readLines(options.valueOf(relationFeatures)));
    
    List<P3Example> examples = Lists.newArrayList();
    for (String trainingDataEnv : options.valuesOf(testData)) {
      examples.addAll(P3Utils.readTrainingData(trainingDataEnv, categoryFeatureNames,
          relationFeatureNames, options.valueOf(categoryFilename), options.valueOf(relationFilename),
          options.valueOf(exampleFilename), null, categoryList, relationList));
    }
    
    CcgParser ccgParser = IoUtils.readSerializedObject(options.valueOf(parserOpt), CcgParser.class);
    KbModel kbModel = IoUtils.readSerializedObject(options.valueOf(kbModelOpt), KbModel.class);
    KbContinuationIncEval eval = new KbContinuationIncEval(
        P3Utils.getIncEval(options.valuesOf(defs)), kbModel);
    
    GroundedParser parser = new GroundedParser(ccgParser, eval);
    GroundedParserInference inf = new GroundedParserPipelinedInference(
        CcgCkyInference.getDefault(100), P3Utils.getSimplifier(), 10, 100, false);
    
    evaluate(examples, parser, inf);
  }
  
  private static void evaluate(List<P3Example> examples, GroundedParser parser,
      GroundedParserInference inf) {
    int numCorrect = 0;
    int numNoPrediction = 0;
    for (P3Example ex : examples) {
      System.out.println(ex.getSentence());
      Set<?> s = (Set<?>) ex.getLabel();
      System.out.println(s + " " + s.size());

      List<GroundedCcgParse> parses = inf.beamSearch(parser, ex.getSentence(), ex.getDiagram());
      
      CountAccumulator<ImmutableSet<Object>> denotationCounts = CountAccumulator.create();
      double partitionFunction = 0.0;
      for (GroundedCcgParse parse : parses) {
        Object d = parse.getDenotation();
        double prob = parse.getSubtreeProbability();
        partitionFunction += prob;

        if (d instanceof Set) {
          denotationCounts.increment(ImmutableSet.copyOf((Set<?>) d), prob);
        }
      }

      System.out.println("   " + parses.size() + " parses");      
      int numToPrint = Math.min(parses.size(), 10);
      for (int i = 0; i < numToPrint; i++) {
        GroundedCcgParse parse = parses.get(i);
        Expression2 lf = P3Utils.getSimplifier().apply(parse.getLogicalForm());
        double prob = parse.getSubtreeProbability() / partitionFunction;
        System.out.println("   " + prob + " " + parse.getDenotation() + " " + lf);
      }
      
      if (denotationCounts.keySet().size() > 0) {
        ImmutableSet<Object> items = denotationCounts.getSortedKeys().get(0);
        System.out.println("  Predicted: " + items + " " + items.size());
        if (ex.getLabel().equals(items)) {
          System.out.println("  CORRECT");
          numCorrect++;
        } else {
          System.out.println("  INCORRECT");
        }
      } else {
        System.out.println("  NO PREDICTION");
        numNoPrediction++;
      }
    }
    
    int numPredicted = (examples.size() - numNoPrediction);
    double precision = ((double) numCorrect) / numPredicted;
    double recall = ((double) numCorrect) / examples.size();
    System.out.println("Precision: " + precision + " (" + numCorrect + " / " + numPredicted + ")");
    System.out.println("Recall: " + recall + " (" + numCorrect + " / " + examples.size() + ")");
  }

  public static void main(String[] args) {
    new TestP3().run(args);
  }
}
