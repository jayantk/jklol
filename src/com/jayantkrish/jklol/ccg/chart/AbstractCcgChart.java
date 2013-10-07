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

/**
 * Common implementations of CCG parse chart methods.
 * 
 * @author jayantk
 */
public abstract class AbstractCcgChart implements CcgChart {
  
  // The words and pos tags of the sentence being parsed.
  private final List<String> terminals;
  private final List<String> posTags;
  private final int[] posTagsInt;

  // Various kinds of distances between words in the sentence.
  private final int[] wordDistances;
  private final int[] puncDistances;
  private final int[] verbDistances;
  
  private final ChartFilter entryFilter;
  
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
  
  public AbstractCcgChart(List<String> terminals, List<String> posTags, int[] posTagsInt,
      int[] wordDistances, int[] puncDistances, int[] verbDistances, ChartFilter entryFilter) {
    this.terminals = ImmutableList.copyOf(terminals);
    this.posTags = ImmutableList.copyOf(posTags);
    this.posTagsInt = Preconditions.checkNotNull(posTagsInt);
    
    this.wordDistances = Preconditions.checkNotNull(wordDistances);
    this.puncDistances = Preconditions.checkNotNull(puncDistances);
    this.verbDistances = Preconditions.checkNotNull(verbDistances);
    
    int n = terminals.size();
    Preconditions.checkArgument(posTags.size() == n);
    Preconditions.checkArgument(wordDistances.length == n * n);
    Preconditions.checkArgument(puncDistances.length == n * n);
    Preconditions.checkArgument(verbDistances.length == n * n);
    
    this.entryFilter = entryFilter;

    // dependencyTensor, the distance tensors, and syntax distribution are
    // left null, and must be manually set.
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
  public void applyChartFilterToTerminals() {
    if (entryFilter != null) {
      entryFilter.applyToTerminals(this);
    }
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
  protected CcgParse decodeParseFromSpan(int spanStart, int spanEnd, int beamIndex,
      CcgParser parser, DiscreteVariable syntaxVarType) {
    ChartEntry entry = getChartEntriesForSpan(spanStart, spanEnd)[beamIndex];

    HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) syntaxVarType.getValue(
        entry.getHeadedSyntax());

    if (entry.isTerminal()) {
      List<String> terminals = getWords();
      List<String> posTags = getPosTags();
      return CcgParse.forTerminal(syntax, entry.getLexiconEntry(), entry.getLexiconTriggerWords(), posTags.subList(spanStart, spanEnd + 1),
          parser.variableToIndexedPredicateArray(syntax.getRootVariable(), entry.getAssignments()),
              Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())),
              terminals.subList(spanStart, spanEnd + 1), getChartEntryProbsForSpan(spanStart, spanEnd)[beamIndex],
              entry.getRootUnaryRule(), spanStart, spanEnd);
    } else {
      CcgParse left = decodeParseFromSpan(entry.getLeftSpanStart(), entry.getLeftSpanEnd(),
          entry.getLeftChartIndex(), parser, syntaxVarType);
      CcgParse right = decodeParseFromSpan(entry.getRightSpanStart(), entry.getRightSpanEnd(),
          entry.getRightChartIndex(), parser, syntaxVarType);

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
          parser.variableToIndexedPredicateArray(syntax.getRootVariable(), entry.getAssignments()),
              Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())), nodeProb, left, right,
              entry.getCombinator(), entry.getRootUnaryRule(), spanStart, spanEnd);
    }
  }
}
