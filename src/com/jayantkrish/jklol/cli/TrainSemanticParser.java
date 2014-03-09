package com.jayantkrish.jklol.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgPerceptronOracle;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainSemanticParser extends AbstractCli {

  private OptionSpec<String> trainingData;
  private OptionSpec<String> modelOutput;

  private OptionSpec<Integer> beamSize;
  
  public TrainSemanticParser() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE,
        CommonOptions.PARAMETRIC_CCG_PARSER);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingData = parser.accepts("trainingData").withRequiredArg().ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();

    // Optional options
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
  }
  
  @Override
  public void run(OptionSet options) {
    List<CcgExample> trainingExamples = readCcgExamplesJson(options.valueOf(trainingData));
    
    ParametricCcgParser family = createCcgParser(null, null, new DefaultCcgFeatureFactory(null, true));

    CcgInference inferenceAlgorithm = new CcgBeamSearchInference(null, options.valueOf(beamSize),
        -1, Integer.MAX_VALUE, 1, false);
    GradientOracle<CcgParser, CcgExample> oracle = new CcgPerceptronOracle(family,
        inferenceAlgorithm, 0.0);

    GradientOptimizer trainer = createGradientOptimizer(trainingExamples.size());
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);
    CcgParser ccgParser = family.getModelFromParameters(parameters);

    System.out.println("Serializing trained model...");
    IoUtils.serializeObjectToFile(ccgParser, options.valueOf(modelOutput));

    System.out.println("Trained model parameters:");
    System.out.println(family.getParameterDescription(parameters));
  }

  private static List<CcgExample> readCcgExamplesJson(String jsonFilename) {
    List<CcgExample> examples = Lists.newArrayList();
    try {
      ExpressionParser<Expression> lfParser = ExpressionParser.lambdaCalculus();
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(new File(jsonFilename));
      Iterator<JsonNode> iter = rootNode.elements();
      while (iter.hasNext()) {
        JsonNode exampleNode = iter.next();
        
        String utterance = exampleNode.get("utterance").asText();
        String targetFormula = exampleNode.get("targetFormula").asText();
        
        List<String> words = Arrays.asList(utterance.split("\\s"));
        Expression lf = lfParser.parseSingleExpression(targetFormula);
        
        // Parts-of-speech are assumed to be unknown.
        List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
        SupertaggedSentence sentence = ListSupertaggedSentence.createWithUnobservedSupertags(words, posTags);

        CcgExample example = new CcgExample(sentence, null, null, lf);
        examples.add(example);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return examples;
  }

  public static void main(String[] args) {
    new TrainSemanticParser().run(args);
  }
}
