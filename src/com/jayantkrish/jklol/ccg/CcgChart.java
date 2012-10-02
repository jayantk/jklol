package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCategory.Argument;
import com.jayantkrish.jklol.util.HeapUtils;

/**
 * Data structure for performing beam search inference with a CCG.
 * 
 * @author jayant
 */
public class CcgChart {

  private final List<String> terminals;
  private final int beamSize;

  private final ChartEntry[][][] chart;
  private final double[][][] probabilities;
  private final int[] chartSizes;

  public CcgChart(List<String> terminals, int beamSize) {
    this.terminals = ImmutableList.copyOf(terminals);
    this.beamSize = beamSize;

    int n = terminals.size();
    this.chart = new ChartEntry[n][n][beamSize + 1];
    this.probabilities = new double[n][n][beamSize + 1];
    this.chartSizes = new int[n * n];
    Arrays.fill(chartSizes, 0);
  }

  /**
   * Gets the number of terminals in this parse chart.
   * 
   * @return
   */
  public int size() {
    return terminals.size();
  }

  /**
   * Decodes the CCG parse which is the {@code beamIndex}'th best parse in 
   * the beam for the given span. 
   * 
   * @param spanStart
   * @param spanEnd
   * @param beamIndex
   * @return
   */
  public CcgParse decodeParseFromSpan(int spanStart, int spanEnd, int beamIndex) {
    ChartEntry entry = chart[spanStart][spanEnd][beamIndex];

    if (entry.isTerminal()) {
      return CcgParse.forTerminal(entry.getLexiconEntry(), entry.getHeads(), entry.getDependencies(), 
          terminals.subList(spanStart, spanEnd + 1), probabilities[spanStart][spanEnd][beamIndex]);
    } else {
      CcgParse left = decodeParseFromSpan(entry.getLeftSpanStart(), entry.getLeftSpanEnd(), entry.getLeftChartIndex());
      CcgParse right = decodeParseFromSpan(entry.getRightSpanStart(), entry.getRightSpanEnd(), entry.getRightChartIndex());

      double nodeProb = probabilities[spanStart][spanEnd][beamIndex] / (left.getSubtreeProbability() * right.getSubtreeProbability());
      
      return CcgParse.forNonterminal(entry.getSyntax(), entry.getHeads(), entry.getDependencies(), nodeProb, left, right);
    }
  }

  /**
   * Gets the chart entries spanning the words {@code spanStart}-{@code spanEnd}, 
   * inclusive. Some entries of the returned array may not be valid 
   * {@code ChartEntry}s. Use {@link #getNumChartEntriesForSpan(int, int)} to determine
   * how many chart entries exist for a given span.
   *  
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public ChartEntry[] getChartEntriesForSpan(int spanStart, int spanEnd) {
    return chart[spanStart][spanEnd];
  }

  /**
   * Gets the probabilities of each chart entry spanning {@code spanStart}-{@code spanEnd}, 
   * inclusive. The indexes of the returned array match the indexes of the ChartEntry[] 
   * returned by {@link #getChartEntriesForSpan}.
   * 
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public double[] getChartEntryProbsForSpan(int spanStart, int spanEnd) {
    return probabilities[spanStart][spanEnd];
  }

  /**
   * Gets the number of chart entries spanning {@code spanStart}-{@code spanEnd}, 
   * inclusive. This is the number of valid entries in  
   *  
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public int getNumChartEntriesForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanEnd + (terminals.size() * spanStart)];
  }

  /**
   * Adds {@code entry} as a chart entry spanning {@code spanStart}-{@code spanEnd},
   * with probability {@code probability}.
   * 
   * @param entry
   * @param probability
   * @param spanStart
   * @param spanEnd
   */
  public void addChartEntryForSpan(ChartEntry entry, double probability, int spanStart, int spanEnd) { 
    offerEntry(entry, probability, spanStart, spanEnd);
  }

  public void addChartEntryForTerminalSpan(CcgCategory result, double probability, 
      int spanStart, int spanEnd) {
    // Assign each predicate in this category a unique word index.
    Set<IndexedPredicate> heads = Sets.newHashSet();
    Set<Integer> headArgumentNumbers = Sets.newHashSet();
    for (Argument head : result.getHeads()) {
      if (head.hasPredicate()) {
        heads.add(new IndexedPredicate(head.getPredicate(), spanEnd));
      } else {
        headArgumentNumbers.add(head.getArgumentNumber());
      }
    }
    List<DependencyStructure> deps = Lists.newArrayList();
    Multimap<Integer, UnfilledDependency> unfilledDeps = result.createUnfilledDependencies(spanEnd, deps);
    
    ChartEntry entry = new ChartEntry(result, heads, headArgumentNumbers,
        unfilledDeps, deps, spanStart, spanEnd);
    
    offerEntry(entry, probability, spanStart, spanEnd);
  }

  /**
   * Adds a chart entry to the heap for {@code spanStart} to {@code spanEnd}. 
   * This operation implements beam truncation by discarding the minimum 
   * probability entry when a heap reaches the beam size. 
   */
  private final void offerEntry(ChartEntry entry, double probability, int spanStart, int spanEnd) {
    HeapUtils.offer(chart[spanStart][spanEnd], probabilities[spanStart][spanEnd],
        chartSizes[spanEnd + (terminals.size() * spanStart)], entry, probability);
    chartSizes[spanEnd + (terminals.size() * spanStart)]++;
    
    if (chartSizes[spanEnd + (terminals.size() * spanStart)] > beamSize) {
      HeapUtils.removeMin(chart[spanStart][spanEnd], probabilities[spanStart][spanEnd], 
          chartSizes[spanEnd + (terminals.size() * spanStart)]);
      chartSizes[spanEnd + (terminals.size() * spanStart)]--;
    }
  }

