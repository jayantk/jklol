package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A chart parser for Combinatory Categorial Grammar (CCG).
 * 
 * @author jayantk
 */
public class CcgParser implements Serializable {

  private static final long serialVersionUID = 1L;

  // Parameters for encoding (filled and unfilled) dependency
  // structures
  // in longs. These are the size of each field, in bits.
  private static final int PREDICATE_BITS = 20;
  private static final long PREDICATE_MASK = ~(-1L << PREDICATE_BITS);
  private static final int ARG_NUM_BITS = 4;
  private static final long ARG_NUM_MASK = ~(-1L << ARG_NUM_BITS);
  private static final int WORD_IND_BITS = 8;
  private static final long WORD_IND_MASK = ~(-1L << WORD_IND_BITS);
  // The largest possible argument number.
  private static final int MAX_ARG_NUM = 1 << ARG_NUM_BITS;
  // These are the locations of each field within the number. The
  // layout
  // within the number is:
  // | sbj word ind | obj word ind | arg num | subj word | obj word |
  // 63 0
  private static final int OBJECT_OFFSET = 0;
  private static final int ARG_NUM_OFFSET = OBJECT_OFFSET + PREDICATE_BITS;
  private static final int SUBJECT_OFFSET = ARG_NUM_OFFSET + ARG_NUM_BITS;
  private static final int OBJECT_WORD_IND_OFFSET = SUBJECT_OFFSET + PREDICATE_BITS;
  private static final int SUBJECT_WORD_IND_OFFSET = OBJECT_WORD_IND_OFFSET + WORD_IND_BITS;

  // Member variables ////////////////////////////////////

  // Weights and object -> int mappings for the lexicon (terminals).
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;

  // Pull out the weights and variable types from the dependency
  // structure distribution for efficiency.
  private final DiscreteVariable dependencyHeadType; // type of head
                                                     // and argument.
  private final DiscreteVariable dependencyArgNumType;
  private final Tensor dependencyTensor;

  // Binary type changing/combining rules.
  private final List<CcgBinaryRule> binaryRules;
  // Unary type changing/raising rules.
  private final List<CcgUnaryRule> unaryRules;
  private final Multimap<SyntacticCategory, CcgUnaryRule> applicableUnaryRuleMap;
  
  private final boolean allowComposition;

