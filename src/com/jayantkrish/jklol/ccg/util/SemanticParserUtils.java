package com.jayantkrish.jklol.ccg.util;

import java.util.Collections;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplificationException;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;

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
   * @return
   */
  public static SemanticParserLoss testSemanticParser(List<CcgExample> testExamples, CcgParser parser,
      CcgInference inferenceAlg, ExpressionSimplifier simplifier, ExpressionComparator comparator,
      List<SemanticParserExampleLoss> exampleLossAccumulator) {
    int numCorrect = 0;
    int numCorrectLfPossible = 0;
    int numParsed = 0;

    LogFunction log = new NullLogFunction();
    for (CcgExample example : testExamples) {
      Expression2 correctLf = simplifier.apply(example.getLogicalForm());

      CcgParse parse = inferenceAlg.getBestParse(parser, example.getSentence(), null, log);
      System.out.println("====");
      System.out.println("SENT: " + example.getSentence().getWords());
      if (parse != null) {
        int correct = 0;
        int correctLfPossible = 0;
        Expression2 lf = null;

        try {
          lf = simplifier.apply(parse.getLogicalForm());
          correct = comparator.equals(lf, correctLf) ? 1 : 0;
        } catch (ExpressionSimplificationException e) {
          // Make lf print out as null.
          lf = Expression2.constant("null");
        }
        
        CcgParse conditionalParse = inferenceAlg.getBestConditionalParse(parser,
            example.getSentence(), null, log, null, null, null, correctLf);
        if (conditionalParse != null) {
          correctLfPossible = 1;
        }
        
        List<DependencyStructure> deps = parse.getAllDependencies();

        System.out.println("PREDICTED: " + lf);
        System.out.println("TRUE:      " + correctLf);
        System.out.println("DEPS: " + deps);
        System.out.println("CORRECT: " + correct);
        System.out.println("LICENSED: " + correctLfPossible);
        System.out.println("LEX: ");
        
        List<LexiconEntryInfo> entries = parse.getSpannedLexiconEntries();
        for (int i = 0; i < entries.size(); i++) {
          System.out.println("   " + entries.get(i));
        }

        numCorrect += correct;
        numCorrectLfPossible += correctLfPossible;
        numParsed++;
        
        if (exampleLossAccumulator != null) {
          exampleLossAccumulator.add(new SemanticParserExampleLoss(example, lf, deps,
              entries, correctLf, true, correct > 0, correctLfPossible > 0));
        }
      } else {
        System.out.println("NO PARSE");

        if (exampleLossAccumulator != null) {
          exampleLossAccumulator.add(new SemanticParserExampleLoss(example, null,
              Collections.emptyList(), Collections.emptyList(), correctLf, false, false, false));
        }
      }
    }

    double precision = ((double) numCorrect) / numParsed;
    double recall = ((double) numCorrect) / testExamples.size();
    double licensedRecall = ((double) numCorrectLfPossible) / testExamples.size();

    System.out.println("\nPrecision: " + precision);
    System.out.println("Recall: " + recall);
    System.out.println("Licensed Recall: " + licensedRecall);

    return new SemanticParserLoss(testExamples.size(), numParsed,
        numCorrect, numCorrectLfPossible);
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
