package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
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
  // structures in longs. These are the size of each field, in bits.
  private static final int PREDICATE_BITS = 20;
  private static final long PREDICATE_MASK = ~(-1L << PREDICATE_BITS);
  private static final int ARG_NUM_BITS = 4;
  private static final long ARG_NUM_MASK = ~(-1L << ARG_NUM_BITS);
  private static final int WORD_IND_BITS = 8;
  private static final long WORD_IND_MASK = ~(-1L << WORD_IND_BITS);
  // The largest possible argument number.
  private static final int MAX_ARG_NUM = 1 << ARG_NUM_BITS;
  // These are the locations of each field within the number. The
  // layout within the number is:
  // | sbj word ind | obj word ind | arg num | subj word | obj word |
  // 63 0
  private static final int OBJECT_OFFSET = 0;
  private static final int ARG_NUM_OFFSET = OBJECT_OFFSET + PREDICATE_BITS;
  private static final int SUBJECT_OFFSET = ARG_NUM_OFFSET + ARG_NUM_BITS;
  private static final int OBJECT_WORD_IND_OFFSET = SUBJECT_OFFSET + PREDICATE_BITS;
  private static final int SUBJECT_WORD_IND_OFFSET = OBJECT_WORD_IND_OFFSET + WORD_IND_BITS;

  // Default names for the variables in the syntactic distribution
  // built by
  // buildSyntacticDistribution
  public static final String LEFT_SYNTAX_VAR_NAME = "leftSyntax";
  public static final String RIGHT_SYNTAX_VAR_NAME = "rightSyntax";
  public static final String PARENT_SYNTAX_VAR_NAME = "parentSyntax";

  // Member variables ////////////////////////////////////

  // Weights and word -> ccg category mappings for the lexicon
  // (terminals).
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;

  // Weights on dependency structures.
  private final DiscreteVariable dependencyHeadType; // type of head
                                                     // and argument.
  private final DiscreteVariable dependencyArgNumType;
  private final Tensor dependencyTensor;

  // Weights on syntactic structures.
  private final VariableNumMap leftSyntaxVar;
  private final VariableNumMap rightSyntaxVar;
  private final VariableNumMap parentSyntaxVar;
  private final DiscreteFactor syntaxDistribution;

  // Binary rules
  private final List<CcgBinaryRule> binaryRules;
  // Unary type changing/raising rules.
  private final Multimap<SyntacticCategory, CcgUnaryRule> applicableUnaryRuleMap;
  // All predicates used in CCG rules.
  private final Set<Long> predicatesInRules;

  public CcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap dependencyHeadVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      DiscreteFactor dependencyDistribution, VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      DiscreteFactor syntaxDistribution, List<CcgBinaryRule> binaryRules, List<CcgUnaryRule> unaryRules) {
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

    this.leftSyntaxVar = leftSyntaxVar;
    this.rightSyntaxVar = rightSyntaxVar;
    this.parentSyntaxVar = parentSyntaxVar;
    Preconditions.checkArgument(syntaxDistribution.getVars().equals(
        VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, parentSyntaxVar)));
    this.syntaxDistribution = syntaxDistribution;

    this.binaryRules = Preconditions.checkNotNull(binaryRules);
    this.applicableUnaryRuleMap = HashMultimap.create();
    for (CcgUnaryRule rule : unaryRules) {
      applicableUnaryRuleMap.put(rule.getInputSyntacticCategory(), rule);
    }

    // Cache predicates in rules.
    predicatesInRules = Sets.newHashSet();
    for (CcgBinaryRule rule : binaryRules) {
      for (String predicate : rule.getSubjects()) {
        predicatesInRules.add((long) dependencyHeadType.getValueIndex(predicate));
      }
    }
    for (CcgUnaryRule rule : unaryRules) {
      for (String predicate : rule.getSubjects()) {
        predicatesInRules.add((long) dependencyHeadType.getValueIndex(predicate));
      }
    }
  }

  public static DiscreteFactor buildSyntacticDistribution(
      Iterable<HeadedSyntacticCategory> syntacticCategories, boolean allowComposition) {
    // Compute the closure of syntactic categories, assuming the only
    // rule is function application.
    Set<HeadedSyntacticCategory> allCategories = Sets.newHashSet();
    for (HeadedSyntacticCategory cat : syntacticCategories) {
      Preconditions.checkArgument(cat.isCanonicalForm());
      allCategories.add(cat);

      while (!cat.getSyntax().isAtomic()) {
        allCategories.add(cat.getArgumentType().getCanonicalForm());
        allCategories.add(cat.getReturnType().getCanonicalForm());
        cat = cat.getReturnType();
      }
    }

    Set<List<Object>> validOutcomes = Sets.newHashSet();
    Set<Combinator> combinators = Sets.newHashSet();
    // Compute function application rules.
    for (HeadedSyntacticCategory functionCat : allCategories) {
      for (HeadedSyntacticCategory argumentCat : allCategories) {
        if (!functionCat.isAtomic() && functionCat.getArgumentType().isUnifiableWith(argumentCat)) {
          Direction direction = functionCat.getSyntax().getDirection();
          Combinator combinator;
          List<Object> outcome;
          if (direction.equals(Direction.LEFT)) {
            combinator = getApplicationCombinator(functionCat, argumentCat, true);
            outcome = Arrays.<Object> asList(argumentCat, functionCat, combinator);
          } else if (direction.equals(Direction.RIGHT)) {
            combinator = getApplicationCombinator(functionCat, argumentCat, false);
            outcome = Arrays.<Object> asList(functionCat, argumentCat, combinator);
          } else {
            // Forward compatible error message, for handling
            // Direction.BOTH if added.
            throw new IllegalArgumentException("Unknown direction type: " + direction);
          }
          validOutcomes.add(outcome);
          combinators.add(combinator);
        }
      }
    }
    
    if (allowComposition) {
      // Compute function composition rules.
      for (HeadedSyntacticCategory functionCat : allCategories) {
        for (HeadedSyntacticCategory argumentCat : allCategories) {
          if (!functionCat.isAtomic() && !argumentCat.isAtomic() 
              && functionCat.getArgumentType().isUnifiableWith(argumentCat.getReturnType())) {
            Direction direction = functionCat.getSyntax().getDirection();
            Combinator combinator;
            List<Object> outcome;
            if (direction.equals(Direction.LEFT)) {
              combinator = getCompositionCombinator(functionCat, argumentCat, true);
              outcome = Arrays.<Object> asList(argumentCat, functionCat, combinator);
            } else if (direction.equals(Direction.RIGHT)) {
              combinator = getCompositionCombinator(functionCat, argumentCat, false);
              outcome = Arrays.<Object> asList(functionCat, argumentCat, combinator);
            } else {
              // Forward compatible error message, for handling
              // Direction.BOTH if added.
              throw new IllegalArgumentException("Unknown direction type: " + direction);
            }
            validOutcomes.add(outcome);
            combinators.add(combinator);
          }
        }
      }
    }

    // Build an indicator tensor for valid combinations of syntactic
    // categories.
    DiscreteVariable syntaxType = new DiscreteVariable("syntacticCategory", allCategories);
    DiscreteVariable combinatorType = new DiscreteVariable("combinator", combinators);
    VariableNumMap syntaxVars = new VariableNumMap(Arrays.asList(0, 1, 2),
        Arrays.asList(LEFT_SYNTAX_VAR_NAME, RIGHT_SYNTAX_VAR_NAME, PARENT_SYNTAX_VAR_NAME),
        Arrays.asList(syntaxType, syntaxType, combinatorType));
    TableFactorBuilder syntaxDistributionBuilder = new TableFactorBuilder(syntaxVars,
        SparseTensorBuilder.getFactory());
    for (List<Object> outcome : validOutcomes) {
      syntaxDistributionBuilder.setWeight(syntaxVars.outcomeToAssignment(outcome), 1.0);
    }

    return syntaxDistributionBuilder.build();
  }

  private static Combinator getApplicationCombinator(HeadedSyntacticCategory functionCat,
      HeadedSyntacticCategory argumentCat, boolean argumentOnLeft) {
    Preconditions.checkArgument(functionCat.getArgumentType().isUnifiableWith(argumentCat));

    HeadedSyntacticCategory functionReturnType = functionCat.getReturnType();
    HeadedSyntacticCategory functionArgumentType = functionCat.getArgumentType();
    Preconditions.checkState(functionReturnType.isCanonicalForm());

    int[] argumentRelabeling = argumentCat.unifyVariables(argumentCat.getUniqueVariables(),
        functionArgumentType, new int[0]);
    int[] functionRelabeling = functionCat.getUniqueVariables();
    int[] resultRelabeling = functionReturnType.getUniqueVariables(); 
    int[] unifiedVariables = functionArgumentType.getUniqueVariables();

    if (argumentOnLeft) {
      return new Combinator(functionReturnType, argumentRelabeling, functionRelabeling,
          resultRelabeling, resultRelabeling, unifiedVariables);
    } else {
      return new Combinator(functionReturnType, functionRelabeling, argumentRelabeling,
          resultRelabeling, resultRelabeling, unifiedVariables);
    }
  }
  
  private static Combinator getCompositionCombinator(HeadedSyntacticCategory functionCat,
      HeadedSyntacticCategory argumentCat, boolean argumentOnLeft) {
    Preconditions.checkArgument(functionCat.getArgumentType().isUnifiableWith(argumentCat.getReturnType()));
    // Determine which syntactic category results from composing the
    // two input categories.
    int[] argumentVars = argumentCat.getUniqueVariables();
    int[] argumentRelabeling = argumentCat.getReturnType().unifyVariables(argumentVars, 
        functionCat.getArgumentType(), functionCat.getUniqueVariables());
    System.out.println("function: " + functionCat);
    System.out.println("argument: " + argumentCat);
    HeadedSyntacticCategory relabeledArgumentType = argumentCat.relabelVariables(argumentVars, argumentRelabeling);
    HeadedSyntacticCategory resultType = functionCat.getReturnType().addArgument(
        relabeledArgumentType.getArgumentType(), argumentCat.getDirection(), 
        functionCat.getRootVariable());

    // Relabel the input assignments into the result type's variable
    // numbering.
    int[] argumentCatRelabeling = argumentRelabeling;
    int[] functionCatRelabeling = functionCat.getUniqueVariables();
    int[] unifiedVariables = functionCat.getArgumentType().getUniqueVariables();
    
    Map<Integer, Integer> resultRelabelingMap = Maps.newHashMap();
    HeadedSyntacticCategory canonicalResultType = resultType.getCanonicalForm(resultRelabelingMap);
    int[] resultUniqueVars = resultType.getUniqueVariables();
    int[] resultCatRelabeling = new int[resultUniqueVars.length];
    for (int i = 0; i < resultUniqueVars.length; i++) {
      resultCatRelabeling[i] = resultRelabelingMap.get(resultUniqueVars[i]);
    }

    if (argumentOnLeft) {
      return new Combinator(canonicalResultType, argumentCatRelabeling, functionCatRelabeling, 
          resultUniqueVars, resultCatRelabeling, unifiedVariables);
    } else {
      return new Combinator(canonicalResultType, functionCatRelabeling, argumentCatRelabeling, 
          resultUniqueVars, resultCatRelabeling, unifiedVariables);
    }
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
    return beamSearch(terminals, beamSize, null, log);
  }

  public List<CcgParse> beamSearch(List<String> terminals, int beamSize, ChartFilter beamFilter,
      LogFunction log) {
    CcgChart chart = new CcgChart(terminals, beamSize, beamFilter);

    log.startTimer("ccg_parse/initialize_chart");
    initializeChart(terminals, chart);
    log.stopTimer("ccg_parse/initialize_chart");

    // Construct a tree from the nonterminals.
    log.startTimer("ccg_parse/calculate_inside_beam");
    calculateInsideBeam(chart, log);
    log.stopTimer("ccg_parse/calculate_inside_beam");

    return decodeParsesForRoot(chart);
  }

  /**
   * Performs a beam search over possible CCG parses given a
   * {@code chart} initialized with entries for all terminals.
   * 
   * @param chart
   * @param log
   */
  public void calculateInsideBeam(CcgChart chart, LogFunction log) {
    int chartSize = chart.size();
    for (int spanSize = 1; spanSize < chartSize; spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chartSize; spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart, log);
      }
    }
  }

  /**
   * Gets the best parses for an entire sentence, given an
   * already-filled {@code chart}.
   * 
   * @param chart
   * @return
   */
  public List<CcgParse> decodeParsesForRoot(CcgChart chart) {
    int numParses = Math.min(chart.getBeamSize(),
        chart.getNumChartEntriesForSpan(0, chart.size() - 1));
    return chart.decodeBestParsesForSpan(0, chart.size() - 1, numParses, this);
  }

  /**
   * Initializes the parse chart with entries from the CCG lexicon for
   * {@code terminals}.
   * 
   * @param terminals
   * @param chart
   */
  public void initializeChart(List<String> terminals, CcgChart chart) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());

    // Identify all possible assignments to the dependency head and
    // argument variables, so that we can look up probabilities in a
    // sparser tensor.
    Set<Long> possiblePredicates = Sets.newHashSet(predicatesInRules);

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
    long[] depAccumulator = new long[20];
    int[] assignmentVariableAccumulator = new int[20];
    int[] assignmentPredicateAccumulator = new int[20];
    int[] assignmentIndexAccumulator = new int[20];

    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j is for forward compatibility for skipping terminal
      // symbols.
      for (int j = i + 1; j < i + 2; j++) {
        // log.startTimer("ccg_parse/aggregate_syntax");
        ChartEntry[] leftTrees = chart.getChartEntriesForSpan(spanStart, spanStart + i);
        double[] leftProbs = chart.getChartEntryProbsForSpan(spanStart, spanStart + i);
        int numLeftTrees = chart.getNumChartEntriesForSpan(spanStart, spanStart + i);
        Multimap<HeadedSyntacticCategory, Integer> leftTypes = aggregateBySyntacticType(leftTrees, numLeftTrees);

        ChartEntry[] rightTrees = chart.getChartEntriesForSpan(spanStart + j, spanEnd);
        double[] rightProbs = chart.getChartEntryProbsForSpan(spanStart + j, spanEnd);
        int numRightTrees = chart.getNumChartEntriesForSpan(spanStart + j, spanEnd);
        Multimap<HeadedSyntacticCategory, Integer> rightTypes = aggregateBySyntacticType(rightTrees, numRightTrees);

        for (HeadedSyntacticCategory leftType : leftTypes.keySet()) {
          for (HeadedSyntacticCategory rightType : rightTypes.keySet()) {
            Assignment assignment = leftSyntaxVar.outcomeArrayToAssignment(leftType).union(
                rightSyntaxVar.outcomeArrayToAssignment(rightType));
            Iterator<Outcome> results = syntaxDistribution.outcomePrefixIterator(assignment);

            while (results.hasNext()) {
              Combinator resultCombinator = (Combinator) results.next().getAssignment().getValue(
                  parentSyntaxVar.getOnlyVariableNum());
              HeadedSyntacticCategory resultSyntax = resultCombinator.getSyntax();

              for (Integer leftIndex : leftTypes.get(leftType)) {
                ChartEntry leftRoot = leftTrees[leftIndex];
                double leftProb = leftProbs[leftIndex];
                for (Integer rightIndex : rightTypes.get(rightType)) {
                  ChartEntry rightRoot = rightTrees[rightIndex];
                  double rightProb = rightProbs[rightIndex];

                  // Relabel assignments from the left and right chart
                  // entries.
                  int numAssignments = relabelAssignment(leftRoot, resultCombinator.getLeftVariableRelabeling(),
                      assignmentVariableAccumulator, assignmentPredicateAccumulator, assignmentIndexAccumulator, 0);
                  numAssignments = relabelAssignment(rightRoot, resultCombinator.getRightVariableRelabeling(),
                      assignmentVariableAccumulator, assignmentPredicateAccumulator, assignmentIndexAccumulator, numAssignments);
                  int[] newAssignmentVariableNums = Arrays.copyOfRange(assignmentVariableAccumulator, 0, numAssignments);
                  int[] newAssignmentPredicateNums = Arrays.copyOfRange(assignmentPredicateAccumulator, 0, numAssignments);
                  int[] newAssignmentIndexes = Arrays.copyOfRange(assignmentIndexAccumulator, 0, numAssignments);

                  System.out.println("vars: " + Arrays.toString(newAssignmentVariableNums));
                  System.out.println("predicates: " + Arrays.toString(newAssignmentPredicateNums));
                  System.out.println("indexes: " + Arrays.toString(newAssignmentIndexes));

                  // Relabel and fill dependencies from the left and
                  // right chart entries.
                  long[] leftUnfilledDependenciesRelabeled = leftRoot.getUnfilledDependenciesRelabeled(
                      resultCombinator.getLeftVariableRelabeling());
                  long[] rightUnfilledDependenciesRelabeled = rightRoot.getUnfilledDependenciesRelabeled(
                      resultCombinator.getRightVariableRelabeling());

                  int numDeps = 0;
                  numDeps = accumulateDependencies(leftUnfilledDependenciesRelabeled,
                      resultCombinator.getUnifiedVariables(), newAssignmentVariableNums,
                      newAssignmentPredicateNums, newAssignmentIndexes, depAccumulator,
                      resultCombinator.getResultOriginalVars(), resultCombinator.getResultVariableRelabeling(),
                      resultSyntax.getUniqueVariables(), numDeps);
                  if (numDeps == -1) {
                    continue;
                  }
                  numDeps = accumulateDependencies(rightUnfilledDependenciesRelabeled,
                      resultCombinator.getUnifiedVariables(), newAssignmentVariableNums,
                      newAssignmentPredicateNums, newAssignmentIndexes, depAccumulator,
                      resultCombinator.getResultOriginalVars(), resultCombinator.getResultVariableRelabeling(), 
                      resultSyntax.getUniqueVariables(), numDeps);
                  if (numDeps == -1) {
                    continue;
                  }
                  long[] filledDepArray = separateDependencies(depAccumulator, numDeps, true);
                  long[] unfilledDepArray = separateDependencies(depAccumulator, numDeps, false);

                  numAssignments = filterAssignmentVariables(assignmentVariableAccumulator, assignmentPredicateAccumulator,
                      assignmentIndexAccumulator, resultCombinator.getResultOriginalVars(), 
                      resultCombinator.getResultVariableRelabeling(), numAssignments);
                  newAssignmentVariableNums = Arrays.copyOfRange(assignmentVariableAccumulator, 0, numAssignments);
                  newAssignmentPredicateNums = Arrays.copyOfRange(assignmentPredicateAccumulator, 0, numAssignments);
                  newAssignmentIndexes = Arrays.copyOfRange(assignmentIndexAccumulator, 0, numAssignments);

                  ChartEntry result = new ChartEntry(resultSyntax, null, newAssignmentVariableNums,
                      newAssignmentPredicateNums, newAssignmentIndexes, unfilledDepArray,
                      filledDepArray, spanStart, spanStart + i, leftIndex, spanStart + j, spanEnd,
                      rightIndex);
                  addChartEntryWithUnaryRules(result, chart, leftProb * rightProb, spanStart, spanEnd);
                }
              }
            }
          }
        }
      }
    }
  }

  private Multimap<HeadedSyntacticCategory, Integer> aggregateBySyntacticType(
      ChartEntry[] entries, int numEntries) {
    Multimap<HeadedSyntacticCategory, Integer> map = HashMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      map.put(entries[i].getHeadedSyntax(), i);
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
  public void addChartEntryWithUnaryRules(ChartEntry result, CcgChart chart, double leftRightProb,
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

  private int relabelAssignment(ChartEntry entry, int[] relabeling, int[] variableAccumulator,
      int[] predicateAccumulator, int[] indexAccumulator, int startIndex) {

    int[] uniqueVars = entry.getHeadedSyntax().getUniqueVariables();
    int[] assignmentVariableNums = entry.getAssignmentVariableNums();
    int[] assignmentPredicateNums = entry.getAssignmentPredicateNums();
    int[] assignmentIndexes = entry.getAssignmentIndexes();

    System.out.println("uniqueVars: " + Arrays.toString(uniqueVars));
    System.out.println("assignmentVars: " + Arrays.toString(assignmentVariableNums));
    System.out.println("assignmentPredicates: " + Arrays.toString(assignmentPredicateNums));
    System.out.println("assignmentIndexes: " + Arrays.toString(assignmentIndexes));
    for (int i = 0; i < assignmentVariableNums.length; i++) {
      for (int j = 0; j < uniqueVars.length; j++) {
        if (uniqueVars[j] == assignmentVariableNums[i]) {
          variableAccumulator[i + startIndex] = relabeling[j];
          predicateAccumulator[i + startIndex] = assignmentPredicateNums[i];
          indexAccumulator[i + startIndex] = assignmentIndexes[i];
        }
      }
    }

    return startIndex + assignmentVariableNums.length;
  }

  private int filterAssignmentVariables(int[] variableAccumulator, int[] predicateAccumulator,
      int[] indexAccumulator, int[] varsToRetain, int[] relabeling, int accumulatorSize) {
    int numRemoved = 0;
    for (int i = 0; i < accumulatorSize; i++) {
      int index = Ints.indexOf(varsToRetain, variableAccumulator[i]);
      if (index != -1) {
        variableAccumulator[i - numRemoved] = relabeling[index];
        predicateAccumulator[i - numRemoved] = predicateAccumulator[i];
        indexAccumulator[i - numRemoved] = indexAccumulator[i];
      } else {
        numRemoved++;
      }
    }
    return accumulatorSize - numRemoved;
  }

  /**
   * Fills any dependencies in {@code unfilledDependencies} and
   * accumulates them in {@code depAccumulator}.
   * 
   * @param unfilledDependencies
   * @param variablesToUnify
   * @param assignmentVariableNums
   * @param assignmentPredicateNums
   * @param assignmentIndexes
   * @param depAccumulator
   * @param returnVariableNums
   * @param numDeps
   * @return
   */
  private int accumulateDependencies(long[] unfilledDependencies, int[] variablesToUnify,
      int[] assignmentVariableNums, int[] assignmentPredicateNums, int[] assignmentIndexes,
      long[] depAccumulator, int[] returnOriginalVars, int[] returnVarsRelabeling, 
      int[] returnVariableNums, int numDeps) {

    /*
     * System.out.println(Arrays.toString(unfilledDependencies));
     * System.out.println(Arrays.toString(assignmentVariableNums));
     * System.out.println(Arrays.toString(assignmentPredicateNums));
     * System.out.println(Arrays.toString(assignmentIndexes));
     * System.out.println(numDeps);
     */

    // Fill any dependencies that depend on this variable.
    for (long unfilledDependency : unfilledDependencies) {
      int objectArgNum = getObjectArgNumFromDep(unfilledDependency);

      boolean depWasFilled = false;
      if (Ints.contains(variablesToUnify, objectArgNum)) {
        for (int i = 0; i < assignmentVariableNums.length; i++) {
          if (assignmentVariableNums[i] == objectArgNum) {
            // Create a new filled dependency by substituting in the
            // current object.
            long filledDep = unfilledDependency - (objectArgNum << OBJECT_OFFSET);
            filledDep += ((long) assignmentPredicateNums[i] + MAX_ARG_NUM) << OBJECT_OFFSET;
            filledDep += ((long) assignmentIndexes[i]) << OBJECT_WORD_IND_OFFSET;

            if (numDeps >= depAccumulator.length) {
              return -1;
            }
            depAccumulator[numDeps] = filledDep;
            numDeps++;
            depWasFilled = true;
          }
        }
      }

      if (!depWasFilled && Ints.contains(returnOriginalVars, objectArgNum)) {
        if (numDeps >= depAccumulator.length) {
          return -1;
        }
        int relabeledVarNum = returnVarsRelabeling[Ints.indexOf(returnOriginalVars, objectArgNum)];
        
        long relabeledDep = unfilledDependency - (objectArgNum << OBJECT_OFFSET);
        relabeledDep += (relabeledVarNum << OBJECT_OFFSET);
        
        depAccumulator[numDeps] = relabeledDep;
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

  public long predicateToLong(String predicate) {
    if (dependencyHeadType.canTakeValue(predicate)) {
      return dependencyHeadType.getValueIndex(predicate);
    } else {
      return -1;
    }
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

  public static long marshalFilledDependency(long objectNum, long argNum, long subjectNum,
      long objectWordInd, long subjectWordInd) {
    long value = 0L;
    value += (objectNum + MAX_ARG_NUM) << OBJECT_OFFSET;
    value += argNum << ARG_NUM_OFFSET;
    value += (subjectNum + MAX_ARG_NUM) << SUBJECT_OFFSET;
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

  public static int getSubjectArgNumFromDep(long depLong) {
    int subjectNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK);
    if (subjectNum >= MAX_ARG_NUM) {
      return -1;
    } else {
      return subjectNum;
    }
  }

  public static int getSubjectPredicateFromDep(long depLong) {
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

  public UnfilledDependency longToUnfilledDependency(long value) {
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