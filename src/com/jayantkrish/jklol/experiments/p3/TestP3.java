package com.jayantkrish.jklol.experiments.p3;

import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.gi.ValueGroundedParseExample;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.util.IoUtils;

public class TestP3 extends AbstractCli {

  private OptionSpec<String> testData;
  private OptionSpec<String> environment;
  private OptionSpec<String> defs;
  private OptionSpec<String> genDefs;
  
  private OptionSpec<String> categoryFeatures;
  private OptionSpec<String> relationFeatures;
  
  private OptionSpec<String> lexicon;
  
  private OptionSpec<String> parserOpt;
  private OptionSpec<String> kbModelOpt;

  public TestP3() {
    super();
  }
  
  @Override
  public void initializeOptions(OptionParser parser) {
    testData = parser.accepts("testData").withRequiredArg().withValuesSeparatedBy(',')
        .ofType(String.class).required();
    environment = parser.accepts("environment").withRequiredArg().ofType(String.class);
    defs = parser.accepts("defs").withRequiredArg().ofType(String.class);
    genDefs = parser.accepts("gendefs").withRequiredArg().ofType(String.class);

    categoryFeatures = parser.accepts("categoryFeatures").withRequiredArg()
        .ofType(String.class).required();
    relationFeatures = parser.accepts("relationFeatures").withRequiredArg()
        .ofType(String.class).required();

    lexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();

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
    
    CcgParser parser = IoUtils.readSerializedObject(options.valueOf(parserOpt), CcgParser.class);
    KbModel kbModel = IoUtils.readSerializedObject(options.valueOf(kbModelOpt), KbModel.class);
  }

  public static void main(String[] args) {
    new TestP3().run(args);
  }
}
