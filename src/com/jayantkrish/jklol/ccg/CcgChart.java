package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.HeapUtils;

/**
 * Data structure for performing beam search inference with a CCG.
 * 
 * @author jayant
 */
public class CcgChart {

  private final List<String> terminals;
  private final List<String> posTags;

  private final int[] wordDistances;
  private final int[] puncDistances;
  private final int[] verbDistances;

  private final int beamSize;

  private final ChartEntry[][][] chart;
  private final double[][][] probabilities;
  private final int[] chartSizes;

  // The parser weights which might be used in this sentence.
  // This is a subset of all parser weights, which is precomputed
  // to make lookups more efficient during parsing.
  private Tensor dependencyTensor;
  private Tensor wordDistanceTensor;
  private Tensor puncDistanceTensor;
  private Tensor verbDistanceTensor;
  
  // Compilation of distance weights, indexed by word location in
  // the sentence.
  private Tensor wordIndexWeightTensor;

  // The syntactic category combinations that will be considered
  // while parsing this sentence.
  private DiscreteFactor syntaxDistribution;

  private final ChartFilter entryFilter;

  /**
   * Creates a CCG chart for storing the current state of a beam
   * search trying to parse {@code terminals}.
   * 
   * @param terminals
   * @param posTags
   * @param puncCount
   * @param verbCount
   * @param beamSize
   * @param entryFilter filter for discarding portions of the beam.
   * May be {@code null}, in which case all beam entries are retained.
   */
  public CcgChart(List<String> terminals, List<String> posTags, int[] wordDistances,
      int[] puncDistances, int[] verbDistances, int beamSize, ChartFilter entryFilter) {
    this.terminals = ImmutableList.copyOf(terminals);
    this.posTags = ImmutableList.copyOf(posTags);
    int n = terminals.size();

    this.wordDistances = wordDistances;
    this.puncDistances = puncDistances;
    this.verbDistances = verbDistances;

    Preconditions.checkArgument(posTags.size() == n);
    Preconditions.checkArgument(wordDistances.length == n * n);
    Preconditions.checkArgument(puncDistances.length == n * n);
    Preconditions.checkArgument(verbDistances.length == n * n);

    this.beamSize = beamSize;
    this.dependencyTensor = null;

    this.chart = new ChartEntry[n][n][beamSize + 1];
    this.probabilities = new double[n][n][beamSize + 1];
    this.chartSizes = new int[n * n];
    Arrays.fill(chartSizes, 0);

    this.entryFilter = entryFilter;
  }

  /**
   * Gets the number of terminals in this parse chart.
   * 
   * @return
   */
  public int size() {
    return terminals.size();
  }
  
  public List<String> getWords() {
    return terminals;
  }
  
  public List<String> getPosTags() {
    return posTags;
  }

  public int[] getWordDistances() {
    return wordDistances;
  }

  /**
   * Gets an array containing the distance (number of punctuation
   * marks) between every two terminal indexes.
   * 
   * @return
   */
  public int[] getPunctuationDistances() {
    return puncDistances;
  }

  public int[] getVerbDistances() {
    return verbDistances;
  }

  public int getBeamSize() {
    return beamSize;
  }

  /**
   * Caches the subset of the parser's semantic dependencies which may
   * get used during the parse of this sentence. This cache improves
   * the speed of weight lookups during parsing.
   * 
   * @param tensor
   */
  public void setDependencyTensor(Tensor tensor) {
    this.dependencyTensor = tensor;
  }

  public void setWordDistanceTensor(Tensor tensor) {
    this.wordDistanceTensor = tensor;
  }

  public void setPuncDistanceTensor(Tensor tensor) {
    this.puncDistanceTensor = tensor;
  }

  public void setVerbDistanceTensor(Tensor tensor) {
    this.verbDistanceTensor = tensor;
  }
  
  public void setWordIndexWeightTensor(Tensor tensor) {
    wordIndexWeightTensor = tensor;
  }

  public void setSyntaxDistribution(DiscreteFactor syntaxDistribution) {
    this.syntaxDistribution = syntaxDistribution;
  }

