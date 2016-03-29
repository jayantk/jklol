package com.jayantkrish.jklol.ccg.chart;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntryInfo;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.IntMultimap;

/**
 * Common implementations of CCG parse chart methods.
 * 
 * @author jayantk
 */
public abstract class AbstractCcgChart implements CcgChart {

  // The words and pos tags of the sentence being parsed.
  private final AnnotatedSentence input;
  private final List<String> terminals;
  private final List<String> posTags;
  private int[] posTagsInt;
  
  // Maximum number of chart entries.
  private final int maxChartSize;

  // Various kinds of distances between words in the sentence.
  private int[] wordDistances;
  private int[] puncDistances;
  private int[] verbDistances;

  protected ChartCost entryFilter;

  // The syntactic category combinations that will be considered
  // while parsing this sentence.
  private DiscreteFactor syntaxDistribution;

  private int[][] assignmentVarIndexAccumulator;
  private long[][] assignmentAccumulator;
  private long[][] filledDepAccumulator;
  private int[][] unfilledDepVarIndexAccumulator;
  private long[][] unfilledDepAccumulator;
  private long[] depLongCache;
  private double[] depProbCache;

  private boolean finishedParsing;

  public AbstractCcgChart(AnnotatedSentence input, int maxChartSize) {
    this.input = input;
    this.terminals = ImmutableList.copyOf(input.getWords());
    this.posTags = ImmutableList.copyOf(input.getPosTags());
    this.maxChartSize = maxChartSize;

    // dependencyTensor, the distance arrays / tensors, and syntax distribution are
    // left null, and must be manually set.

    this.finishedParsing = false;
  }

  @Override
  public int size() {
    return terminals.size();
  }
  
  @Override
  public AnnotatedSentence getInput() {
    return input;
  }

  @Override
  public List<String> getWords() {
    return terminals;
  }

  @Override
  public List<String> getPosTags() {
    return posTags;
  }
  
  @Override
  public int getMaxChartEntries() {
    return maxChartSize;
  }

  @Override
  public final void setPosTagsInt(int[] posTagsInt) {
    Preconditions.checkArgument(posTagsInt.length == size());
    this.posTagsInt = posTagsInt;
  }

  @Override
  public final void setWordDistances(int[] wordDistances) {
    Preconditions.checkArgument(wordDistances.length == size() * size());
    this.wordDistances = wordDistances;
  }

  @Override
  public final void setPuncDistances(int[] puncDistances) {
    Preconditions.checkArgument(puncDistances.length == size() * size());
    this.puncDistances = puncDistances;
  }

  @Override
  public final void setVerbDistances(int[] verbDistances) {
    Preconditions.checkArgument(verbDistances.length == size() * size());
    this.verbDistances = verbDistances;
  }

  @Override
  public final void setChartCost(ChartCost entryFilter) {
    this.entryFilter = entryFilter;
  }

  @Override
  public final int[] getPosTagsInt() {
    return posTagsInt;
  }

  @Override
  public final int[] getWordDistances() {
    return wordDistances;
  }

  @Override
  public final int[] getPunctuationDistances() {
    return puncDistances;
  }

  @Override
  public final int[] getVerbDistances() {
    return verbDistances;
  }

  @Override
  public final void setAssignmentVarIndexAccumulator(int[][] assignmentVarIndexAccumulator) {
    this.assignmentVarIndexAccumulator = assignmentVarIndexAccumulator;
  }

  @Override
  public final void setAssignmentAccumulator(long[][] assignmentAccumulator) {
    this.assignmentAccumulator = assignmentAccumulator;
  }

  @Override
  public final void setFilledDepAccumulator(long[][] filledDepAccumulator) {
    this.filledDepAccumulator = filledDepAccumulator;
  }

  @Override
  public final void setUnfilledDepVarIndexAccumulator(int[][] unfilledDepVarIndexAccumulator) {
    this.unfilledDepVarIndexAccumulator = unfilledDepVarIndexAccumulator;
  }

  @Override
  public final void setUnfilledDepAccumulator(long[][] unfilledDepAccumulator) {
    this.unfilledDepAccumulator = unfilledDepAccumulator;
  }
  
  @Override 
  public void setDepLongCache(long[] depCache) {
    this.depLongCache = depCache;
  }
  
  @Override
  public void setDepProbCache(double[] depProb) {
    this.depProbCache = depProb;
  }

