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
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;

public class BatchLexiconInductionTest extends TestCase {

  List<CcgExample> trainingData;
  
  BatchLexiconInduction lexiconInduction;
  
  CcgBeamSearchInference inference;
  
  private static final String[] trainingWords = {
      "translate hello to spanish",
      "translate hello to french",
      "translate hello to hindi",
      "translate restaurant to spanish",
      "translate restaurant to french",
      "translate restaurant to hindi",
      "translate french to hindi",
      "translate spanish to hindi",
      "translate spanish to french",
  };

  private static final String[] trainingLfs = {
    "(translate (x-source thisApp) (word hello) (language spanish))",
    "(translate (x-source thisApp) (word hello) (language french))",
    "(translate (x-source thisApp) (word hello) (language hindi))",
    "(translate (x-source thisApp) (word restaurant) (language spanish))",
    "(translate (x-source thisApp) (word restaurant) (language french))",
    "(translate (x-source thisApp) (word restaurant) (language hindi))",
    "(translate (x-source thisApp) (word french) (language spanish))",
    "(translate (x-source thisApp) (word spanish) (language french))",
    "(translate (x-source thisApp) (word spanish) (language french))",
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

    inference = new CcgBeamSearchInference(null, 100, -1, Integer.MAX_VALUE, 1, false);
    GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(
        trainingData.size() * 10, 1, 1, true, true, 0.1, new NullLogFunction());
    lexiconInduction = new BatchLexiconInduction(4, true, false, false,
        new DefaultCcgFeatureFactory(null, false), binaryRules, unaryRules, inference, trainer);
  }

  public void testLexiconInduction() {
    CcgParser parser = lexiconInduction.induceLexicon(trainingData);
    List<CcgParse> parses = inference.beamSearch(parser, createSupertaggedSentence("translate hello to french"),
        null, new NullLogFunction());

    System.out.println("PARSES:");
    for (CcgParse parse : parses) {
      System.out.println(parse + " " + parse.getLogicalForm().simplify());
      System.out.println(parse.getSubtreeProbability());
      for (LexiconEntry entry : parse.getSpannedLexiconEntries()) {
        System.out.println(entry);
      }
      System.out.println("======");
    }
  }

  private static SupertaggedSentence createSupertaggedSentence(String sentence) {
    List<String> words = Arrays.asList(sentence.split("\\s"));
    List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    return ListSupertaggedSentence.createWithUnobservedSupertags(words, posTags);
  }
}