  /**
   * An entry of the beam search chart, containing both a syntactic and 
   * semantic type. The semantic type consists of yet-unfilled semantic 
   * dependencies.
   * <p>
   * Chart entries also include any filled dependencies instantiated during
   * the parsing operation that produced the entry. Finally, chart entries
   * include backpointers to the chart entries used to create them. These
   * backpointers allow CCG parses to be reconstructed from the chart.
   * 
   * @author jayant
   */
  public static class ChartEntry {
    private final SyntacticCategory syntax;
    private final Set<IndexedPredicate> heads;
    private final Set<Integer> headArguments;
    private final Multimap<Integer, UnfilledDependency> unfilledDependencies;
    
    private final List<DependencyStructure> deps;

    private final boolean isTerminal;

    // If this is a terminal, lexiconEntry contains the CcgCategory 
    // from the lexicon used to create this chartEntry. This variable
    // is saved to track which lexicon entries are used in a parse,
    // for parameter estimation purposes.
    private final CcgCategory lexiconEntry;
        
    // Backpointer information 
    private final int leftSpanStart;
    private final int leftSpanEnd;
    private final int leftChartIndex;

    private final int rightSpanStart;
    private final int rightSpanEnd;
    private final int rightChartIndex;

    public ChartEntry(SyntacticCategory syntax, Set<IndexedPredicate> heads, Set<Integer> headArguments,
        Multimap<Integer, UnfilledDependency> unfilledDependencies, List<DependencyStructure> deps,
        int leftSpanStart, int leftSpanEnd, int leftChartIndex,
        int rightSpanStart, int rightSpanEnd, int rightChartIndex) {
      this.syntax = Preconditions.checkNotNull(syntax);
      this.heads = Preconditions.checkNotNull(heads);
      this.headArguments = Preconditions.checkNotNull(headArguments);
      this.unfilledDependencies = Preconditions.checkNotNull(unfilledDependencies);

      this.lexiconEntry = null;
      this.deps = Preconditions.checkNotNull(deps);

      isTerminal = false;

      this.leftSpanStart = leftSpanStart;
      this.leftSpanEnd = leftSpanEnd;
      this.leftChartIndex = leftChartIndex;

      this.rightSpanStart = rightSpanStart;
      this.rightSpanEnd = rightSpanEnd;
      this.rightChartIndex = rightChartIndex;
    }

    /**
     * Create a chart entry for a terminal (lexicon entry) in the parse tree.
     * 
     * @param lexiconEntry
     * @param heads
     * @param headArguments
     * @param unfilledDependencies
     * @param deps
     * @param spanStart
     * @param spanEnd
     */
    public ChartEntry(CcgCategory lexiconEntry, Set<IndexedPredicate> heads, 
        Set<Integer> headArguments, Multimap<Integer, UnfilledDependency> unfilledDependencies, 
        List<DependencyStructure> deps, int spanStart, int spanEnd) {
      this.syntax = Preconditions.checkNotNull(lexiconEntry.getSyntax());
      this.heads = Preconditions.checkNotNull(heads);
      this.headArguments = Preconditions.checkNotNull(headArguments);
      this.unfilledDependencies = Preconditions.checkNotNull(unfilledDependencies);
      
      this.lexiconEntry = lexiconEntry;  
      this.deps = ImmutableList.copyOf(deps);

      isTerminal = true;

      // Use the leftSpan to represent the spanned terminal.
      this.leftSpanStart = spanStart;
      this.leftSpanEnd = spanEnd;
      this.leftChartIndex = -1;
      
      this.rightSpanStart = -1;
      this.rightSpanEnd = -1;
      this.rightChartIndex = -1;
    }

    public SyntacticCategory getSyntax() {
      return syntax;
    }
    
    public Set<IndexedPredicate> getHeads() {
      return heads;
    }
    
    public Set<Integer> getUnfilledHeads() {
      return headArguments;
    }
    
    public Multimap<Integer, UnfilledDependency> getUnfilledDependencies() {
      return unfilledDependencies;
    }
    
    public CcgCategory getLexiconEntry() {
      return lexiconEntry;
    }
    
    public List<DependencyStructure> getDependencies() {
      return deps;
    }

    public boolean isTerminal() {
      return isTerminal;
    }

    public int getLeftSpanStart() {
      return leftSpanStart;
    }

    public int getLeftSpanEnd() {
      return leftSpanEnd;
    }

    public int getLeftChartIndex() {
      return leftChartIndex;
    }

    public int getRightSpanStart() {
      return rightSpanStart;
    }

    public int getRightSpanEnd() {
      return rightSpanEnd;
    }

    public int getRightChartIndex() {
      return rightChartIndex;
    }
  }
  
  /**
   * A semantic predicate paired with the index of the word that instantiated it.
   * This class disambiguates between multiple distinct instantiations of a
   * predicate in a sentence. Such instantiations occur, for example, when 
   * a single word occurs multiple times in the sentence.
   *   
   * @author jayant
   */
  public static class IndexedPredicate {
    // The name of the predicate.
    private final String predicate;
    
    // The word index that created predicate.
    private final int wordIndex;
    
    public IndexedPredicate(String head, int wordIndex) {
      this.predicate = head;
      this.wordIndex = wordIndex;
    }

    public String getHead() {
      return predicate;
    }
    
    public int getHeadIndex() {
      return wordIndex;
    }
    
    @Override
    public String toString() {
      return predicate + ":" + wordIndex;
    }
  }
}