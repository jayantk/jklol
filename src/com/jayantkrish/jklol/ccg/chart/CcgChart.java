package com.jayantkrish.jklol.ccg.chart;

import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.IntMultimap;

public interface CcgChart {

  /**
   * Gets the number of terminals in this parse chart.
   * 
   * @return
   */
  public int size();

  /**
   * Gets the input to which the parser was applied.
   * 
   * @return
   */
  public AnnotatedSentence getInput();

  /**
   * Gets the maximum number of chart entries for this parse. This
   * number acts as a constraint on chart parsing: the parser stops
   * after this number of chart entries has been exceeded. 
   *
   * @return
   */
  public int getMaxChartEntries();

  /**
   * Gets the words being parsed.
   * 
   * @return
   */
  public List<String> getWords();

  /**
   * Gets the POS tag of each word being parsed.
   * 
   * @return
   */
  public List<String> getPosTags();
  
  public void setPosTagsInt(int[] posTagsInt);
  
  public void setWordDistances(int[] wordDistances);
  
  public void setPuncDistances(int[] puncDistances);
  
  public void setVerbDistances(int[] verbDistances);

  public void setChartCost(ChartCost chartCost);

  /**
   * Gets the POS tag of each word being parsed encoded as an integer.
   * 
   * @return
   */
  public int[] getPosTagsInt();

  public int[] getWordDistances();

  /**
   * Gets an array containing the distance (number of punctuation
   * marks) between every two terminal indexes.
   * 
   * @return
   */
  public int[] getPunctuationDistances();

  public int[] getVerbDistances();

  public void setAssignmentVarIndexAccumulator(int[][] assignmentVarIndexAccumulator);
  
  public void setAssignmentAccumulator(long[][] assignmentAccumulator);
  
  public void setFilledDepAccumulator(long[][] filledDepAccumulator);
  
  public void setUnfilledDepVarIndexAccumulator(int[][] unfilledDepVarIndexAccumulator);
  
  public void setUnfilledDepAccumulator(long[][] unfilledDepAccumulator);
  
  public void setDepLongCache(long[] depCache);
  
  public void setDepProbCache(double[] depProb);

  public int[][] getAssignmentVarIndexAccumulator();
  
  public long[][] getAssignmentAccumulator();
  
  public long[][] getFilledDepAccumulator();
  
  public int[][] getUnfilledDepVarIndexAccumulator();
  
  public long[][] getUnfilledDepAccumulator();
  
  public long[] getDepLongCache();
  
  public double[] getDepProbCache();

  /**
   * Gets the chart entries spanning the words {@code spanStart}-
   * {@code spanEnd} , inclusive. Some entries of the returned array
   * may not be valid {@code ChartEntry}s. Use
   * {@link #getNumChartEntriesForSpan(int, int)} to determine how
   * many chart entries exist for a given span.
   * 
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public ChartEntry[] getChartEntriesForSpan(int spanStart, int spanEnd);

  /**
   * Gets the probabilities of each chart entry spanning
   * {@code spanStart}- {@code spanEnd}, inclusive. The indexes of the
   * returned array match the indexes of the ChartEntry[] returned by
   * {@link #getChartEntriesForSpan}.
   * 
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public double[] getChartEntryProbsForSpan(int spanStart, int spanEnd);

  /**
   * Gets the number of chart entries spanning {@code spanStart}-
   * {@code spanEnd} , inclusive. This is the number of valid entries
   * in {@link #getChartEntriesForSpan}.
   * 
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public int getNumChartEntriesForSpan(int spanStart, int spanEnd);
  
  public IntMultimap getChartEntriesBySyntacticCategoryForSpan(int spanStart, int spanEnd);

  /**
   * Gets the total number of entries in this parse chart, which is the
   * sum of the number of entries for all spans.
   * 
   * @return
   */
  public int getTotalNumChartEntries();

  /**
   * Adds {@code entry} as a chart entry spanning {@code spanStart}-
   * {@code spanEnd}, with probability {@code probability}. Note that
   * {@link #doneAddingChartEntriesForSpan} must be invoked after
   * adding entries to a span but before reading those same entries.
   * 
   * @param entry
   * @param probability
   * @param spanStart
   * @param spanEnd
   */
  public void addChartEntryForSpan(ChartEntry entry, double probability, int spanStart,
      int spanEnd, DiscreteVariable syntaxVarType);

  /**
   * Does clean-up work for the given span after entries have been
   * added. This method must be called after adding entries to a span
   * before reading those entries. Calling this method multiple times
   * after adding entries (for a given span) has no harmful effects.
   * 
   * @param spanStart
   * @param spanEnd
   */
  public void doneAddingChartEntriesForSpan(int spanStart, int spanEnd);

  /**
   * Deletes all chart entries spanning {@code spanStart} to
   * {@code spanEnd}.
   * 
   * @param spanStart
   * @param spanEnd
   */
  public void clearChartEntriesForSpan(int spanStart, int spanEnd);
  
  /**
   * Retrieves the highest-scoring parse from the parse chart. Returns
   * {@code null} if no complete parse was found.
   * 
   * @param parser Parser that produced this parse chart.
   * @return
   */
  public CcgParse decodeBestParse(CcgParser parser);
  
  public boolean isFinishedParsing();
  
  public void setFinishedParsing(boolean finished);
}