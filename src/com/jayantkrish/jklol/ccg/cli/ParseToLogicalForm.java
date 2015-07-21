package com.jayantkrish.jklol.ccg.cli;

import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.ccg.CcgExactInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.SupertaggingCcgParser;
import com.jayantkrish.jklol.ccg.SupertaggingCcgParser.CcgParseResult;
import com.jayantkrish.jklol.ccg.augment.CcgParseAugmenter;
import com.jayantkrish.jklol.ccg.augment.TemplateCcgParseAugmenter;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.supertag.Supertagger;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.IoUtils;

public class ParseToLogicalForm extends AbstractCli {
  
  private OptionSpec<String> parser;
  private OptionSpec<String> supertagger;
  private OptionSpec<Double> multitagThresholds;
  private OptionSpec<String> lfTemplates;
  private OptionSpec<String> inputFile;

  private OptionSpec<Long> maxParseTimeMillis;
  private OptionSpec<Integer> maxChartSize;
  private OptionSpec<Integer> parserThreads;
  
  public ParseToLogicalForm() {
    super();
  }
  
  @Override
  public void initializeOptions(OptionParser optionParser) {
    // Required arguments.
    parser = optionParser.accepts("parser", "File containing serialized CCG parser.").withRequiredArg()
        .ofType(String.class).required();
    supertagger = optionParser.accepts("supertagger").withRequiredArg().ofType(String.class).required();
    multitagThresholds = optionParser.accepts("multitagThreshold").withRequiredArg()
        .ofType(Double.class).withValuesSeparatedBy(',').required();
    
    lfTemplates = optionParser.accepts("lfTemplates").withRequiredArg().ofType(String.class).required();
    inputFile = optionParser.accepts("inputFile").withRequiredArg().ofType(String.class).required();
    
    // Optional arguments
    maxParseTimeMillis = optionParser.accepts("maxParseTimeMillis").withRequiredArg()
        .ofType(Long.class).defaultsTo(-1L);
    maxChartSize = optionParser.accepts("maxChartSize").withRequiredArg().ofType(Integer.class)
        .defaultsTo(Integer.MAX_VALUE);
    parserThreads = optionParser.accepts("parserThreads").withRequiredArg().ofType(Integer.class)
        .defaultsTo(1);
  }

  @Override
  public void run(OptionSet options) {
    // Read in supertagger and CCG parser.
    CcgParser ccgParser = IoUtils.readSerializedObject(options.valueOf(parser), CcgParser.class);
    Supertagger tagger = IoUtils.readSerializedObject(options.valueOf(supertagger), Supertagger.class);
    double[] tagThresholds = Doubles.toArray(options.valuesOf(multitagThresholds));

    SupertaggingCcgParser supertaggingParser = new SupertaggingCcgParser(ccgParser, 
        new CcgExactInference(null, options.valueOf(maxParseTimeMillis),
            options.valueOf(maxChartSize), options.valueOf(parserThreads)),
        tagger, tagThresholds, TrainSyntacticCcgParser.SUPERTAG_ANNOTATION_NAME);

    // Read the logical form templates.
    CcgParseAugmenter augmenter = TemplateCcgParseAugmenter.parseFrom(IoUtils.readLines(options.valueOf(lfTemplates)), true);
    ExpressionSimplifier simplifier = ExpressionSimplifier.lambdaCalculus();
    for (String line : IoUtils.readLines(options.valueOf(inputFile))) {
      List<String> words = Lists.newArrayList();
      List<String> posTags = Lists.newArrayList();
      TestSyntacticCcgParser.parsePosTaggedInput(Arrays.asList(line.split("\\s")), words, posTags);

      StringBuilder sb = new StringBuilder();
      sb.append(line);
      sb.append("\t");

      CcgParseResult result = null;
      Expression2 lf = null;
      try { 
        result = supertaggingParser.parse(new AnnotatedSentence(words, posTags));
        if (result != null && result.getParse().getSyntacticCategory().isAtomic()) {
          CcgParse parse = result.getParse();
          CcgParse augmentedParse = augmenter.addLogicalForms(parse);
          lf = augmentedParse.getLogicalForm();
        }
      } catch (Exception e) {
        System.err.println("Error processing sentence: " + words);
        e.printStackTrace(System.err);
      }

      if (result == null) {
        sb.append("NO PARSE");
      } else if (!result.getParse().getSyntacticCategory().isAtomic()) {
        sb.append("NOT ATOMIC\t" + result.getParse().getSyntacticParse());
      } else if (lf == null) {
        sb.append("NO LF CONVERSION\t" + result.getParse().getSyntacticParse());
      } else {
        // TODO: implement a simplifier for logic that also assumes the quantification
        // of any final lambda args and expands universal quantifiers.
        lf = simplifier.apply(lf);

        sb.append(lf);
        sb.append("\t");
        
        String resultString = null;
        if (result != null) {
          resultString = result.getParse().getSyntacticParse().toString();
        }
        sb.append(resultString);
        sb.append("\t");

        Multimap<String, String> categoryInstances = HashMultimap.create();
        Multimap<String, List<String>> relationInstances = HashMultimap.create();
        getEntailedPredicateInstances(lf, categoryInstances, relationInstances);

        sb.append(Joiner.on(",").join(categoryInstances.keySet()));
        sb.append("\t");
        List<String> catInstances = Lists.newArrayList();
        for (String varName : categoryInstances.keySet()) {
          for (String predName : categoryInstances.get(varName)) {
            catInstances.add(predName + " " + varName);
          }
        }
        sb.append(Joiner.on(",").join(catInstances));
        sb.append("\t");
        List<String> relInstances = Lists.newArrayList();
        for (String predName : relationInstances.keySet()) {
          for (List<String> entities : relationInstances.get(predName)) {
            relInstances.add(predName + " " + Joiner.on(" ").join(entities));
          }
        }
        sb.append(Joiner.on(",").join(relInstances));
      }
      System.out.println(sb.toString());
    }
  }

  private static void getEntailedPredicateInstances(Expression2 expression,
      Multimap<String, String> categoryPredicateInstances,
      Multimap<String, List<String>> relationPredicateInstances) {
    for (int i = 0; i < expression.size(); i++) {
      Expression2 subexpression = expression.getSubexpression(i);
      if (!subexpression.isConstant()) {
        List<Expression2> components = subexpression.getSubexpressions();
        if (components.size() == 2  && components.get(0).isConstant()
            && components.get(1).isConstant()) {
          categoryPredicateInstances.put(components.get(1).getConstant(),
              components.get(0).getConstant());
        } else if (components.size() == 3 && components.get(0).isConstant()
            && components.get(1).isConstant() && components.get(2).isConstant()) {
          relationPredicateInstances.put(components.get(0).getConstant(),
              Arrays.asList(components.get(1).getConstant(), components.get(2).getConstant()));          
        }
      }
    }
  }

  public static void main(String[] args) {
    new ParseToLogicalForm().run(args);
  }
}
