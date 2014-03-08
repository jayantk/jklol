package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgBeamSearchInference;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.DefaultCcgFeatureFactory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

public class BatchLexiconInductionTest extends TestCase {

  List<CcgExample> trainingData;
  
  BatchLexiconInduction lexiconInduction;
  
  CcgInference inference;
  
  private static final String[] trainingWords = {
      "city",
      "the city",
      "the city in california"
  };
  private static final String[] trainingLfs = {
    "(lambda x (city x))",
    "(lambda x (city x))",
    "(lambda x (exists y (and (city x) (in x y) (california y))))"
  };

  public void setUp() {
    Preconditions.checkState(trainingWords.length == trainingLfs.length);
    ExpressionParser<Expression> parser = ExpressionParser.lambdaCalculus();
    
    List<CcgBinaryRule> binaryRules = Lists.newArrayList(CcgBinaryRule.parseFrom(
        "NOT_A_CAT{0} NOT_A_CAT{0} NOT_A_CAT{0},,OTHER,NOT_A_DEP 0 0"));
    List<CcgUnaryRule> unaryRules = Lists.newArrayList(CcgUnaryRule.parseFrom(
        "NOT_A_CAT{0} NOT_A_CAT{0}"));

    trainingData = Lists.newArrayList();
    for (int i = 0; i < trainingWords.length; i++) {
      SupertaggedSentence sentence = createSupertaggedSentence(trainingWords[i]);
      Expression logicalForm = parser.parseSingleExpression(trainingLfs[i]);
      trainingData.add(new CcgExample(sentence, null, null, logicalForm));
    }

    GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(
        trainingData.size(), 1, 1, true, true, 0.0, new DefaultLogFunction());
    lexiconInduction = new BatchLexiconInduction(1, true, true, false,
        new DefaultCcgFeatureFactory(null), binaryRules, unaryRules, trainer);
    
    inference = new CcgBeamSearchInference(null, 100, -1, Integer.MAX_VALUE, 1, false);
  }

  public void testLexiconInduction() {
    CcgParser parser = lexiconInduction.induceLexicon(trainingData);
    CcgParse parse = inference.getBestParse(parser, createSupertaggedSentence("the city"),
        null, new NullLogFunction());
    System.out.println(parse + " " + parse.getLogicalForm());
  }

  private static SupertaggedSentence createSupertaggedSentence(String sentence) {
    List<String> words = Arrays.asList(sentence.split("\\s"));
    List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    return ListSupertaggedSentence.createWithUnobservedSupertags(words, posTags);
  }
}