  @Override
  public final int[][] getAssignmentVarIndexAccumulator() {
    return assignmentVarIndexAccumulator;
  }
  
  @Override
  public final long[][] getAssignmentAccumulator() {
    return assignmentAccumulator;
  }
  
  @Override
  public final long[][] getFilledDepAccumulator() {
    return filledDepAccumulator;
  }
  
  @Override
  public final int[][] getUnfilledDepVarIndexAccumulator() {
    return unfilledDepVarIndexAccumulator;
  }
  
  @Override
  public final long[][] getUnfilledDepAccumulator() {
    return unfilledDepAccumulator;
  }
  
  @Override
  public long[] getDepLongCache() {
    return depLongCache;
  }
  
  @Override
  public double[] getDepProbCache() {
    return depProbCache;
  }

  @Override
  public final boolean isFinishedParsing() {
    return finishedParsing;
  }

  @Override
  public final void setFinishedParsing(boolean finished) {
    this.finishedParsing = finished;
  }

  /**
   * Decodes the CCG parse which is the {@code beamIndex}'th parse in
   * the beam for the given span.
   * 
   * @param spanStart
   * @param spanEnd
   * @param beamIndex
   * @param parser
   * @return
   */
  public CcgParse decodeParseFromSpan(int spanStart, int spanEnd, int beamIndex, CcgParser parser) {
    DiscreteVariable syntaxVarType = parser.getSyntaxVarType();
    ChartEntry entry = getChartEntriesForSpan(spanStart, spanEnd)[beamIndex];
    HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) syntaxVarType.getValue(
        entry.getHeadedSyntax());

    if (entry.isTerminal()) {
      List<String> terminals = getWords();
      List<String> posTags = getPosTags();
      // rightSpanStart and rightSpanEnd are used to track the trigger span
      // in chart entries for terminals.
      LexiconEntryInfo lexiconEntryInfo = new LexiconEntryInfo(entry.getLexiconEntry(),
          entry.getLexiconTrigger(), entry.getLexiconIndex(), spanStart, spanEnd,
          entry.getRightSpanStart(), entry.getRightSpanEnd()); 
      
      return CcgParse.forTerminal(syntax, lexiconEntryInfo, posTags.subList(spanStart, spanEnd + 1),
          parser.variableToIndexedPredicateArray(syntax.getHeadVariable(), entry.getAssignments()),
          Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())),
          terminals.subList(spanStart, spanEnd + 1), getChartEntryProbsForSpan(spanStart, spanEnd)[beamIndex],
          entry.getRootUnaryRule(), spanStart, spanEnd);
    } else {
      CcgParse left = decodeParseFromSpan(entry.getLeftSpanStart(), entry.getLeftSpanEnd(),
          entry.getLeftChartIndex(), parser);
      CcgParse right = decodeParseFromSpan(entry.getRightSpanStart(), entry.getRightSpanEnd(),
          entry.getRightChartIndex(), parser);

      if (entry.getLeftUnaryRule() != null) {
        left = left.addUnaryRule(entry.getLeftUnaryRule(), (HeadedSyntacticCategory)
            syntaxVarType.getValue(entry.getLeftUnaryRule().getSyntax()));
      }
      if (entry.getRightUnaryRule() != null) {
        right = right.addUnaryRule(entry.getRightUnaryRule(), (HeadedSyntacticCategory)
            syntaxVarType.getValue(entry.getRightUnaryRule().getSyntax()));
      }

      double nodeProb = getChartEntryProbsForSpan(spanStart, spanEnd)[beamIndex] /
          (left.getSubtreeProbability() * right.getSubtreeProbability());

      return CcgParse.forNonterminal(syntax,
          parser.variableToIndexedPredicateArray(syntax.getHeadVariable(), entry.getAssignments()),
          Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())), nodeProb,
          left, right, entry.getCombinator(), entry.getRootUnaryRule(), spanStart, spanEnd);
    }
  }

  protected  IntMultimap aggregateBySyntacticType(ChartEntry[] entries, int numEntries) {
    int[] keys = new int[numEntries];
    int[] values = new int[numEntries];
    for (int i = 0; i < numEntries; i++) {
      keys[i] = entries[i].getHeadedSyntax();
      values[i] = i;
    }
    return IntMultimap.createFromUnsortedArrays(keys, values, 0);
  }
}
