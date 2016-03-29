package com.jayantkrish.jklol.ccg;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.jayantkrish.jklol.ccg.chart.CcgBeamSearchChart;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.chart.SyntacticChartCost;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.parallel.MapReduceConfiguration;
import com.jayantkrish.jklol.parallel.MapReduceExecutor;
import com.jayantkrish.jklol.parallel.Mappers;
import com.jayantkrish.jklol.parallel.Reducer;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;

/**
 * Utility functions for CCG parsers.
 * 
 * @author jayantk
 */
public class CcgParserUtils {

  /**
   * Checks whether each example in {@code examples} can be produced
   * by this parser. Invalid examples are filtered out of the returned
   * examples.
   * 
   * @param parser
   * @param examples
   * @return
   */
  public static <T extends CcgExample> List<T> filterExampleCollection(
      final CcgParser parser, List<T> examples) {
    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();

    List<T> filteredExamples = executor.filter(examples, new Predicate<T>() {
      @Override
      public boolean apply(CcgExample example) {
        return isPossibleExample(parser, example);
      }
    });

    return filteredExamples;
  }

  /**
   * Returns {@code true} if {@code parser} can reproduce the
   * syntactic tree in {@code example}. 
   * 
   * @param parser
   * @param example
   * @return
   */
  public static boolean isPossibleExample(CcgParser parser, CcgExample example) {
    CcgBeamSearchChart chart = new CcgBeamSearchChart(example.getSentence(), Integer.MAX_VALUE, 100);
    SyntacticChartCost filter = SyntacticChartCost.createAgreementCost(example.getSyntacticParse());
    parser.parseCommon(chart, example.getSentence(), filter, null, -1, 1);
    List<CcgParse> parses = chart.decodeBestParsesForSpan(0, example.getSentence().size() - 1, 100, parser);
    if (parses.size() == 0) {
      // Provide a deeper analysis of why parsing failed.
      analyzeParseFailure(example.getSyntacticParse(), chart, parser.getSyntaxVarType(), "Parse failure", 0);
      System.out.println("Discarding example: " + example);
      return false;
    } else if (parses.size() > 1) {
      analyzeParseFailure(example.getSyntacticParse(), chart, parser.getSyntaxVarType(), "Parse duplication", 2);
      System.out.println("Duplicate correct parse: " + example.getSyntacticParse());
    }
    return true;
  }

  public static boolean analyzeParseFailure(CcgSyntaxTree tree, CcgChart chart,
      DiscreteVariable syntaxVarType, String errorMessage, int failureNum) {
    boolean foundFailurePoint = false;
    if (!tree.isTerminal()) {
      foundFailurePoint = foundFailurePoint || analyzeParseFailure(tree.getLeft(), chart,
          syntaxVarType, errorMessage, failureNum);
      foundFailurePoint = foundFailurePoint || analyzeParseFailure(tree.getRight(), chart,
          syntaxVarType, errorMessage, failureNum);
    }

    if (foundFailurePoint) {
      return true;
    } else {
      int spanStart = tree.getSpanStart();
      int spanEnd = tree.getSpanEnd();

      int numChartEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd);
      if (numChartEntries == failureNum) {
        if (tree.isTerminal()) {
          System.out.println(errorMessage + " terminal: " + tree.getWords() + " -> " +
              tree.getPreUnaryRuleSyntax() + "_" + tree.getRootSyntax() + " headed: " + tree.getHeadedSyntacticCategory());
        } else {
          System.out.println(errorMessage + " nonterminal: " + tree.getLeft().getRootSyntax() + " "
              + tree.getRight().getRootSyntax() + " -> " + tree.getRootSyntax());
          StringBuilder sb = new StringBuilder();
          sb.append("left entries: ");
          ChartEntry[] leftEntries = chart.getChartEntriesForSpan(tree.getLeft().getSpanStart(), tree.getLeft().getSpanEnd());
          int numLeftEntries = chart.getNumChartEntriesForSpan(tree.getLeft().getSpanStart(), tree.getLeft().getSpanEnd());
          for (int i = 0; i < numLeftEntries; i++) {
            sb.append(syntaxVarType.getValue(leftEntries[i].getHeadedSyntax()));
            sb.append(" ");
          }
          System.out.println(sb.toString());
          sb = new StringBuilder();
          sb.append("right entries: ");
          ChartEntry[] rightEntries = chart.getChartEntriesForSpan(tree.getRight().getSpanStart(), tree.getRight().getSpanEnd());
          int numRightEntries = chart.getNumChartEntriesForSpan(tree.getRight().getSpanStart(), tree.getRight().getSpanEnd());
          for (int i = 0; i < numRightEntries; i++) {
            sb.append(syntaxVarType.getValue(rightEntries[i].getHeadedSyntax()));
            sb.append(" ");
          }
          System.out.println(sb.toString());
        }
        return true;
      } else {
        return false;
      }
    }
  }

  public static SufficientStatistics getFeatureCounts(ParametricCcgParser family,
      Collection<CcgExample> examples) {
    MapReduceExecutor executor = MapReduceConfiguration.getMapReduceExecutor();

    CcgInference inference = CcgCkyInference.getDefault(100);
    Reducer<CcgExample, SufficientStatistics> reducer = new FeatureCountReducer(family, inference);
    return executor.mapReduce(examples, Mappers.<CcgExample>identity(), reducer);
  }

  private CcgParserUtils() {
    // Prevent instantiation.
  }

  /**
   * Computes empirical feature counts of a CCG parser on a
   * collection of training examples.
   *  
   * @author jayantk
   * @param <T>
   */
  private static class FeatureCountReducer implements Reducer<CcgExample, SufficientStatistics> {

    private final ParametricCcgParser ccgFamily;
    private final CcgParser parser;
    private final SufficientStatistics parserZeroParameters;
    private final CcgInference inference;
    private final LogFunction log;

    public FeatureCountReducer(ParametricCcgParser ccgFamily, CcgInference inference) {
      this.ccgFamily = Preconditions.checkNotNull(ccgFamily);
      this.parserZeroParameters = ccgFamily.getNewSufficientStatistics();
      this.parser = ccgFamily.getModelFromParameters(parserZeroParameters);
      this.inference = Preconditions.checkNotNull(inference);
      this.log = new NullLogFunction();
    }

    @Override
    public SufficientStatistics getInitialValue() {
      return ccgFamily.getNewSufficientStatistics();
    }

    @Override
    public SufficientStatistics reduce(CcgExample example, SufficientStatistics featureCounts) {
      Preconditions.checkArgument(example.getLogicalForm() == null,
          "FeatureCountReducer cannot condition on logical form");
      CcgParse bestParse = CcgPerceptronOracle.getBestConditionalParse(inference, parser, null,
          example, log);

      if (bestParse != null) {
        ccgFamily.incrementSufficientStatistics(featureCounts, parserZeroParameters,
            example.getSentence(), bestParse, 1.0);
      }
      
      return featureCounts;
    }

    @Override
    public SufficientStatistics combine(SufficientStatistics other, SufficientStatistics accumulated) {
      accumulated.increment(other, 1.0);
      return accumulated;
    }
  }
}