  /**
   * Gets the subset of all parser weights which may be used in this
   * parse.
   * 
   * @return
   */
  public Tensor getDependencyTensor() {
    return dependencyTensor;
  }

  public Tensor getWordDistanceTensor() {
    return wordDistanceTensor;
  }

  public Tensor getPuncDistanceTensor() {
    return puncDistanceTensor;
  }

  public Tensor getVerbDistanceTensor() {
    return verbDistanceTensor;
  }
  
  public Tensor getWordIndexWeightTensor() {
    return wordIndexWeightTensor;
  }

  public DiscreteFactor getSyntaxDistribution() {
    return syntaxDistribution;
  }

  public void applyChartFilterToTerminals() {
    if (entryFilter != null) {
      entryFilter.applyToTerminals(this);
    }
  }

  public List<CcgParse> decodeBestParsesForSpan(int spanStart, int spanEnd, int numParses,
      CcgParser parser, DiscreteVariable syntaxVarType) {
    // Perform a heap sort on the array indexes paired with the
    // probabilities.
    double[] probsCopy = ArrayUtils.copyOf(probabilities[spanStart][spanEnd], probabilities[spanStart][spanEnd].length);
    Integer[] chartEntryIndexes = new Integer[probabilities[spanStart][spanEnd].length];
    for (int i = 0; i < chartEntryIndexes.length; i++) {
      chartEntryIndexes[i] = i;
    }

    // Heaps are min-heaps, so we throw away the initial entries.
    // Then the remaining entries are the best parses.
    List<CcgParse> bestParses = Lists.newArrayList();
    int numChartEntries = getNumChartEntriesForSpan(spanStart, spanEnd);
    while (numChartEntries > 0) {
      if (numChartEntries <= numParses) {
        bestParses.add(decodeParseFromSpan(spanStart, spanEnd, chartEntryIndexes[0],
            parser, syntaxVarType));
      }

      HeapUtils.removeMin(chartEntryIndexes, probsCopy, numChartEntries);
      numChartEntries--;
    }

    Collections.reverse(bestParses);
    return bestParses;
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
  private CcgParse decodeParseFromSpan(int spanStart, int spanEnd, int beamIndex,
      CcgParser parser, DiscreteVariable syntaxVarType) {
    ChartEntry entry = chart[spanStart][spanEnd][beamIndex];

    // System.out.println(spanStart + "." + spanEnd + "." + beamIndex
    // + "   " + entry) ;
    HeadedSyntacticCategory syntax = (HeadedSyntacticCategory) syntaxVarType.getValue(
        entry.getHeadedSyntax());

    if (entry.isTerminal()) {
      return CcgParse.forTerminal(syntax, entry.getLexiconEntry(), entry.getLexiconTriggerWords(), posTags.subList(spanStart, spanEnd + 1),
          parser.variableToIndexedPredicateArray(syntax.getRootVariable(),
              entry.getAssignmentVariableNums(), entry.getAssignmentPredicateNums(), entry.getAssignmentIndexes()),
          Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())),
          terminals.subList(spanStart, spanEnd + 1), probabilities[spanStart][spanEnd][beamIndex],
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

      double nodeProb = probabilities[spanStart][spanEnd][beamIndex] /
          (left.getSubtreeProbability() * right.getSubtreeProbability());

      return CcgParse.forNonterminal(syntax,
          parser.variableToIndexedPredicateArray(syntax.getRootVariable(),
              entry.getAssignmentVariableNums(), entry.getAssignmentPredicateNums(), entry.getAssignmentIndexes()),
          Arrays.asList(parser.longArrayToFilledDependencyArray(entry.getDependencies())), nodeProb, left, right,
          entry.getCombinator(), entry.getRootUnaryRule(), spanStart, spanEnd);
    }
  }

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
  public ChartEntry[] getChartEntriesForSpan(int spanStart, int spanEnd) {
    return chart[spanStart][spanEnd];
  }

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
  public double[] getChartEntryProbsForSpan(int spanStart, int spanEnd) {
    return probabilities[spanStart][spanEnd];
  }

