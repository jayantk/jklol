package com.jayantkrish.jklol.ccg.util;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lexicon.SpanFeatureAnnotation;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.CountAccumulator;

public class SemanticParserUtils {

  /**
   * Evaluate {@code parser} by measuring its precision and
   * recall at reproducing the annotated logical forms in
   * {@code testExamples}.
   * 
   * @param testExamples
   * @param parser
   * @param inferenceAlg
   * @param simplifier
   * @param comparator
   * @param exampleLossAccumulator if non-null, the predictions for each example are
   * added to this list.
   * @param print if true, print out loss information.
   * @return
   */
  public static SemanticParserLoss testSemanticParser(List<CcgExample> testExamples, CcgParser parser,
      CcgInference inferenceAlg, ExpressionSimplifier simplifier, ExpressionComparator comparator,
      List<SemanticParserExampleLoss> exampleLossAccumulator, boolean print) {

    int numCorrect = 0;
    int numCorrectLfPossible = 0;
    int numParsed = 0;

    LogFunction log = new NullLogFunction();
    for (CcgExample example : testExamples) {
      Expression2 correctLf = simplifier.apply(example.getLogicalForm());

      List<CcgParse> parses = inferenceAlg.beamSearch(parser, example.getSentence(), null, log);
      CountAccumulator<Expression2> lfProbs = estimateLfProbabilities(parses, simplifier);
      
      if (print) {
        System.out.println("====");
        System.out.println("SENT: " + example.getSentence().getWords());
      }

      if (lfProbs.keySet().size() > 0) {
        Expression2 best = lfProbs.getSortedKeys().get(0); 
        // Pick the best parse that produces this expression:
        CcgParse parse = null;
        for (CcgParse p : parses) {
          if (simplifier.apply(p.getLogicalForm()).equals(best)) {
            parse = p;
            break;
          }
        }
        Preconditions.checkState(parse != null);

        int correct = 0;
        int correctLfPossible = 0;
        Expression2 lf = lfProbs.getSortedKeys().get(0);

        correct = comparator.equals(lf, correctLf) ? 1 : 0;

        List<CcgParse> conditionalParses = CcgLoglikelihoodOracle.filterParsesByLogicalForm(
            correctLf, comparator, parses);
        if (conditionalParses.size() > 0) {
          correctLfPossible = 1;
        }

        List<DependencyStructure> deps = parse.getAllDependencies();

        if (print) {
          System.out.println("PREDICTED: " + lf);
          List<Expression2> sorted = lfProbs.getSortedKeys();
          int numToPrint = Math.min(sorted.size(), 5);
          for (int i = 0; i < numToPrint; i++) {
            Expression2 key = sorted.get(i);
            System.out.println("   " + lfProbs.getProbability(key) + " " + key);
          }

          System.out.println("TRUE:      " + correctLf);
          System.out.println("DEPS: " + deps);
          System.out.println("CORRECT: " + correct);
          System.out.println("LICENSED: " + correctLfPossible);
          System.out.println("LEX: ");

          List<LexiconEntryInfo> entries = parse.getSpannedLexiconEntries();
          for (int i = 0; i < entries.size(); i++) {
            System.out.println("   " + entries.get(i));
          }
        }

        numCorrect += correct;
        numCorrectLfPossible += correctLfPossible;
        numParsed++;

        if (exampleLossAccumulator != null) {
          exampleLossAccumulator.add(new SemanticParserExampleLoss(example, lf,
              correctLf, true, correct > 0, correctLfPossible > 0));
        }
      } else {
        if (print) {
          System.out.println("NO PARSE");
        }

        if (exampleLossAccumulator != null) {
          exampleLossAccumulator.add(new SemanticParserExampleLoss(example, null,
              correctLf, false, false, false));
        }
      }
    }

    double precision = ((double) numCorrect) / numParsed;
    double recall = ((double) numCorrect) / testExamples.size();
    double licensedRecall = ((double) numCorrectLfPossible) / testExamples.size();

    if (print) {
      System.out.println("\nPrecision: " + precision);
      System.out.println("Recall: " + recall);
      System.out.println("Licensed Recall: " + licensedRecall);
    }

    return new SemanticParserLoss(testExamples.size(), numParsed,
        numCorrect, numCorrectLfPossible);
  }

  public static List<CcgExample> annotateFeatures(List<CcgExample> examples,
      FeatureVectorGenerator<StringContext> featureGen, String annotationName) {
    List<CcgExample> newExamples = Lists.newArrayList();
    for (CcgExample example : examples) {
      AnnotatedSentence sentence = example.getSentence();
      SpanFeatureAnnotation annotation = SpanFeatureAnnotation.annotate(sentence, featureGen);

      AnnotatedSentence annotatedSentence = sentence.addAnnotation(annotationName, annotation);

      newExamples.add(new CcgExample(annotatedSentence, example.getDependencies(),
          example.getSyntacticParse(), example.getLogicalForm()));
    }
    return newExamples;
  }

  
  public static CountAccumulator<Expression2> estimateLfProbabilities(List<CcgParse> parses,
      ExpressionSimplifier simplifier) {
    CountAccumulator<Expression2> probs = CountAccumulator.create();

    for (CcgParse parse : parses) {
      Expression2 lf = parse.getLogicalForm();
      if (lf != null) {
        lf = simplifier.apply(lf);
        probs.increment(lf, parse.getSubtreeProbability());
      } 
    }
    return probs;
  }

  
  private SemanticParserUtils() {
    // Prevent instantiation.
  }
  
  public static class SemanticParserLoss {
    private final int numExamples;
    private final int numParsed;
    private final int numParsedCorrectly;
    private final int numPossibleToParseCorrectly;
    
    public SemanticParserLoss(int numExamples, int numParsed,
        int numParsedCorrectly, int numPossibleToParseCorrectly) {
      this.numExamples = numExamples;
      this.numParsed = numParsed;
      this.numParsedCorrectly = numParsedCorrectly;
      this.numPossibleToParseCorrectly = numPossibleToParseCorrectly;
    }
    
    public double getPrecision() {
      return ((double) numParsedCorrectly) / numParsed;
    }
    
    public double getRecall() {
      return ((double) numParsedCorrectly) / numExamples;
    }
    
    /**
     * Gets the maximum recall possible under the given lexicon
     * and grammar.
     * 
     * @return
     */
    public double getLexiconRecall() {
      return ((double) numPossibleToParseCorrectly) / numExamples;
    }
    
    public SemanticParserLoss add(SemanticParserLoss o) {
      return new SemanticParserLoss(numExamples + o.numExamples,
          numParsed + o.numParsed, numParsedCorrectly + o.numParsedCorrectly,
          numPossibleToParseCorrectly + o.numPossibleToParseCorrectly);
    }
  }
}
