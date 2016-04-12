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
import com.jayantkrish.jklol.ccg.gi.ValueGroundedParseExample;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.experiments.p3.KbParametricContinuationIncEval.KbContinuationIncEval;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IoUtils;

public class TestP3 extends AbstractCli {

  private OptionSpec<String> testData;
  private OptionSpec<String> defs;

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
    defs = parser.accepts("defs").withRequiredArg().withValuesSeparatedBy(',')
        .ofType(String.class);
    
    categoryFeatures = parser.accepts("categoryFeatures").withRequiredArg()
        .ofType(String.class).required();
    relationFeatures = parser.accepts("relationFeatures").withRequiredArg()
        .ofType(String.class).required();

    parserOpt = parser.accepts("parser").withRequiredArg().ofType(String.class).required();
    kbModelOpt = parser.accepts("kbModel").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    DiscreteVariable categoryFeatureNames = new DiscreteVariable("categoryFeatures",
        IoUtils.readLines(options.valueOf(categoryFeatures)));
    DiscreteVariable relationFeatureNames = new DiscreteVariable("relationFeatures",
        IoUtils.readLines(options.valueOf(relationFeatures)));
    
    List<ValueGroundedParseExample> examples = Lists.newArrayList();
    for (String trainingDataEnv : options.valuesOf(testData)) {
      examples.addAll(P3Utils.readTrainingData(trainingDataEnv, categoryFeatureNames,
          relationFeatureNames));
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
  
  private static void evaluate(List<ValueGroundedParseExample> examples, GroundedParser parser,
      GroundedParserInference inf) {
    int numCorrect = 0;
    int numNoPrediction = 0;
    for (ValueGroundedParseExample ex : examples) {
      System.out.println(ex.getSentence());

      List<GroundedCcgParse> parses = inf.beamSearch(parser, ex.getSentence(), ex.getDiagram());
      
      CountAccumulator<ImmutableSet<Object>> denotationCounts = CountAccumulator.create();
      for (GroundedCcgParse parse : parses) {
        Object d = parse.getDenotation();
        double prob = parse.getSubtreeProbability();
        if (d instanceof Set) {
          denotationCounts.increment(ImmutableSet.copyOf((Set<?>) d), prob);
        }
      }
      
      int numToPrint = Math.min(denotationCounts.keySet().size(), 5);
      List<ImmutableSet<Object>> sortedDenotations = denotationCounts.getSortedKeys();
      for (int i = 0; i < numToPrint; i++) {
        ImmutableSet<Object> d = sortedDenotations.get(i);
        System.out.println("   " + denotationCounts.getProbability(d) + " " + d);
      }
      
      if (denotationCounts.keySet().size() > 0) {
        ImmutableSet<Object> items = denotationCounts.getSortedKeys().get(0);
        if (ex.getLabel().equals(items)) {
          System.out.println("CORRECT");
          numCorrect++;
        } else {
          System.out.println("INCORRECT");
        }
      } else {
        System.out.println("NO PREDICTION");
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