  /**
   * Gets the number of chart entries spanning {@code spanStart}-
   * {@code spanEnd} , inclusive. This is the number of valid entries
   * in {@link #getChartEntriesForSpan}.
   * 
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public int getNumChartEntriesForSpan(int spanStart, int spanEnd) {
    return chartSizes[spanEnd + (terminals.size() * spanStart)];
  }

  /**
   * Adds {@code entry} as a chart entry spanning {@code spanStart}-
   * {@code spanEnd}, with probability {@code probability}.
   * 
   * @param entry
   * @param probability
   * @param spanStart
   * @param spanEnd
   */
  public void addChartEntryForSpan(ChartEntry entry, double probability, int spanStart,
      int spanEnd, DiscreteVariable syntaxVarType) {
    if (probability != 0.0 && (entryFilter == null || entryFilter.apply(entry, spanStart, spanEnd, syntaxVarType))) {
      offerEntry(entry, probability, spanStart, spanEnd);
    }
  }

  /**
   * Deletes all chart entries spanning {@code spanStart} to
   * {@code spanEnd}.
   * 
   * @param spanStart
   * @param spanEnd
   */
  public void clearChartEntriesForSpan(int spanStart, int spanEnd) {
    chartSizes[spanEnd + (terminals.size() * spanStart)] = 0;
    // This second part is unnecessary, but makes debugging easier.
    Arrays.fill(chart[spanStart][spanEnd], null);
  }

  /**
   * Adds a chart entry to the heap for {@code spanStart} to
   * {@code spanEnd}. This operation implements beam truncation by
   * discarding the minimum probability entry when a heap reaches the
   * beam size.
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

  public static ChartEntry[] copyChartEntryArray(ChartEntry[] entries, int numEntries) {
    ChartEntry[] returnValue = new ChartEntry[numEntries];
    for (int i = 0; i < numEntries; i++) {
      returnValue[i] = entries[i];
    }
    return returnValue;
  }

  /**
   * An entry of the beam search chart, containing both a syntactic
   * and semantic type. The semantic type consists of yet-unfilled
   * semantic dependencies.
   * <p>
   * Chart entries also include any filled dependencies instantiated
   * during the parsing operation that produced the entry. Finally,
   * chart entries include backpointers to the chart entries used to
   * create them. These backpointers allow CCG parses to be
   * reconstructed from the chart.
   * 
   * @author jayant
   */
  public static class ChartEntry {
    // The syntactic category of the root of the parse span,
    // encoded as an integer.
    private final int syntax;
    private final int[] syntaxUniqueVars;

    // If non-null, this unary rule was applied at this entry to
    // produce syntax from the original category.
    private final UnaryCombinator rootUnaryRule;

    // If non-null, these rules were applied to the left / right
    // chart entries before the binary rule that produced this entry.
    private final UnaryCombinator leftUnaryRule;
    private final UnaryCombinator rightUnaryRule;

    // An assignment to the semantic variables given by syntax.
    // Each value is both a predicate and its index in the sentence.
    private final int[] assignmentVariableNums;
    private final int[] assignmentPredicateNums;
    private final int[] assignmentIndexes;

    // Partially complete dependency structures, encoded into longs
    // for efficiency.
    private final long[] unfilledDependencies;
    // Complete dependency structures, encoded into longs for
    // efficiency.
    private final long[] deps;

    // If this is a terminal, lexiconEntry contains the CcgCategory
    // from the lexicon used to create this chartEntry. This variable
    // is saved to track which lexicon entries are used in a parse,
    // for parameter estimation purposes.
    private final CcgCategory lexiconEntry;
    // If this is a terminal, this contains the words used to trigger
    // the category. This may be different from the words in the
    // sentence,
    // if the original words were not part of the lexicon.
    private final List<String> lexiconTriggerWords;

    // Backpointer information
    private final int leftSpanStart;
    private final int leftSpanEnd;
    private final int leftChartIndex;

    private final int rightSpanStart;
    private final int rightSpanEnd;
    private final int rightChartIndex;

    private final Combinator combinator;