  public CcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap dependencyHeadVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      DiscreteFactor dependencyDistribution, List<CcgBinaryRule> binaryRules,
      List<CcgUnaryRule> unaryRules, boolean allowComposition) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);

    Preconditions.checkArgument(dependencyDistribution.getVars().equals(
        VariableNumMap.unionAll(dependencyHeadVar, dependencyArgNumVar, dependencyArgVar)));
    Preconditions.checkArgument(dependencyHeadVar.getOnlyVariableNum() < dependencyArgNumVar.getOnlyVariableNum());
    Preconditions.checkArgument(dependencyArgNumVar.getOnlyVariableNum() < dependencyArgVar.getOnlyVariableNum());
    this.dependencyHeadType = dependencyHeadVar.getDiscreteVariables().get(0);
    this.dependencyArgNumType = dependencyArgNumVar.getDiscreteVariables().get(0);
    DiscreteVariable dependencyArgType = dependencyArgVar.getDiscreteVariables().get(0);
    Preconditions.checkArgument(dependencyHeadType.equals(dependencyArgType));
    this.dependencyTensor = dependencyDistribution.getWeights();

    this.binaryRules = Preconditions.checkNotNull(binaryRules);
    this.unaryRules = Preconditions.checkNotNull(unaryRules);

    this.applicableUnaryRuleMap = HashMultimap.create();
    for (CcgUnaryRule rule : unaryRules) {
      applicableUnaryRuleMap.put(rule.getInputSyntacticCategory(), rule);
    }
    
    this.allowComposition = allowComposition;
  }

  public List<CcgParse> beamSearch(List<String> terminals, int beamSize) {
    return beamSearch(terminals, beamSize, new NullLogFunction());
  }
  
  public List<CcgParse> beamSearch(int beamSize, String... terminals) {
    return beamSearch(Arrays.asList(terminals), beamSize);
  }

  /**
   * Performs a beam search to find the best CCG parses of
   * {@code terminals}. Note that this is an approximate inference
   * strategy, and the returned parses may not be the best parses if
   * at any point during the search more than {@code beamSize} parse
   * trees exist for a span of the sentence.
   * 
   * @param terminals
   * @param beamSize
   * @param log
   * @return {@code beamSize} best parses for {@code terminals}.
   */
  public List<CcgParse> beamSearch(List<String> terminals, int beamSize, LogFunction log) {
    CcgChart chart = new CcgChart(terminals, beamSize);

    log.startTimer("ccg_parse/initialize_chart");
    initializeChart(terminals, chart);
    log.stopTimer("ccg_parse/initialize_chart");

    // Construct a tree from the nonterminals.
    log.startTimer("ccg_parse/calculate_inside_beam");
    int chartSize = chart.size();
    for (int spanSize = 1; spanSize < chartSize; spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chartSize; spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart, log);
      }
    }
    log.stopTimer("ccg_parse/calculate_inside_beam");

    int numParses = Math.min(beamSize, chart.getNumChartEntriesForSpan(0, chart.size() - 1));

    return chart.decodeBestParsesForSpan(0, chart.size() - 1, numParses, this);
  }

  /**
   * Initializes the parse chart with entries from the CCG lexicon for
   * {@code terminals}.
   * 
   * @param terminals
   * @param chart
   */
  private void initializeChart(List<String> terminals, CcgChart chart) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());

    // Identify all possible assignments to the dependency head and
    // argument
    // variables, so that we can look up probabilities in a sparser
    // tensor.
    Set<Long> possiblePredicates = Sets.newHashSet();

    int ccgCategoryVarNum = ccgCategoryVar.getOnlyVariableNum();
    for (int i = 0; i < terminals.size(); i++) {
      for (int j = i; j < terminals.size(); j++) {
        if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
          Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, j + 1));
          Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
          while (iterator.hasNext()) {
            Outcome bestOutcome = iterator.next();
            CcgCategory category = (CcgCategory) bestOutcome.getAssignment().getValue(ccgCategoryVarNum);

            // Add all possible chart entries to the ccg chart.
            ChartEntry entry = ccgCategoryToChartEntry(category, i, j);
            addChartEntryWithUnaryRules(entry, chart, bestOutcome.getProbability(), i, j);

            // Identify possible predicates.
            for (int assignmentPredicateNum : entry.getAssignmentPredicateNums()) {
              possiblePredicates.add((long) assignmentPredicateNum);
            }
          }
        }
      }
    }

    // Sparsify the dependency tensor for faster parsing.
    long[] keyNums = Longs.toArray(possiblePredicates);
    double[] values = new double[keyNums.length];
    Arrays.fill(values, 1.0);

    int headVarNum = dependencyTensor.getDimensionNumbers()[0];
    int argVarNum = dependencyTensor.getDimensionNumbers()[2];
    int predVarSize = dependencyTensor.getDimensionSizes()[0];

    SparseTensor keyIndicator = SparseTensor.fromUnorderedKeyValues(new int[] { headVarNum },
        new int[] { predVarSize }, keyNums, values);

    Tensor smallDependencyTensor = dependencyTensor.retainKeys(keyIndicator)
        .retainKeys(keyIndicator.relabelDimensions(new int[] { argVarNum }));

    chart.setDependencyTensor(smallDependencyTensor);
  }

  private ChartEntry ccgCategoryToChartEntry(CcgCategory result, int spanStart, int spanEnd) {
    // Assign each predicate in this category a unique word index.
    List<Integer> variableNums = Lists.newArrayList();
    List<Integer> predicateNums = Lists.newArrayList();
    List<Integer> indexes = Lists.newArrayList();

    List<Set<String>> values = result.getAssignment();
    int[] semanticVariables = result.getSemanticVariables();
    for (int i = 0; i < values.size(); i++) {
      for (String value : values.get(i)) {
        variableNums.add(semanticVariables[i]);
        predicateNums.add(dependencyHeadType.getValueIndex(value));
        indexes.add(spanEnd);
      }
    }

    List<UnfilledDependency> deps = Lists.newArrayList();
    List<UnfilledDependency> unfilledDeps = result.createUnfilledDependencies(spanEnd, deps);

    long[] unfilledDepArray = unfilledDependencyArrayToLongArray(unfilledDeps);
    long[] depArray = unfilledDependencyArrayToLongArray(deps);

    return new ChartEntry(result.getSyntax(), result, null, Ints.toArray(variableNums),
        Ints.toArray(predicateNums), Ints.toArray(indexes), unfilledDepArray, depArray,
        spanStart, spanEnd);
  }

  private void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart, LogFunction log) {
    long[] depAccumulator = new long[50];

    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j is for forward compatibility for skipping terminal
      // symbols.
      for (int j = i + 1; j < i + 2; j++) {
        // log.startTimer("ccg_parse/aggregate_syntax");
        ChartEntry[] leftTrees = chart.getChartEntriesForSpan(spanStart, spanStart + i);
        double[] leftProbs = chart.getChartEntryProbsForSpan(spanStart, spanStart + i);
        int numLeftTrees = chart.getNumChartEntriesForSpan(spanStart, spanStart + i);
        Multimap<SyntacticCategory, Integer> leftTypes = aggregateBySyntacticType(leftTrees, numLeftTrees);
        Multimap<SyntacticCategory, Integer> leftArguments = aggregateByArgumentType(leftTrees, numLeftTrees, Direction.RIGHT);
        Multimap<SyntacticCategory, Integer> leftReturns = aggregateByReturnType(leftTrees, numLeftTrees);

        ChartEntry[] rightTrees = chart.getChartEntriesForSpan(spanStart + j, spanEnd);
        double[] rightProbs = chart.getChartEntryProbsForSpan(spanStart + j, spanEnd);
        int numRightTrees = chart.getNumChartEntriesForSpan(spanStart + j, spanEnd);
        Multimap<SyntacticCategory, Integer> rightTypes = aggregateBySyntacticType(rightTrees, numRightTrees);
        Multimap<SyntacticCategory, Integer> rightArguments = aggregateByArgumentType(rightTrees, numRightTrees, Direction.LEFT);
        Multimap<SyntacticCategory, Integer> rightReturns = aggregateByReturnType(rightTrees, numRightTrees);
        // log.stopTimer("ccg_parse/aggregate_syntax");

        // log.startTimer("ccg_parse/application");
        // Do CCG right application. (The category on the left is a
        // function.)
        for (SyntacticCategory leftArgument : leftArguments.keySet()) {
          for (SyntacticCategory rightType : rightTypes.keySet()) {
            if (leftArgument.isUnifiableWith(rightType)) {
              for (Integer leftIndex : leftArguments.get(leftArgument)) {
                ChartEntry leftRoot = leftTrees[leftIndex];
                double leftProb = leftProbs[leftIndex];
                for (Integer rightIndex : rightTypes.get(rightType)) {
                  ChartEntry rightRoot = rightTrees[rightIndex];
                  double rightProb = rightProbs[rightIndex];

                  ChartEntry result = unifyChartEntries(leftRoot, rightRoot, 0, spanStart,
                      spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex,
                      depAccumulator, log);
                  if (result != null) {
                    addChartEntryWithUnaryRules(result, chart, leftProb * rightProb, spanStart, spanEnd);
                  }
                }
              }
            }
          }
        }

        // Do CCG left application. (The category on the right is a
        // function.)
        for (SyntacticCategory rightArgument : rightArguments.keySet()) {
          for (SyntacticCategory leftType : leftTypes.keySet()) {
            if (rightArgument.isUnifiableWith(leftType)) {
              for (Integer leftIndex : leftTypes.get(leftType)) {
                ChartEntry leftRoot = leftTrees[leftIndex];
                double leftProb = leftProbs[leftIndex];
                for (Integer rightIndex : rightArguments.get(rightArgument)) {
                  ChartEntry rightRoot = rightTrees[rightIndex];
                  double rightProb = rightProbs[rightIndex];

                  ChartEntry result = unifyChartEntries(rightRoot, leftRoot, 0, spanStart,
                      spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex,
                      depAccumulator, log);
                  if (result != null) {
                    addChartEntryWithUnaryRules(result, chart, leftProb * rightProb, spanStart, spanEnd);
                  }
                }
              }
            }
          }
        }
        // log.stopTimer("ccg_parse/application");
        
        // log.startTimer("ccg_parse/composition");
        // Rightward depth-1 forward composition
        if (allowComposition) {
          for (SyntacticCategory leftArgument : leftArguments.keySet()) {
            for (SyntacticCategory rightReturn : rightReturns.keySet()) {
              if (leftArgument.isUnifiableWith(rightReturn)) {
                for (Integer rightIndex : rightReturns.get(rightReturn)) {
                  ChartEntry rightRoot = rightTrees[rightIndex];
                  double rightProb = rightProbs[rightIndex];
                  for (Integer leftIndex : leftArguments.get(leftArgument)) {
                    ChartEntry leftRoot = leftTrees[leftIndex];
                    double leftProb = leftProbs[leftIndex];
                    
                    ChartEntry result = unifyChartEntries(leftRoot, rightRoot, 1, spanStart,
                        spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex,
                        depAccumulator, log);
                    if (result != null) {
                      addChartEntryWithUnaryRules(result, chart, leftProb * rightProb, spanStart, spanEnd);
                    }
                  }
                }
              }
            }
          }
        }

        // Leftward depth-1 forward composition 
        if (allowComposition) {
          for (SyntacticCategory rightArgument : rightArguments.keySet()) {
            for (SyntacticCategory leftReturn : leftReturns.keySet()) {
              if (rightArgument.isUnifiableWith(leftReturn)) {
                for (Integer leftIndex : leftReturns.get(leftReturn)) {
                  ChartEntry leftRoot = leftTrees[leftIndex];
                  double leftProb = leftProbs[leftIndex];
                  for (Integer rightIndex : rightArguments.get(rightArgument)) {
                    ChartEntry rightRoot = rightTrees[rightIndex];
                    double rightProb = rightProbs[rightIndex];
                    
                    ChartEntry result = unifyChartEntries(rightRoot, leftRoot, 1, spanStart,
                        spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex,
                        depAccumulator, log);
                  if (result != null) {
                    addChartEntryWithUnaryRules(result, chart, leftProb * rightProb, spanStart, spanEnd);
                  }
                  }
                }
              }
            }
          }
        }
        // log.stopTimer("ccg_parse/composition");

        // log.startTimer("ccg_parse/binary_rules");
        // Do any binary CCG rules.
        for (CcgBinaryRule binaryRule : binaryRules) {
          SyntacticCategory leftType = binaryRule.getLeftSyntacticType();
          SyntacticCategory rightType = binaryRule.getRightSyntacticType();
          for (Integer leftIndex : leftTypes.get(leftType)) {
            ChartEntry leftRoot = leftTrees[leftIndex];
            double leftProb = leftProbs[leftIndex];
            for (Integer rightIndex : rightTypes.get(rightType)) {
              ChartEntry rightRoot = rightTrees[rightIndex];
              double rightProb = rightProbs[rightIndex];

              ChartEntry result = binaryRule.apply(leftRoot, rightRoot, spanStart, spanStart + i,
                  leftIndex, spanStart + j, spanEnd, rightIndex);

              if (result != null) {
                addChartEntryWithUnaryRules(result, chart, leftProb * rightProb, spanStart, spanEnd);
              }
            }
          }
        }
        // log.stopTimer("ccg_parse/binary_rules");
      }
    }
  }

  private Multimap<SyntacticCategory, Integer> aggregateBySyntacticType(
      ChartEntry[] entries, int numEntries) {
    Multimap<SyntacticCategory, Integer> map = HashMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      map.put(entries[i].getSyntax(), i);
    }
    return map;
  }

  /**
   * Identifies all elements of {@code entries} that accept an
   * argument on {@code direction}, and returns a map from the
   * argument type to the indexes of chart entries that accept that
   * type.
   * 
   * @param entries
   * @param numEntries
   * @param direction
   * @return
   */
  private Multimap<SyntacticCategory, Integer> aggregateByArgumentType(
      ChartEntry[] entries, int numEntries, Direction direction) {
    Multimap<SyntacticCategory, Integer> map = HashMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      SyntacticCategory syntax = entries[i].getSyntax();
      if (!syntax.isAtomic() && syntax.acceptsArgumentOn(direction)) {
        map.put(syntax.getArgument(), i);
      }
    }
    return map;
  }
  
  private Multimap<SyntacticCategory, Integer> aggregateByReturnType(ChartEntry[] entries, 
      int numEntries) {
    Multimap<SyntacticCategory, Integer> map = HashMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      SyntacticCategory syntax = entries[i].getSyntax();
      if (!syntax.isAtomic()) {
        map.put(syntax.getReturn(), i);
      }
    }
    return map;
  }

  /**
   * Calculates the probability of any new dependencies in
   * {@code result}, then inserts it into {@code chart}. Also applies
   * any unary rules which match to result.
   * 
   * @param result
   * @param chart
   * @param leftRightProb
   * @param spanStart
   * @param spanEnd
   */
  private void addChartEntryWithUnaryRules(ChartEntry result, CcgChart chart, double leftRightProb,
      int spanStart, int spanEnd) {
    // Get the probabilities of the generated dependencies.
    double depProb = 1.0;
    Tensor currentParseTensor = chart.getDependencyTensor();
    for (long dep : result.getDependencies()) {
      long depNum = dependencyLongToTensorKeyNum(dep);
      depProb *= currentParseTensor.get(depNum);
    }

    double totalProb = leftRightProb * depProb;
    chart.addChartEntryForSpan(result, totalProb, spanStart, spanEnd);
    /*
     * System.out.println(spanStart + "." + spanEnd + " " +
     * result.getHeadedSyntax() + " " +
     * Arrays.toString(result.getDependencies()) + " " + totalProb);
     */

    for (CcgUnaryRule unaryRule : applicableUnaryRuleMap.get(result.getSyntax())) {
      ChartEntry unaryRuleResult = unaryRule.apply(result);

      if (unaryRuleResult != null) {
        chart.addChartEntryForSpan(unaryRuleResult, totalProb, spanStart, spanEnd);
          /*
           * System.out.println(spanStart + "." + spanEnd + " " +
           * unaryRuleResult.getHeadedSyntax() + " " +
           * unaryRuleResult.getDependencies() + " " + totalProb);
           */
      }
    }
  }

  /**
   * This method generalizes both function application and composition.
   * 
   * @param first
   * @param other
   * @param otherArgumentDepth
   * @param leftSpanStart
   * @param leftSpanEnd
   * @param leftIndex
   * @param rightSpanStart
   * @param rightSpanEnd
   * @param rightIndex
   * @param depAccumulator
   * @return
   */
  private ChartEntry unifyChartEntries(ChartEntry first, ChartEntry other, int otherArgumentDepth,
      int leftSpanStart, int leftSpanEnd, int leftIndex, int rightSpanStart, int rightSpanEnd,
      int rightIndex, long[] depAccumulator, LogFunction log) {
    // log.startTimer("unify/syntax");
    HeadedSyntacticCategory firstArgumentSyntax = first.getHeadedSyntax().getArgumentType();
    HeadedSyntacticCategory firstReturnSyntax = first.getHeadedSyntax().getReturnType();

    HeadedSyntacticCategory otherReturnSyntax = other.getHeadedSyntax();
    HeadedSyntacticCategory[] otherArgumentSyntaxes = new HeadedSyntacticCategory[otherArgumentDepth];
    Direction[] otherArgumentDirections = new Direction[otherArgumentDepth];
    int[] otherHeadNums = new int[otherArgumentDepth];
    for (int i = 0; i < otherArgumentDepth; i++) {
      otherArgumentSyntaxes[i] = otherReturnSyntax.getArgumentType();
      otherArgumentDirections[i] = otherReturnSyntax.getSyntax().getDirection();
      otherHeadNums[i] = otherReturnSyntax.getRootVariable();
      otherReturnSyntax = otherReturnSyntax.getReturnType();
    }
    // log.stopTimer("unify/syntax");
    
    // log.startTimer("unify/relabel_vars");
    // Variables which will be in the returned syntactic type.
    int[] firstReturnVars = firstReturnSyntax.getUniqueVariables();

    // Map each semantic variable of other to a variable of {@code
    // this}.
    int[] otherUniqueVars = other.getHeadedSyntax().getUniqueVariables();
    int[] otherToFirstMap = otherReturnSyntax.unifyVariables(otherUniqueVars, 
        firstArgumentSyntax, firstReturnVars);
    if (otherToFirstMap == null) {
      return null;
    }

    // Build the return syntactic type.
    HeadedSyntacticCategory returnSyntax = firstReturnSyntax;
    for (int i = 0; i < otherArgumentSyntaxes.length; i++) {
      int relabeledHeadNum = otherToFirstMap[Ints.indexOf(otherUniqueVars, otherHeadNums[i])];
      returnSyntax = returnSyntax.addArgument(otherArgumentSyntaxes[i]
          .relabelVariables(other.getHeadedSyntax().getUniqueVariables(), otherToFirstMap),
          otherArgumentDirections[i], relabeledHeadNum);
    }

    int[] uniqueReturnVars = returnSyntax.getUniqueVariables(); 

    // Relabel other's variable assignment to correspond to this'
    // variables.
    int[] relabeledAssignmentVariableNums = other.getAssignmentVariableNumsRelabeled(otherToFirstMap); 
    int[] otherAssignmentPredicateNums = other.getAssignmentPredicateNums();
    int[] otherAssignmentIndexes = other.getAssignmentIndexes();
    // log.stopTimer("unify/relabel_vars");

    // log.startTimer("unify/fill_dependencies");
    // Fill any dependencies from first, using any just-filled
    // variables.
    long[] unfilledDependencies = first.getUnfilledDependencies();
    int numDeps = accumulateDependencies(unfilledDependencies, relabeledAssignmentVariableNums,
        otherAssignmentPredicateNums, otherAssignmentIndexes, depAccumulator, uniqueReturnVars, 0);
    long[] otherUnfilledDependencies = other.getUnfilledDependenciesRelabeled(otherToFirstMap);
    numDeps = accumulateDependencies(otherUnfilledDependencies, first.getAssignmentVariableNums(),
        first.getAssignmentPredicateNums(), first.getAssignmentIndexes(), depAccumulator,
        uniqueReturnVars, numDeps);

    long[] filledDepArray = separateDependencies(depAccumulator, numDeps, true);
    long[] unfilledDepArray = separateDependencies(depAccumulator, numDeps, false);

    for (int i = 0; i < unfilledDepArray.length; i++) {
      int objectVarNum = getObjectArgNumFromDep(unfilledDepArray[i]);
      Preconditions.checkState(Ints.contains(uniqueReturnVars, objectVarNum),
          "Unfillable dependency: %s %s ->\n %s %s", first.getHeadedSyntax(), other.getHeadedSyntax(),
          firstReturnSyntax, objectVarNum);
    }
    // log.stopTimer("unify/fill_dependencies");    
    
    // log.startTimer("unify/fill_assignment");
    // Accumulators for the new assignment.
    List<Integer> newAssignmentVariableNums = Lists.newArrayList();
    List<Integer> newAssignmentPredicateNums = Lists.newArrayList();
    List<Integer> newAssignmentIndexes = Lists.newArrayList();

    // Copy any assignments from the argument type which remain in the
    // return type.
    for (int i = 0; i < relabeledAssignmentVariableNums.length; i++) {
      // If the return type contains this variable, update the current
      // assignment.
      if (Ints.indexOf(uniqueReturnVars, relabeledAssignmentVariableNums[i]) != -1) {
        newAssignmentVariableNums.add(relabeledAssignmentVariableNums[i]);
        newAssignmentPredicateNums.add(otherAssignmentPredicateNums[i]);
        newAssignmentIndexes.add(otherAssignmentIndexes[i]);
      }
    }

    // Copy over any assignments from first which are still referenced
    // in the return type.
    int[] firstAssignmentVariableNums = first.getAssignmentVariableNums();
    int[] firstAssignmentPredicateNums = first.getAssignmentPredicateNums();
    int[] firstAssignmentIndexes = first.getAssignmentIndexes();
    for (int i = 0; i < firstAssignmentVariableNums.length; i++) {
      int curVar = firstAssignmentVariableNums[i];
      if (Ints.indexOf(uniqueReturnVars, curVar) != -1) {
        newAssignmentVariableNums.add(curVar);
        newAssignmentPredicateNums.add(firstAssignmentPredicateNums[i]);
        newAssignmentIndexes.add(firstAssignmentIndexes[i]);
      }
    }
    // log.startTimer("unify/fill_assignment");

    /*
     * System.out.println(newAssignmentVariableNums);
     * System.out.println(newAssignmentPredicateNums);
     * System.out.println(newAssignmentIndexes);
     * System.out.println(Arrays
     * .toString(Arrays.copyOf(depAccumulator, numDeps)));
     */


    // System.out.println("filledDeps: " +
    // Arrays.toString(longArrayToUnfilledDependencyArray(filledDepArray)));
    // System.out.println("unfilledDeps: " +
    // Arrays.toString(longArrayToUnfilledDependencyArray(unfilledDepArray)));

    return new ChartEntry(returnSyntax, null, Ints.toArray(newAssignmentVariableNums),
        Ints.toArray(newAssignmentPredicateNums), Ints.toArray(newAssignmentIndexes),
        unfilledDepArray, filledDepArray, leftSpanStart, leftSpanEnd, leftIndex, rightSpanStart,
        rightSpanEnd, rightIndex);
  }

  /**
   * Fills any dependencies in {@code unfilledDependencies} and
   * accumulates them in {@code depAccumulator}.
   * 
   * @param unfilledDependencies
   * @param assignmentVariableNums
   * @param assignmentPredicateNums
   * @param assignmentIndexes
   * @param depAccumulator
   * @param returnVariableNums
   * @param numDeps
   * @return
   */
  private int accumulateDependencies(long[] unfilledDependencies, int[] assignmentVariableNums,
      int[] assignmentPredicateNums, int[] assignmentIndexes, long[] depAccumulator,
      int[] returnVariableNums, int numDeps) {
    
    /*
    System.out.println(Arrays.toString(unfilledDependencies));
    System.out.println(Arrays.toString(assignmentVariableNums));
    System.out.println(Arrays.toString(assignmentPredicateNums));
    System.out.println(Arrays.toString(assignmentIndexes));
    System.out.println(numDeps);
    */

    // Fill any dependencies that depend on this variable.
    for (long unfilledDependency : unfilledDependencies) {
      int objectArgNum = getObjectArgNumFromDep(unfilledDependency);

      boolean depWasFilled = false;
      for (int i = 0; i < assignmentVariableNums.length; i++) {
        if (assignmentVariableNums[i] == objectArgNum) {
          // Create a new filled dependency by substituting in the
          // current object.
          long filledDep = unfilledDependency - (objectArgNum << OBJECT_OFFSET);
          filledDep += ((long) assignmentPredicateNums[i] + MAX_ARG_NUM) << OBJECT_OFFSET;
          filledDep += ((long) assignmentIndexes[i]) << OBJECT_WORD_IND_OFFSET;

          depAccumulator[numDeps] = filledDep;
          numDeps++;
          depWasFilled = true;
        }
      }

      if (!depWasFilled && Ints.contains(returnVariableNums, objectArgNum)) {
        depAccumulator[numDeps] = unfilledDependency;
        numDeps++;
      }
    }
    return numDeps;
  }

  private long[] separateDependencies(long[] deps, int numDeps, boolean getFilled) {
    // Count filled/unfilled dependencies
    int count = 0;
    for (int i = 0; i < numDeps; i++) {
      if (isFilledDependency(deps[i]) == getFilled) {
        count++;
      }
    }

    long[] filtered = new long[count];
    count = 0;
    for (int i = 0; i < numDeps; i++) {
      if (isFilledDependency(deps[i]) == getFilled) {
        filtered[count] = deps[i];
        count++;
      }
    }
    return filtered;
  }

  // Methods for efficiently encoding dependencies as longs
  // //////////////////////////////

  /**
   * Computes the {@code keyNum} containing the weight for {@code dep}
   * in {@code dependencyTensor}.
   * 
   * @param dep
   * @return
   */
  private long dependencyLongToTensorKeyNum(long depLong) {
    int headNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM;
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM;

    int argNumNum = dependencyArgNumType.getValueIndex(
        (int) ((depLong >> ARG_NUM_OFFSET) & ARG_NUM_MASK));

    return dependencyTensor.dimKeyToKeyNum(new int[] { headNum, argNumNum, objectNum });
  }

  private long unfilledDependencyToLong(UnfilledDependency dep) {
    long argNum = dep.getArgumentIndex();
    long objectNum, objectWordInd, subjectNum, subjectWordInd;

    if (dep.hasObject()) {
      IndexedPredicate obj = dep.getObject();
      objectNum = dependencyHeadType.getValueIndex(obj.getHead()) + MAX_ARG_NUM;
      objectWordInd = obj.getHeadIndex();
    } else {
      objectNum = dep.getObjectIndex();
      objectWordInd = 0L;
    }

    if (dep.hasSubject()) {
      IndexedPredicate sbj = dep.getSubject();
      subjectNum = dependencyHeadType.getValueIndex(sbj.getHead()) + MAX_ARG_NUM;
      subjectWordInd = sbj.getHeadIndex();
    } else {
      subjectNum = dep.getSubjectIndex();
      subjectWordInd = 0L;
    }

    return marshalUnfilledDependency(objectNum, argNum, subjectNum, objectWordInd, subjectWordInd);
  }

  public static long marshalUnfilledDependency(long objectNum, long argNum, long subjectNum,
      long objectWordInd, long subjectWordInd) {
    long value = 0L;
    value += objectNum << OBJECT_OFFSET;
    value += argNum << ARG_NUM_OFFSET;
    value += subjectNum << SUBJECT_OFFSET;
    value += objectWordInd << OBJECT_WORD_IND_OFFSET;
    value += subjectWordInd << SUBJECT_WORD_IND_OFFSET;
    return value;
  }

  private int getArgNumFromDep(long depLong) {
    return (int) ((depLong >> ARG_NUM_OFFSET) & ARG_NUM_MASK);
  }

  public static int getObjectArgNumFromDep(long depLong) {
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK);
    if (objectNum >= MAX_ARG_NUM) {
      return -1;
    } else {
      return objectNum;
    }
  }

  private int getObjectPredicateFromDep(long depLong) {
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK);
    if (objectNum >= MAX_ARG_NUM) {
      return objectNum - MAX_ARG_NUM;
    } else {
      return -1;
    }
  }

  private int getSubjectArgNumFromDep(long depLong) {
    int subjectNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK);
    if (subjectNum >= MAX_ARG_NUM) {
      return -1;
    } else {
      return subjectNum;
    }
  }

  private int getSubjectPredicateFromDep(long depLong) {
    int subjectNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK);
    if (subjectNum >= MAX_ARG_NUM) {
      return subjectNum - MAX_ARG_NUM;
    } else {
      return -1;
    }
  }

  /**
   * Returns {@code true} if {@code depLong} represents a filled
   * dependency structure.
   * 
   * @param depLong
   * @return
   */
  private boolean isFilledDependency(long depLong) {
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK);
    int subjectNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK);

    return objectNum >= MAX_ARG_NUM && subjectNum >= MAX_ARG_NUM;
  }

  private UnfilledDependency longToUnfilledDependency(long value) {
    int argNum, objectNum, objectWordInd, subjectNum, subjectWordInd;

    objectNum = (int) ((value >> OBJECT_OFFSET) & PREDICATE_MASK);
    argNum = (int) ((value >> ARG_NUM_OFFSET) & ARG_NUM_MASK);
    subjectNum = (int) ((value >> SUBJECT_OFFSET) & PREDICATE_MASK);
    objectWordInd = (int) ((value >> OBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);
    subjectWordInd = (int) ((value >> SUBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);

    IndexedPredicate sbj = null, obj = null;
    int objectArgIndex = -1, subjectArgIndex = -1;
    if (objectNum >= MAX_ARG_NUM) {
      String objectHead = (String) dependencyHeadType.getValue(objectNum - MAX_ARG_NUM);
      obj = new IndexedPredicate(objectHead, objectWordInd);
    } else {
      objectArgIndex = objectNum;
    }

    if (subjectNum >= MAX_ARG_NUM) {
      String subjectHead = (String) dependencyHeadType.getValue(subjectNum - MAX_ARG_NUM);
      sbj = new IndexedPredicate(subjectHead, subjectWordInd);
    } else {
      subjectArgIndex = subjectNum;
    }

    return new UnfilledDependency(sbj, subjectArgIndex, argNum, obj, objectArgIndex);
  }

  private UnfilledDependency[] longArrayToUnfilledDependencyArray(long[] values) {
    UnfilledDependency[] unfilled = new UnfilledDependency[values.length];
    for (int i = 0; i < values.length; i++) {
      unfilled[i] = longToUnfilledDependency(values[i]);
    }
    return unfilled;
  }

  private long[] unfilledDependencyArrayToLongArray(List<UnfilledDependency> deps) {
    long[] values = new long[deps.size()];
    for (int i = 0; i < deps.size(); i++) {
      values[i] = unfilledDependencyToLong(deps.get(i));
    }
    return values;
  }

  public DependencyStructure[] longArrayToFilledDependencyArray(long[] values) {
    DependencyStructure[] deps = new DependencyStructure[values.length];
    for (int i = 0; i < values.length; i++) {
      UnfilledDependency unfilled = longToUnfilledDependency(values[i]);
      deps[i] = unfilled.toDependencyStructure();
    }
    return deps;
  }

  /**
   * Converts the values assigned to a particular variable from
   * {@code variableNums} into a set of {@code IndexedPredicate}.
   * 
   * @param varNum
   * @param variableNums
   * @param predicateNums
   * @param indexes
   * @return
   */
  public Set<IndexedPredicate> variableToIndexedPredicateArray(int varNum, int[] variableNums,
      int[] predicateNums, int[] indexes) {
    Set<IndexedPredicate> predicates = Sets.newHashSet();
    for (int i = 0; i < variableNums.length; i++) {
      if (variableNums[i] == varNum) {
        predicates.add(new IndexedPredicate((String) dependencyHeadType.getValue(predicateNums[i]), indexes[i]));
      }
    }
    return predicates;
  }
}