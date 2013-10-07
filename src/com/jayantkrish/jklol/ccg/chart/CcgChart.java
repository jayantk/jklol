package com.jayantkrish.jklol.ccg.chart;

import java.util.List;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.tensor.Tensor;

public interface CcgChart {

  /**
   * Gets the number of terminals in this parse chart.
   * 
   * @return
   */
  public int size();

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

  /**
   * Caches the subset of the parser's semantic dependencies which may
   * get used during the parse of this sentence. This cache improves
   * the speed of weight lookups during parsing.
   * 
   * @param tensor
   */
  public void setDependencyTensor(Tensor tensor);

  public void setWordDistanceTensor(Tensor tensor);

  public void setPuncDistanceTensor(Tensor tensor);

  public void setVerbDistanceTensor(Tensor tensor);

  public void setSyntaxDistribution(DiscreteFactor syntaxDistribution);

  /**
   * Gets the subset of all parser weights which may be used in this
   * parse.
   * 
   * @return
   */
  public Tensor getDependencyTensor();

  public Tensor getWordDistanceTensor();

  public Tensor getPuncDistanceTensor();

  public Tensor getVerbDistanceTensor();

  public DiscreteFactor getSyntaxDistribution();

  public void applyChartFilterToTerminals();

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
}