    public ChartEntry(int syntax, int[] syntaxUniqueVars, UnaryCombinator rootUnaryRule, UnaryCombinator leftUnaryRule,
        UnaryCombinator rightUnaryRule, int[] assignmentVariableNums, int[] assignmentPredicateNums,
        int[] assignmentIndexes, long[] unfilledDependencies,
        long[] deps, int leftSpanStart, int leftSpanEnd, int leftChartIndex,
        int rightSpanStart, int rightSpanEnd, int rightChartIndex, Combinator combinator) {
      this.syntax = syntax;
      this.syntaxUniqueVars = syntaxUniqueVars;

      this.rootUnaryRule = rootUnaryRule;
      this.leftUnaryRule = leftUnaryRule;
      this.rightUnaryRule = rightUnaryRule;

      this.assignmentVariableNums = Preconditions.checkNotNull(assignmentVariableNums);
      this.assignmentPredicateNums = Preconditions.checkNotNull(assignmentPredicateNums);
      this.assignmentIndexes = Preconditions.checkNotNull(assignmentIndexes);
      this.unfilledDependencies = Preconditions.checkNotNull(unfilledDependencies);

      this.lexiconEntry = null;
      this.lexiconTriggerWords = null;
      this.deps = Preconditions.checkNotNull(deps);

      this.leftSpanStart = leftSpanStart;
      this.leftSpanEnd = leftSpanEnd;
      this.leftChartIndex = leftChartIndex;

      this.rightSpanStart = rightSpanStart;
      this.rightSpanEnd = rightSpanEnd;
      this.rightChartIndex = rightChartIndex;

      this.combinator = combinator;
    }

    public ChartEntry(int syntax, int[] syntaxUniqueVars, CcgCategory ccgCategory, List<String> terminalWords,
        UnaryCombinator rootUnaryRule, int[] assignmentVariableNums, int[] assignmentPredicateNums,
        int[] assignmentIndexes, long[] unfilledDependencies, long[] deps, int spanStart, int spanEnd) {
      this.syntax = syntax;
      this.syntaxUniqueVars = syntaxUniqueVars;

      this.rootUnaryRule = rootUnaryRule;
      this.leftUnaryRule = null;
      this.rightUnaryRule = null;

      this.assignmentVariableNums = Preconditions.checkNotNull(assignmentVariableNums);
      this.assignmentPredicateNums = Preconditions.checkNotNull(assignmentPredicateNums);
      this.assignmentIndexes = Preconditions.checkNotNull(assignmentIndexes);
      this.unfilledDependencies = Preconditions.checkNotNull(unfilledDependencies);

      this.lexiconEntry = ccgCategory;
      this.lexiconTriggerWords = terminalWords;
      this.deps = Preconditions.checkNotNull(deps);

      // Use the leftSpan to represent the spanned terminal.
      this.leftSpanStart = spanStart;
      this.leftSpanEnd = spanEnd;
      this.leftChartIndex = -1;

      this.rightSpanStart = -1;
      this.rightSpanEnd = -1;
      this.rightChartIndex = -1;

      this.combinator = null;
    }

    public int getHeadedSyntax() {
      return syntax;
    }

    public int[] getHeadedSyntaxUniqueVars() {
      return syntaxUniqueVars;
    }

    /**
     * Gets the unary rule used to produce this chart entry. If no
     * rule was used, returns {@code null}.
     * 
     * @return
     */
    public UnaryCombinator getRootUnaryRule() {
      return rootUnaryRule;
    }

    public UnaryCombinator getLeftUnaryRule() {
      return leftUnaryRule;
    }

    public UnaryCombinator getRightUnaryRule() {
      return rightUnaryRule;
    }

    public int[] getAssignmentVariableNums() {
      return assignmentVariableNums;
    }

    /**
     * Replaces the {@code i}th unique variable in {@code this} with
     * the {@code i}th variable in {@code relabeling}.
     * 
     * @param relabeling
     * @return
     */
    public int[] getAssignmentVariableNumsRelabeled(int[] relabeling) {
      int[] uniqueVars = syntaxUniqueVars;
      int[] relabeledAssignmentVariableNums = new int[assignmentVariableNums.length];
      Arrays.fill(relabeledAssignmentVariableNums, -1);
      for (int i = 0; i < assignmentVariableNums.length; i++) {
        for (int j = 0; j < uniqueVars.length; j++) {
          if (uniqueVars[j] == assignmentVariableNums[i]) {
            relabeledAssignmentVariableNums[i] = relabeling[j];
          }
        }
      }

      return relabeledAssignmentVariableNums;
    }

