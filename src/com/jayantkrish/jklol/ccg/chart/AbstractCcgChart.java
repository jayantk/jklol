package com.jayantkrish.jklol.ccg.chart;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IntMultimap;

/**
 * Common implementations of CCG parse chart methods.
 * 
 * @author jayantk
 */
public abstract class AbstractCcgChart implements CcgChart {

  // The words and pos tags of the sentence being parsed.
  private final List<String> terminals;
  private final List<String> posTags;
  private int[] posTagsInt;

  // Various kinds of distances between words in the sentence.
  private int[] wordDistances;
  private int[] puncDistances;
  private int[] verbDistances;

  protected ChartFilter entryFilter;

  // The parser weights which might be used in this sentence.
  // This is a subset of all parser weights, which is precomputed
  // to make lookups more efficient during parsing.
  private Tensor dependencyTensor;
  private Tensor wordDistanceTensor;
  private Tensor puncDistanceTensor;
  private Tensor verbDistanceTensor;

  // The syntactic category combinations that will be considered
  // while parsing this sentence.
  private DiscreteFactor syntaxDistribution;

  private int[] assignmentVarIndexAccumulator;
  private long[] assignmentAccumulator;
  private long[] filledDepAccumulator;
  private int[] unfilledDepVarIndexAccumulator;
  private long[] unfilledDepAccumulator;

  private boolean finishedParsing;

  public AbstractCcgChart(List<String> terminals, List<String> posTags) {
    this.terminals = ImmutableList.copyOf(terminals);
    this.posTags = ImmutableList.copyOf(posTags);

    // dependencyTensor, the distance arrays / tensors, and syntax distribution are
    // left null, and must be manually set.

    this.finishedParsing = false;
  }

  @Override
  public int size() {
    return terminals.size();
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
  public void setPosTagsInt(int[] posTagsInt) {
    Preconditions.checkArgument(posTagsInt.length == size());
    this.posTagsInt = posTagsInt;
  }

  @Override
  public void setWordDistances(int[] wordDistances) {
    Preconditions.checkArgument(wordDistances.length == size() * size());
    this.wordDistances = wordDistances;
  }

  @Override
  public void setPuncDistances(int[] puncDistances) {
    Preconditions.checkArgument(puncDistances.length == size() * size());
    this.puncDistances = puncDistances;
  }

  @Override
  public void setVerbDistances(int[] verbDistances) {
    Preconditions.checkArgument(verbDistances.length == size() * size());
    this.verbDistances = verbDistances;
  }

  @Override
  public void setChartFilter(ChartFilter entryFilter) {
    this.entryFilter = entryFilter;
  }

  @Override
  public int[] getPosTagsInt() {
    return posTagsInt;
  }

  @Override
  public int[] getWordDistances() {
    return wordDistances;
  }

  @Override
  public int[] getPunctuationDistances() {
    return puncDistances;
  }

  @Override
  public int[] getVerbDistances() {
    return verbDistances;
  }

  @Override
  public void setDependencyTensor(Tensor tensor) {
    this.dependencyTensor = tensor;
  }

  @Override
  public void setWordDistanceTensor(Tensor tensor) {
    this.wordDistanceTensor = tensor;
  }

  @Override
  public void setPuncDistanceTensor(Tensor tensor) {
    this.puncDistanceTensor = tensor;
  }

  @Override
  public void setVerbDistanceTensor(Tensor tensor) {
    this.verbDistanceTensor = tensor;
  }

  @Override
  public void setSyntaxDistribution(DiscreteFactor syntaxDistribution) {
    this.syntaxDistribution = syntaxDistribution;
  }

  @Override
  public void setAssignmentVarIndexAccumulator(int[] assignmentVarIndexAccumulator) {
    this.assignmentVarIndexAccumulator = assignmentVarIndexAccumulator;
  }

  @Override
  public void setAssignmentAccumulator(long[] assignmentAccumulator) {
    this.assignmentAccumulator = assignmentAccumulator;
  }

  @Override
  public void setFilledDepAccumulator(long[] filledDepAccumulator) {
    this.filledDepAccumulator = filledDepAccumulator;
  }

  @Override
  public void setUnfilledDepVarIndexAccumulator(int[] unfilledDepVarIndexAccumulator) {
    this.unfilledDepVarIndexAccumulator = unfilledDepVarIndexAccumulator;
  }

  @Override
  public void setUnfilledDepAccumulator(long[] unfilledDepAccumulator) {
    this.unfilledDepAccumulator = unfilledDepAccumulator;
  }

  @Override
  public Tensor getDependencyTensor() {
    return dependencyTensor;
  }

  @Override
  public Tensor getWordDistanceTensor() {
    return wordDistanceTensor;
  }

  @Override
  public Tensor getPuncDistanceTensor() {
    return puncDistanceTensor;
  }

  @Override
  public Tensor getVerbDistanceTensor() {
    return verbDistanceTensor;
  }

  @Override
  public DiscreteFactor getSyntaxDistribution() {
    return syntaxDistribution;
  }

  @Override
  public int[] getAssignmentVarIndexAccumulator() {
    return assignmentVarIndexAccumulator;
  }
  
  @Override
  public long[] getAssignmentAccumulator() {
    return assignmentAccumulator;
  }
  
  @Override
  public long[] getFilledDepAccumulator() {
    return filledDepAccumulator;
  }
  
  @Override
  public int[] getUnfilledDepVarIndexAccumulator() {
    return unfilledDepVarIndexAccumulator;
  }
  
  @Override
  public long[] getUnfilledDepAccumulator() {
    return unfilledDepAccumulator;
  }
  
  @Override
  public void applyChartFilterToTerminals() {
    if (entryFilter != null) {
      entryFilter.applyToTerminals(this);
    }
  }

  @Override
  public boolean isFinishedParsing() {
    return finishedParsing;
  }

  @Override
  public void setFinishedParsing(boolean finished) {
    this.finishedParsing = finished;
  }

  /**
   * Decodes the CCG parse which is the {@code beamIndex}'th parse in
   * the beam for the given span.
   * 
   * @param spanStart
   * @param spanEnd
   * @param beamIndex
   * @return
   */
  protected CcgParse decodeParseFromSpan(int spanStart, int spanEnd, int beamIndex, CcgParser parser) {
    DiscreteVariable syntaxVarType = parser.getSyntaxVarType();
    ChartEntry entry = getChartEntriesForSpan(spanStart, spanEnd)[beamIndex];
    HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) syntaxVarType.getValue(
        entry.getHeadedSyntax());

    if (entry.isTerminal()) {
      List<String> terminals = getWords();
      List<String> posTags = getPosTags();
      return CcgParse.forTerminal(syntax, entry.getLexiconEntry(), entry.getLexiconTriggerWords(), posTags.subList(spanStart, spanEnd + 1),
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
          Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())), nodeProb, left, right,
          entry.getCombinator(), entry.getRootUnaryRule(), spanStart, spanEnd);
    }
  }
  
  protected  IntMultimap aggregateBySyntacticType(ChartEntry[] entries, int numEntries) {
    IntMultimap map = IntMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      map.put(entries[i].getHeadedSyntax(), i);
    }
    return map;
  }
}