    public int[] getAssignmentPredicateNums() {
      return assignmentPredicateNums;
    }

    public int[] getAssignmentIndexes() {
      return assignmentIndexes;
    }

    public long[] getUnfilledDependencies() {
      return unfilledDependencies;
    }

    public long[] getUnfilledDependenciesRelabeled(int[] relabeling) {
      int[] uniqueVars = syntaxUniqueVars;
      long[] relabeledUnfilledDependencies = new long[unfilledDependencies.length];
      for (int i = 0; i < unfilledDependencies.length; i++) {
        long unfilledDependency = unfilledDependencies[i];
        int objectVarNum = CcgParser.getObjectArgNumFromDep(unfilledDependency);
        int j;
        for (j = 0; j < uniqueVars.length; j++) {
          if (uniqueVars[j] == objectVarNum) {
            unfilledDependency -= CcgParser.marshalUnfilledDependency(objectVarNum, 0, 0, 0, 0);
            unfilledDependency += CcgParser.marshalUnfilledDependency(relabeling[j], 0, 0, 0, 0);
            relabeledUnfilledDependencies[i] = unfilledDependency;
            break;
          }
        }

        Preconditions.checkState(j != uniqueVars.length, "No relabeling %s %s %s", syntax, i, objectVarNum);
      }

      return relabeledUnfilledDependencies;
    }

    public CcgCategory getLexiconEntry() {
      return lexiconEntry;
    }

    public List<String> getLexiconTriggerWords() {
      return lexiconTriggerWords;
    }

    public long[] getDependencies() {
      return deps;
    }

    public boolean isTerminal() {
      return rightChartIndex == -1;
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

    public Combinator getCombinator() {
      return combinator;
    }

    public ChartEntry applyUnaryRule(int resultSyntax, int[] resultUniqueVars,
        UnaryCombinator unaryRuleCombinator, int[] newVars, int[] newPredicates,
        int[] newIndexes, long[] newUnfilledDeps, long[] newFilledDeps) {
      Preconditions.checkState(rootUnaryRule == null);
      if (isTerminal()) {
        return new ChartEntry(resultSyntax, resultUniqueVars, lexiconEntry, lexiconTriggerWords,
            unaryRuleCombinator, newVars, newPredicates, newIndexes, newUnfilledDeps,
            newFilledDeps, leftSpanStart, leftSpanEnd);
      } else {
        return new ChartEntry(resultSyntax, resultUniqueVars, unaryRuleCombinator, leftUnaryRule, rightUnaryRule,
            newVars, newPredicates, newIndexes, newUnfilledDeps, newFilledDeps, leftSpanStart,
            leftSpanEnd, leftChartIndex, rightSpanStart, rightSpanEnd, rightChartIndex, combinator);
      }
    }

    @Override
    public String toString() {
      return "[" + Arrays.toString(assignmentPredicateNums) + ":" + syntax
          + " " + Arrays.toString(deps) + " " + Arrays.toString(unfilledDependencies) + "]";
    }
  }

  /**
   * Filter for discarding portions of the CCG beam. Chart entries for
   * which {@code apply} returns {@code false} are discarded.
   * 
   * @author jayantk
   */
  public static interface ChartFilter {

    /**
     * Returns {@code true} if {@code entry} is a valid chart entry
     * for the indicated span. If this method returns {@code false},
     * the given entry will be discarded (i.e., not included in the
     * search).
     * 
     * @param entry
     * @param spanStart
     * @param spanEnd
     * @param syntaxVarType
     * @return
     */
    public boolean apply(ChartEntry entry, int spanStart, int spanEnd,
        DiscreteVariable syntaxVarType);

    /**
     * Updates {@code chart} based on the set of possible terminals.
     * This method can be used to restrict the CCG search space based
     * on the words which actually appear in a sentence.
     * <p>
     * When this method is called, {@code chart} is initialized with
     * the terminal symbols (i.e., the words) and the possible lexicon
     * entries for each word.
     * 
     * @param chart
     */
    public void applyToTerminals(CcgChart chart);
  }
}
