package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.chart.CcgBeamSearchChart;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.CcgExactHashTableChart;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.lexicon.CcgLexicon;
import com.jayantkrish.jklol.ccg.lexicon.LexiconScorer;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorHash;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IntMultimap;

/**
 * A chart parser for Combinatory Categorial Grammar (CCG).
 * 
 * @author jayantk
 */
public class CcgParser implements Serializable {

  private static final long serialVersionUID = 1L;

  // Parameters for encoding (filled and unfilled) dependency
  // structures in longs. These are the size of each field, in bits.
  private static final int PREDICATE_BITS = 16;
  private static final long PREDICATE_MASK = ~(-1L << PREDICATE_BITS);
  private static final int MAX_PREDICATES = 1 << PREDICATE_BITS;
  private static final int SYNTACTIC_CATEGORY_BITS = 13;
  private static final long SYNTACTIC_CATEGORY_MASK = ~(-1L << SYNTACTIC_CATEGORY_BITS);
  private static final int MAX_SYNTACTIC_CATEGORIES = 1 << SYNTACTIC_CATEGORY_BITS;
  private static final int ARG_NUM_BITS = 4;
  private static final long ARG_NUM_MASK = ~(-1L << ARG_NUM_BITS);
  private static final int WORD_IND_BITS = 7;
  private static final long WORD_IND_MASK = ~(-1L << WORD_IND_BITS);
  private static final int MAX_WORDS = 1 << WORD_IND_BITS;
  // The largest possible argument number.
  // This plays a dual role of the largest argument number possible in
  // a dependency structure, as well as the maximum number in a
  // syntactic categories head passing markup.
  private static final int MAX_ARG_NUM = 1 << ARG_NUM_BITS;
  // These are the locations of each field within the number. The
  // layout within the number is:
  // | sbj word ind | obj word ind | subj word | subj syntactic category | arg num | obj word |
  // 63 0
  private static final int OBJECT_OFFSET = 0;
  private static final int ARG_NUM_OFFSET = OBJECT_OFFSET + PREDICATE_BITS;
  private static final int SYNTACTIC_CATEGORY_OFFSET = ARG_NUM_OFFSET + ARG_NUM_BITS;
  private static final int SUBJECT_OFFSET = SYNTACTIC_CATEGORY_OFFSET + SYNTACTIC_CATEGORY_BITS;
  private static final int OBJECT_WORD_IND_OFFSET = SUBJECT_OFFSET + PREDICATE_BITS;
  private static final int SUBJECT_WORD_IND_OFFSET = OBJECT_WORD_IND_OFFSET + WORD_IND_BITS;
  
  // Parameters for encoding assignments as longs. Field
  // sizes are derived from dependencies (above).
  private static final int ASSIGNMENT_PREDICATE_OFFSET = 0;
  private static final int ASSIGNMENT_WORD_IND_OFFSET = ASSIGNMENT_PREDICATE_OFFSET + PREDICATE_BITS;
  private static final int ASSIGNMENT_VAR_NUM_OFFSET = ASSIGNMENT_WORD_IND_OFFSET + WORD_IND_BITS;

  private static final int VAR_NUM_BITS = 6;
  private static final long VAR_NUM_MASK = ~(-1L << VAR_NUM_BITS);

  // Parameters for controlling the maximum sizes of CCG chart entries
  private static final int MAX_CHART_ASSIGNMENTS = 100;
  private static final int MAX_CHART_DEPS = 100;
  private static final int MAX_CHART_VAR_INDEX = 100;

  // Default names for the variables in the syntactic distribution
  // built by buildSyntacticDistribution
  public static final String LEFT_SYNTAX_VAR_NAME = "leftSyntax";
  public static final String RIGHT_SYNTAX_VAR_NAME = "rightSyntax";
  public static final String PARENT_SYNTAX_VAR_NAME = "parentSyntax";
  public static final String PARENT_MOVE_SYNTAX_VAR_NAME = "parentMoveSyntax";

  public static final String UNARY_RULE_INPUT_VAR_NAME = "unaryRuleInputVar";
  public static final String UNARY_RULE_VAR_NAME = "unaryRuleVar";

  // Member variables ////////////////////////////////////

  // Weights on lexicon entries
  private final List<CcgLexicon> lexicons;
  private final List<LexiconScorer> lexiconScorers;
  
  private final VariableNumMap wordSkipWordVar;
  private final DiscreteFactor wordSkipFactor;

  // Weights on dependency structures.
  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencySyntaxVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
  private final VariableNumMap dependencyHeadPosVar;
  private final VariableNumMap dependencyArgPosVar;
  private final DiscreteFactor dependencyDistribution;
  private final DiscreteVariable dependencyHeadType;
  private final DiscreteVariable dependencySyntaxType;
  private final DiscreteVariable dependencyArgNumType;
  private final DiscreteVariable dependencyPosType;

  private final Tensor dependencyTensor;
  private final TensorHash dependencyTensorHash;
  private final long dependencyHeadOffset;
  private final long dependencySyntaxOffset;
  private final long dependencyArgNumOffset;
  private final long dependencyObjectOffset;
  private final long dependencyHeadPosOffset;
  private final long dependencyObjectPosOffset;

  // Weights on the distance between predicates and their arguments,
  // measured in terms of number of words, punctuation symbols, and
  // verbs. Each of these factors contains dependencyHeadVar and
  // dependencyArgNumVar, in addition to the corresponding distance
  // variable.
  private final VariableNumMap wordDistanceVar;
  private final DiscreteFactor wordDistanceFactor;
  private final Tensor wordDistanceTensor;
  private final TensorHash wordDistanceTensorHash;

  private final VariableNumMap puncDistanceVar;
  private final DiscreteFactor puncDistanceFactor;
  private final Tensor puncDistanceTensor;
  private final TensorHash puncDistanceTensorHash;
  private final Set<String> puncTagSet;

  private final VariableNumMap verbDistanceVar;
  private final DiscreteFactor verbDistanceFactor;
  private final Tensor verbDistanceTensor;
  private final TensorHash verbDistanceTensorHash;
  private final Set<String> verbTagSet;

  private final long distanceHeadOffset;
  private final long distanceSyntaxOffset;
  private final long distanceArgNumOffset;
  private final long distanceHeadPosOffset;
  private final long distanceDistanceOffset;

  public static final int MAX_DISTANCE = 3;
  public static final DiscreteVariable wordDistanceVarType;
  public static final DiscreteVariable puncDistanceVarType;
  public static final DiscreteVariable verbDistanceVarType;
  static {
    wordDistanceVarType = DiscreteVariable.sequence("wordDistance", MAX_DISTANCE + 1);
    puncDistanceVarType = DiscreteVariable.sequence("puncDistance", MAX_DISTANCE + 1);
    verbDistanceVarType = DiscreteVariable.sequence("verbDistance", MAX_DISTANCE + 1);
  }

  // Weights on binary combination rules.
  private final VariableNumMap leftSyntaxVar;
  private final VariableNumMap rightSyntaxVar;
  private final VariableNumMap combinatorVar;
  private final DiscreteVariable syntaxVarType;
  private final DiscreteVariable combinatorVarType;
  private final DiscreteFactor binaryRuleDistribution;
  
  // Unary type changing/raising rules.
  private final VariableNumMap unaryRuleInputVar;
  private final VariableNumMap unaryRuleVar;
  private final int unaryRuleVarNum;
  private final DiscreteFactor unaryRuleFactor;
  private final DiscreteVariable unaryRuleVarType;
  private final Tensor unaryRuleTensor;

  // Weights on binary combination rules incorporating head information
  private final VariableNumMap headedBinaryPredicateVar;
  private final VariableNumMap headedBinaryPosVar;
  private final DiscreteFactor headedBinaryRuleDistribution;
  private final long headedBinaryRuleCombinatorOffset;
  private final long headedBinaryRulePredicateOffset;
  private final long headedBinaryRulePosOffset;
  private final Tensor headedBinaryRuleTensor;
  private final TensorHash headedBinaryRuleTensorHash;

  // Indicator tensor specifying possible ways to combine pairs of
  // syntactic categories.
  private final VariableNumMap searchMoveVar;
  private final DiscreteVariable searchMoveType;
  private final DiscreteFactor compiledSyntaxDistribution;

  // Weights on the syntactic category of the root of the CCG parse.
  private final VariableNumMap rootSyntaxVar;
  private final VariableNumMap rootPredicateVar;
  private final VariableNumMap rootPosVar;
  // Defined solely over rootSyntaxVar
  private final DiscreteFactor rootSyntaxDistribution;
  // Defined over all three of the above variables
  private final DiscreteFactor headedRootSyntaxDistribution;
  private final long headedRootSyntaxOffset;
  private final long headedRootPredicateOffset;
  private final long headedRootPosOffset;

  // All predicates used in CCG rules.
  private final Set<Long> predicatesInRules;

  private final boolean normalFormOnly;

  public CcgParser(List<CcgLexicon> lexicons, List<LexiconScorer> lexiconScorers,
      VariableNumMap wordSkipWordVar, DiscreteFactor wordSkipFactor,
      VariableNumMap dependencyHeadVar, VariableNumMap dependencySyntaxVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap dependencyArgPosVar,
      DiscreteFactor dependencyDistribution, VariableNumMap wordDistanceVar,
      DiscreteFactor wordDistanceFactor, VariableNumMap puncDistanceVar,
      DiscreteFactor puncDistanceFactor, Set<String> puncTagSet, VariableNumMap verbDistanceVar,
      DiscreteFactor verbDistanceFactor, Set<String> verbTagSet, VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      DiscreteFactor binaryRuleDistribution, VariableNumMap unaryRuleInputVar,
      VariableNumMap unaryRuleVar, DiscreteFactor unaryRuleFactor, VariableNumMap headedBinaryPredicateVar,
      VariableNumMap headedBinaryPosVar, DiscreteFactor headedBinaryRuleDistribution,
      VariableNumMap searchMoveVar, DiscreteFactor compiledSyntaxDistribution,
      VariableNumMap rootSyntaxVar, VariableNumMap rootPredicateVar, VariableNumMap rootPosVar, 
      DiscreteFactor rootSyntaxDistribution, DiscreteFactor headedRootSyntaxDistribution,
      boolean normalFormOnly) {
    this.lexicons = ImmutableList.copyOf(lexicons);
    this.lexiconScorers = ImmutableList.copyOf(lexiconScorers);
    
    this.wordSkipWordVar = wordSkipWordVar;
    this.wordSkipFactor = wordSkipFactor;
    // Both must be null or non null.
    Preconditions.checkArgument(!(wordSkipFactor == null ^ wordSkipWordVar == null));

    Preconditions.checkArgument(dependencyDistribution.getVars().equals(VariableNumMap.unionAll(
        dependencyHeadVar, dependencySyntaxVar, dependencyArgNumVar, dependencyArgVar,
        dependencyHeadPosVar, dependencyArgPosVar)));
    List<Integer> dependencyVarNums = Ints.asList(new int[] {
        dependencyHeadVar.getOnlyVariableNum(), dependencySyntaxVar.getOnlyVariableNum(),
        dependencyArgNumVar.getOnlyVariableNum(), dependencyArgVar.getOnlyVariableNum(),
        dependencyHeadPosVar.getOnlyVariableNum(),  dependencyArgPosVar.getOnlyVariableNum()});
    Preconditions.checkArgument(Ordering.natural().isOrdered(dependencyVarNums),
        "Variables of the dependency distribution are given in the wrong order: " + dependencyVarNums);
    this.dependencyHeadVar = dependencyHeadVar;
    this.dependencySyntaxVar = dependencySyntaxVar;
    this.dependencyArgNumVar = dependencyArgNumVar;
    this.dependencyArgVar = dependencyArgVar;
    this.dependencyHeadPosVar = dependencyHeadPosVar;
    this.dependencyArgPosVar = dependencyArgPosVar;
    this.dependencyDistribution = dependencyDistribution;
    this.dependencyHeadType = dependencyHeadVar.getDiscreteVariables().get(0);
    this.dependencySyntaxType = dependencySyntaxVar.getDiscreteVariables().get(0);
    this.dependencyArgNumType = dependencyArgNumVar.getDiscreteVariables().get(0);
    this.dependencyPosType = dependencyHeadPosVar.getDiscreteVariables().get(0);
    Preconditions.checkArgument(dependencyArgPosVar.getDiscreteVariables().get(0).equals(dependencyPosType));
    // TODO: This check can be made unnecessary by fixing the
    // representation of unfilled dependencies as longs. Right now, the
    // representation requires this condition to hold.
    for (int i = 0; i < dependencyArgNumType.numValues(); i++) {
      Preconditions.checkArgument((int) ((Integer) dependencyArgNumType.getValue(i)) == i);
    }
    
    // Check that the encoding used for dependencies has enough capacity
    // to represent all possible dependencies.
    Preconditions.checkArgument(dependencyHeadType.numValues() + MAX_ARG_NUM < MAX_PREDICATES);
    Preconditions.checkArgument(dependencyArgNumType.numValues() < MAX_ARG_NUM);
    Preconditions.checkArgument(dependencySyntaxType.numValues() < MAX_SYNTACTIC_CATEGORIES);


    DiscreteVariable dependencyArgType = dependencyArgVar.getDiscreteVariables().get(0);
    Preconditions.checkArgument(dependencyHeadType.equals(dependencyArgType));
    this.dependencyTensor = dependencyDistribution.getWeights();
    this.dependencyTensorHash = dependencyTensor.toHash();
    this.dependencyHeadOffset = dependencyTensor.getDimensionOffsets()[0];
    this.dependencySyntaxOffset = dependencyTensor.getDimensionOffsets()[1];
    this.dependencyArgNumOffset = dependencyTensor.getDimensionOffsets()[2];
    this.dependencyObjectOffset = dependencyTensor.getDimensionOffsets()[3];
    this.dependencyHeadPosOffset = dependencyTensor.getDimensionOffsets()[4];
    this.dependencyObjectPosOffset = dependencyTensor.getDimensionOffsets()[5];

    VariableNumMap distanceDependencyVars = VariableNumMap.unionAll(dependencyHeadVar, dependencySyntaxVar,
        dependencyArgNumVar, dependencyHeadPosVar);
    this.wordDistanceVar = wordDistanceVar;
    this.wordDistanceFactor = wordDistanceFactor;
    VariableNumMap expectedWordVars = distanceDependencyVars.union(wordDistanceVar);
    Preconditions.checkArgument(expectedWordVars.equals(wordDistanceFactor.getVars()));
    this.wordDistanceTensor = wordDistanceFactor.getWeights();
    this.wordDistanceTensorHash = wordDistanceTensor.toHash();

    this.puncDistanceVar = puncDistanceVar;
    this.puncDistanceFactor = puncDistanceFactor;
    VariableNumMap expectedPuncVars = distanceDependencyVars.union(puncDistanceVar);
    Preconditions.checkArgument(expectedPuncVars.equals(puncDistanceFactor.getVars()));
    this.puncDistanceTensor = puncDistanceFactor.getWeights();
    this.puncDistanceTensorHash = puncDistanceTensor.toHash();
    this.puncTagSet = puncTagSet;

    this.verbDistanceVar = verbDistanceVar;
    this.verbDistanceFactor = verbDistanceFactor;
    VariableNumMap expectedVerbVars = distanceDependencyVars.union(verbDistanceVar);
    Preconditions.checkArgument(expectedVerbVars.equals(verbDistanceFactor.getVars()));
    this.verbDistanceTensor = verbDistanceFactor.getWeights();
    this.verbDistanceTensorHash = verbDistanceTensor.toHash();
    this.verbTagSet = verbTagSet;

    this.distanceHeadOffset = verbDistanceTensor.getDimensionOffsets()[0];
    this.distanceSyntaxOffset = verbDistanceTensor.getDimensionOffsets()[1];
    this.distanceArgNumOffset = verbDistanceTensor.getDimensionOffsets()[2];
    this.distanceHeadPosOffset = verbDistanceTensor.getDimensionOffsets()[3];
    this.distanceDistanceOffset = verbDistanceTensor.getDimensionOffsets()[4];

    this.leftSyntaxVar = Preconditions.checkNotNull(leftSyntaxVar);
    this.rightSyntaxVar = Preconditions.checkNotNull(rightSyntaxVar);
    this.combinatorVar = Preconditions.checkNotNull(parentSyntaxVar);
    Preconditions.checkArgument(binaryRuleDistribution.getVars().equals(
        VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, parentSyntaxVar)));
    this.syntaxVarType = leftSyntaxVar.getDiscreteVariables().get(0);
    this.combinatorVarType = parentSyntaxVar.getDiscreteVariables().get(0);
    this.binaryRuleDistribution = binaryRuleDistribution;

    this.unaryRuleInputVar = Preconditions.checkNotNull(unaryRuleInputVar);
    this.unaryRuleVar = Preconditions.checkNotNull(unaryRuleVar);
    this.unaryRuleVarNum = unaryRuleVar.getOnlyVariableNum();
    this.unaryRuleFactor = Preconditions.checkNotNull(unaryRuleFactor);
    this.unaryRuleVarType = unaryRuleVar.getDiscreteVariables().get(0);
    this.unaryRuleTensor = unaryRuleFactor.getWeights();

    this.headedBinaryPredicateVar = headedBinaryPredicateVar;
    Preconditions.checkArgument(headedBinaryPredicateVar.getDiscreteVariables().get(0).equals(dependencyHeadType));
    this.headedBinaryPosVar = headedBinaryPosVar;
    Preconditions.checkArgument(headedBinaryPosVar.getDiscreteVariables().get(0).equals(dependencyPosType));
    this.headedBinaryRuleDistribution = headedBinaryRuleDistribution;
    Preconditions.checkArgument(headedBinaryRuleDistribution.getVars().equals(
        VariableNumMap.unionAll(binaryRuleDistribution.getVars(), headedBinaryPredicateVar, headedBinaryPosVar)));
    
    headedBinaryRuleTensor = headedBinaryRuleDistribution.getWeights();
    headedBinaryRuleTensorHash = headedBinaryRuleTensor.toHash();
    long[] headedBinaryOffsets = headedBinaryRuleTensor.getDimensionOffsets();
    headedBinaryRuleCombinatorOffset = headedBinaryOffsets[2];
    headedBinaryRulePredicateOffset = headedBinaryOffsets[3];
    headedBinaryRulePosOffset = headedBinaryOffsets[4];

    this.searchMoveVar = Preconditions.checkNotNull(searchMoveVar);
    this.searchMoveType = (DiscreteVariable) searchMoveVar.getOnlyVariable();
    Preconditions.checkArgument(compiledSyntaxDistribution.getVars().equals(
        VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, searchMoveVar)));
    this.compiledSyntaxDistribution = Preconditions.checkNotNull(compiledSyntaxDistribution);

    this.rootSyntaxVar = Preconditions.checkNotNull(rootSyntaxVar);
    this.rootPredicateVar = Preconditions.checkNotNull(rootPredicateVar);
    this.rootPosVar = Preconditions.checkNotNull(rootPosVar);
    this.rootSyntaxDistribution = Preconditions.checkNotNull(rootSyntaxDistribution);
    Preconditions.checkArgument(rootSyntaxDistribution.getVars().equals(rootSyntaxVar));
    this.headedRootSyntaxDistribution = Preconditions.checkNotNull(headedRootSyntaxDistribution);
    Preconditions.checkArgument(headedRootSyntaxDistribution.getVars().equals(
        VariableNumMap.unionAll(rootSyntaxVar, rootPredicateVar, rootPosVar)));
    long[] headedRootSyntaxOffsets = headedRootSyntaxDistribution.getWeights().getDimensionOffsets();
    this.headedRootSyntaxOffset = headedRootSyntaxOffsets[0];
    this.headedRootPredicateOffset = headedRootSyntaxOffsets[1];
    this.headedRootPosOffset = headedRootSyntaxOffsets[2];

    // Cache predicates in rules.
    predicatesInRules = Sets.newHashSet();
    List<Object> combinatorValues = parentSyntaxVar.getDiscreteVariables().get(0).getValues();
    for (Object combinator : combinatorValues) {
      for (String predicate : ((Combinator) combinator).getSubjects()) {
        predicatesInRules.add((long) dependencyHeadType.getValueIndex(predicate));
      }
    }
    for (Object rule : unaryRuleVar.getDiscreteVariables().get(0).getValues()) {
      for (String predicate : ((UnaryCombinator) rule).getUnaryRule().getSubjects()) {
        predicatesInRules.add((long) dependencyHeadType.getValueIndex(predicate));
      }
    }

    this.normalFormOnly = normalFormOnly;
  }

  public List<CcgLexicon> getLexicons() {
    return lexicons;
  }
  
  /**
   * Creates a new CCG parser by replacing a lexicon of
   * this parser with {@code newLexicon}.
   * 
   * @param index
   * @param newLexicon
   * @return
   */
  public CcgParser replaceLexicon(int index, CcgLexicon newLexicon) {
    List<CcgLexicon> newLexicons = Lists.newArrayList(lexicons);
    newLexicons.set(index, newLexicon);

    return new CcgParser(newLexicons, lexiconScorers, wordSkipWordVar, wordSkipFactor,
        dependencyHeadVar, dependencySyntaxVar, dependencyArgNumVar, dependencyArgVar,
        dependencyHeadPosVar, dependencyArgPosVar, dependencyDistribution, wordDistanceVar,
        wordDistanceFactor, puncDistanceVar, puncDistanceFactor, puncTagSet, verbDistanceVar,
        verbDistanceFactor, verbTagSet, leftSyntaxVar, rightSyntaxVar, combinatorVar,
        binaryRuleDistribution, unaryRuleInputVar, unaryRuleVar, unaryRuleFactor,
        headedBinaryPredicateVar, headedBinaryPosVar, headedBinaryRuleDistribution,
        searchMoveVar, compiledSyntaxDistribution, rootSyntaxVar, rootPredicateVar,
        rootPosVar, rootSyntaxDistribution, headedRootSyntaxDistribution, normalFormOnly);
  }
  
  public DiscreteFactor getWordSkipFactor() {
    return wordSkipFactor;
  }
  
  public boolean canSkipWords() {
    return wordSkipFactor != null;
  }
  
  public double getWordSkipProbability(String lowercaseWord) {
    Assignment assignment = wordSkipWordVar.outcomeArrayToAssignment(Arrays.asList(lowercaseWord));
    if (wordSkipWordVar.isValidAssignment(assignment)) {
      return wordSkipFactor.getUnnormalizedProbability(assignment);
    } else {
      return 1.0;
    }
  }

  public boolean isPossibleDependencyStructure(DependencyStructure dependency, List<String> posTags) {
    return getDependencyStructureLogProbability(dependency, posTags) != Double.NEGATIVE_INFINITY;
  }

  /**
   * Gets the log probability of a dependency structure, which is the
   * weight added into a parse's log probability when
   * {@code dependency} occurs.
   * 
   * @param dependency
   * @return
   */
  public double getDependencyStructureLogProbability(DependencyStructure dependency,
      List<String> posTags) {
    int headIndex = dependency.getHeadWordIndex();
    int objectIndex = dependency.getObjectWordIndex();
    Assignment assignment = Assignment.unionAll(
        dependencyHeadVar.outcomeArrayToAssignment(dependency.getHead()),
        dependencySyntaxVar.outcomeArrayToAssignment(dependency.getHeadSyntacticCategory()),
        dependencyArgNumVar.outcomeArrayToAssignment(dependency.getArgIndex()),
        dependencyArgVar.outcomeArrayToAssignment(dependency.getObject()),
        dependencyHeadPosVar.outcomeArrayToAssignment(posTags.get(headIndex)),
        dependencyArgPosVar.outcomeArrayToAssignment(posTags.get(objectIndex)));

    if (!dependencyDistribution.getVars().isValidAssignment(assignment)) {
      return Double.NEGATIVE_INFINITY;
    } else {
      return dependencyDistribution.getUnnormalizedLogProbability(assignment);
    }
  }

  public SetMultimap<SyntacticCategory, HeadedSyntacticCategory> getSyntacticCategoryMap() {
    SetMultimap<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap = HashMultimap.create();
    for (Object syntacticCategory : leftSyntaxVar.getDiscreteVariables().get(0).getValues()) {
      HeadedSyntacticCategory headedCat = (HeadedSyntacticCategory) syntacticCategory;
      syntacticCategoryMap.put(headedCat.getSyntax()
          .assignAllFeatures(SyntacticCategory.DEFAULT_FEATURE_VALUE), headedCat);
    }
    return syntacticCategoryMap;
  }

  public DiscreteVariable getSyntaxVarType() {
    return syntaxVarType;
  }

  public DiscreteVariable getPredicateVarType() {
    return dependencyHeadType;
  }

  public DiscreteFactor getSyntaxDistribution() {
    return compiledSyntaxDistribution;
  }

  public CcgParser replaceSyntaxDistribution(DiscreteFactor newCompiledSyntaxDistribution) {
    return new CcgParser(lexicons, lexiconScorers, wordSkipWordVar, wordSkipFactor,
        dependencyHeadVar, dependencySyntaxVar, dependencyArgNumVar, dependencyArgVar,
        dependencyHeadPosVar, dependencyArgPosVar, dependencyDistribution,
        wordDistanceVar, wordDistanceFactor, puncDistanceVar, puncDistanceFactor, puncTagSet,
        verbDistanceVar, verbDistanceFactor, verbTagSet, leftSyntaxVar, rightSyntaxVar, combinatorVar,
        binaryRuleDistribution, unaryRuleInputVar, unaryRuleVar, unaryRuleFactor, 
        headedBinaryPredicateVar, headedBinaryPosVar, headedBinaryRuleDistribution,
        searchMoveVar, newCompiledSyntaxDistribution, rootSyntaxVar, rootPredicateVar, rootPosVar, 
        rootSyntaxDistribution, headedRootSyntaxDistribution, normalFormOnly);
  }

  public DiscreteFactor getBinaryRuleDistribution() {
    return binaryRuleDistribution;
  }

  public boolean isNormalFormOnly() {
    return normalFormOnly;
  }

  /**
   * Performs a beam search to find the best CCG parses of
   * {@code input}. Note that this is an approximate inference
   * strategy, and the returned parses may not be the best parses if
   * at any point during the search more than {@code beamSize} parse
   * trees exist for a span of the sentence.
   * 
   * @param input
   * @param beamSize
   * @param log
   * @return {@code beamSize} best parses for {@code terminals}.
   */
  public List<CcgParse> beamSearch(AnnotatedSentence input, int beamSize, LogFunction log) {
    return beamSearch(input, beamSize, null, log, -1, Integer.MAX_VALUE, 1);
  }

  public List<CcgParse> beamSearch(AnnotatedSentence input, int beamSize) {
    return beamSearch(input, beamSize, new NullLogFunction());
  }

  /**
   * 
   * @param input
   * @param beamSize
   * @param beamFilter May be {@code null}, in which case all beam
   * entries are retained.
   * @param log
   * @param maxParseTimeMillis (Approximate) maximum amount of time to
   * spend parsing. Returns an empty list of parses if the time limit
   * is exceeded. If negative, there is no time limit.
   * @param maxChartSize maximum number of chart entries to create during
   * parsing.
   * @return
   */
  public List<CcgParse> beamSearch(AnnotatedSentence input, int beamSize, ChartCost beamFilter,
      LogFunction log, long maxParseTimeMillis, int maxChartSize, int numThreads) {
    CcgBeamSearchChart chart = new CcgBeamSearchChart(input, maxChartSize, beamSize);
    parseCommon(chart, input, beamFilter, log, maxParseTimeMillis, numThreads);
    
    if (chart.isFinishedParsing()) {
      int numParses = Math.min(beamSize, chart.getNumChartEntriesForSpan(0, chart.size() - 1));
      return chart.decodeBestParsesForSpan(0, chart.size() - 1, numParses, this);
    } else {
      System.out.println("CCG Parser Timeout");
      return Lists.newArrayList();
    }
  }

  /**
   * 
   * @param input sentence to parse.
   * @param beamFilter May be {@code null}, in which case no chart entries are pruned.
   * @param log May be {@code null} to suppress logging output.
   * @param maxParseTimeMillis maximum parsing time, in milliseconds. If parsing
   * exceeds this time, it is cancelled and null is returned.
   * @param maxChartSize maximum number of entries allowed in the parse chart. If the
   * chart exceeds this size, parsing is cancelled and null is returned.
   * @param numThreads number of threads to use for parsing.
   * @return
   */
  public CcgParse parse(AnnotatedSentence input, ChartCost beamFilter, LogFunction log,
      long maxParseTimeMillis, int maxChartSize, int numThreads) {
    CcgExactHashTableChart chart = new CcgExactHashTableChart(input, maxChartSize);
    parseCommon(chart, input, beamFilter, log, maxParseTimeMillis, numThreads);

    if (chart.isFinishedParsing()) {
      return chart.decodeBestParseForSpan(0, chart.size() - 1, this);
    } else {
      System.out.println("CCG Parser Timeout");
      return null;
    }
  }
  
  /**
   * Simplified version of {@link #parse} with sane default arguments.
   *  
   * @param input
   * @return
   */
  public CcgParse parse(AnnotatedSentence input) {
    return parse(input, null, new NullLogFunction(), -1, Integer.MAX_VALUE, 1);
  }

  public void parseCommon(CcgChart chart, AnnotatedSentence input, ChartCost beamFilter,
      LogFunction log, long maxParseTimeMillis, int numThreads) {
    if (log == null) {
      log = new NullLogFunction();
    }
    
    Preconditions.checkState(input.getWords().size() <= MAX_WORDS,
        "Maximum sentence length is %s", MAX_WORDS);

    log.startTimer("ccg_parse/initialize_chart");
    initializeChart(chart, input, beamFilter);
    initializeChartTerminals(chart, input, wordSkipFactor != null);
    log.stopTimer("ccg_parse/initialize_chart");

    log.startTimer("ccg_parse/calculate_inside_beam");
    boolean finishedParsing = false;
    if (numThreads <= 1) {
      finishedParsing = calculateInsideBeamSingleThreaded(chart, log, maxParseTimeMillis);
    } else {
      finishedParsing = calculateInsideBeamParallel(chart, log, maxParseTimeMillis, numThreads);
    }
    log.stopTimer("ccg_parse/calculate_inside_beam");

    if (finishedParsing) {
      reweightRootEntries(chart);
    }
    chart.setFinishedParsing(finishedParsing);
  }

  public void initializeChart(CcgChart chart, AnnotatedSentence input, ChartCost chartFilter) {
    int numWords = input.size();
    int[] puncCounts = computeDistanceCounts(input.getPosTags(), puncTagSet);
    int[] verbCounts = computeDistanceCounts(input.getPosTags(), verbTagSet);

    int[] wordDistances = new int[numWords * numWords];
    int[] puncDistances = new int[numWords * numWords];
    int[] verbDistances = new int[numWords * numWords];
    for (int i = 0; i < numWords; i++) {
      for (int j = 0; j < numWords; j++) {
        wordDistances[(i * numWords) + j] = computeWordDistance(i, j);
        puncDistances[(i * numWords) + j] = computeArrayDistance(puncCounts, i, j);
        verbDistances[(i * numWords) + j] = computeArrayDistance(verbCounts, i, j);
      }
    }

    chart.setPosTagsInt(getPosTagsInt(input.getPosTags()));
    chart.setWordDistances(wordDistances);
    chart.setPuncDistances(puncDistances);
    chart.setVerbDistances(verbDistances);
    chart.setChartCost(chartFilter);

    // Create one accumulator per token in the sentence. This permits
    // parsing in parallel while reusing the accumulators.
    chart.setAssignmentVarIndexAccumulator(new int[input.size()][MAX_CHART_VAR_INDEX]);
    chart.setAssignmentAccumulator(new long[input.size()][MAX_CHART_ASSIGNMENTS]);
    chart.setFilledDepAccumulator(new long[input.size()][MAX_CHART_DEPS]);
    chart.setUnfilledDepVarIndexAccumulator(new int[input.size()][MAX_CHART_VAR_INDEX]);
    chart.setUnfilledDepAccumulator(new long[input.size()][MAX_CHART_DEPS]);
    chart.setDepLongCache(new long[input.size()]);
    chart.setDepProbCache(new double[input.size()]);
  }

  public void initializeChartTerminals(CcgChart chart, AnnotatedSentence sentence, boolean wordSkip) {
    Preconditions.checkArgument(wordSkip == false || wordSkipFactor != null);
    
    for (int k = 0; k < lexicons.size(); k++) {
      CcgLexicon lexicon = lexicons.get(k);
      if (wordSkip) {
        lexicon.initializeChart(chart, sentence, this, k, wordSkipWordVar, wordSkipFactor);
      } else {
        lexicon.initializeChart(chart, sentence, this, k, null, null);
      }
    }
  }

  /**
   * Adds lexicon entry {@code category} to {@code chart}. This
   * method should be used by instances of {@code CcgLexicon}
   * to initialize the lexicon entries of the parser.
   *  
   * @param chart
   * @param trigger
   * @param category
   * @param lexiconProb
   * @param spanStart
   * @param spanEnd
   * @param triggerSpanStart
   * @param triggerSpanEnd
   * @param sentence
   * @param lexiconNum
   */
  public void addLexiconEntryToChart(CcgChart chart, Object trigger, CcgCategory category,
      double lexiconProb, int spanStart, int spanEnd, int triggerSpanStart, int triggerSpanEnd,
      AnnotatedSentence sentence, int lexiconNum) {

    for (LexiconScorer lexiconScorer : lexiconScorers) {
      lexiconProb *= lexiconScorer.getCategoryWeight(triggerSpanStart, triggerSpanEnd, sentence, category);
    }

    // Add all possible chart entries to the ccg chart.
    ChartEntry chartEntry = ccgCategoryToChartEntry(trigger, category, spanStart, spanEnd,
        triggerSpanStart, triggerSpanEnd, lexiconNum);
    chart.addChartEntryForSpan(chartEntry, lexiconProb, spanStart, spanEnd, syntaxVarType);
  }

  private ChartEntry ccgCategoryToChartEntry(Object trigger, CcgCategory result,
      int spanStart, int spanEnd, int triggerSpanStart, int triggerSpanEnd, int lexiconIndex) {
    // Assign each predicate in this category a unique word index.
    List<Long> assignments = Lists.newArrayList();
    List<Set<String>> values = result.getAssignment();
    int[] semanticVariables = result.getSemanticVariables();
    int maxSemanticVariable = Ints.max(semanticVariables);
    Preconditions.checkState(maxSemanticVariable == semanticVariables.length - 1);

    int[] assignmentVarIndex = new int[semanticVariables.length + 1];
    int numFilled = 0;
    for (int i = 0; i < values.size(); i++) {
      assignmentVarIndex[i] = numFilled;
      Preconditions.checkState(semanticVariables[i] == i);
      for (String value : values.get(i)) {
        long assignment = marshalAssignment(semanticVariables[i],
            dependencyHeadType.getValueIndex(value), triggerSpanEnd);
        assignments.add(assignment);
        numFilled++;
      }
    }
    assignmentVarIndex[assignmentVarIndex.length - 1] = numFilled;

    List<UnfilledDependency> filledDepsAccumulator = Lists.newArrayList();
    List<UnfilledDependency> unfilledDeps = result.createUnfilledDependencies(triggerSpanEnd, filledDepsAccumulator);

    long[] unfilledDepsOrig = unfilledDependencyArrayToLongArray(unfilledDeps);
    long[] depArray = unfilledDependencyArrayToLongArray(filledDepsAccumulator);
    
    long[] unfilledDepArray = new long[unfilledDeps.size()];
    int[] unfilledDependencyVarIndex = new int[semanticVariables.length + 1];
    orderUnfilledDependencies(unfilledDepsOrig, unfilledDepArray, unfilledDependencyVarIndex);

    int syntaxAsInt = syntaxVarType.getValueIndex(result.getSyntax());
    int syntaxHeadVar = result.getSyntax().getHeadVariable();
    return new ChartEntry(syntaxAsInt, result.getSyntax().getUniqueVariables(), syntaxHeadVar, 
        result, trigger, lexiconIndex, null, assignmentVarIndex, Longs.toArray(assignments),
        unfilledDependencyVarIndex, unfilledDepArray, depArray, spanStart, spanEnd,
        triggerSpanStart, triggerSpanEnd);
  }

  public static int[] computeDistanceCounts(List<String> posTags, Set<String> tagSet) {
    int[] counts = new int[posTags.size()];
    int count = 0;

    for (int i = 0; i < posTags.size(); i++) {
      if (tagSet.contains(posTags.get(i))) {
        count++;
      }
      counts[i] = count;
    }
    return counts;
  }

  public static final int computeArrayDistance(int[] counts, int word1Index, int word2Index) {
    int leftWordIndex = word1Index < word2Index ? word1Index : word2Index;
    int rightWordIndex = word1Index < word2Index ? word2Index : word1Index;
    int distance = leftWordIndex != rightWordIndex ? counts[rightWordIndex - 1] - counts[leftWordIndex] : 0;

    return distance < CcgParser.MAX_DISTANCE ? distance : CcgParser.MAX_DISTANCE;
  }

  public static final int computeWordDistance(int word1Index, int word2Index) {
    int leftWordIndex = word1Index < word2Index ? word1Index : word2Index;
    int rightWordIndex = word1Index < word2Index ? word2Index : word1Index;
    int distance = leftWordIndex != rightWordIndex ? (rightWordIndex - 1) - leftWordIndex : 0;

    return distance < CcgParser.MAX_DISTANCE ? distance : CcgParser.MAX_DISTANCE;
  }
  
  private int[] getPosTagsInt(List<String> posTags) {
    int[] posTagsInt = new int[posTags.size()];
    for (int i = 0; i < posTags.size(); i++) {
      posTagsInt[i] = dependencyPosType.getValueIndex(posTags.get(i));
    }
    return posTagsInt;
  }

  /**
   * Updates entries in the beam for the root node with a factor for
   * the root syntactic category and any unary rules.
   * 
   * @param chart
   */
  public void reweightRootEntries(CcgChart chart) {
    int spanStart = 0;
    int spanEnd = chart.size() - 1;
    int numChartEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd);

    // Apply unary rules.
    ChartEntry[] entries = CcgBeamSearchChart.copyChartEntryArray(chart.getChartEntriesForSpan(spanStart, spanEnd),
        numChartEntries);
    double[] probs = ArrayUtils.copyOf(chart.getChartEntryProbsForSpan(spanStart, spanEnd),
        numChartEntries);
    chart.clearChartEntriesForSpan(spanStart, spanEnd);
    for (int i = 0; i < entries.length; i++) {
      chart.addChartEntryForSpan(entries[i], probs[i], spanStart, spanEnd, syntaxVarType);
      applyUnaryRules(chart, entries[i], probs[i], spanStart, spanEnd);
    }
    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);

    // Apply root factor.
    numChartEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd);
    entries = CcgBeamSearchChart.copyChartEntryArray(chart.getChartEntriesForSpan(spanStart, spanEnd),
        numChartEntries);
    probs = ArrayUtils.copyOf(chart.getChartEntryProbsForSpan(spanStart, spanEnd),
        numChartEntries);
    chart.clearChartEntriesForSpan(spanStart, spanEnd);

    for (int i = 0; i < entries.length; i++) {
      ChartEntry entry = entries[i];
      double rootProb = scoreRootEntry(entry, chart);
      chart.addChartEntryForSpan(entry, probs[i] * rootProb, spanStart, spanEnd,
          syntaxVarType);
    }
    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);
  }
  
  public double scoreRootEntry(ChartEntry entry, CcgChart chart) {
    Tensor rootSyntaxTensor = rootSyntaxDistribution.getWeights();
    Tensor headedRootTensor = headedRootSyntaxDistribution.getWeights();
    int[] currentPosTags = chart.getPosTagsInt();

    double rootProb = rootSyntaxTensor.get(entry.getHeadedSyntax());

    double headedRootProb = 1.0;
    int headSyntax = entry.getHeadedSyntax();
    int headVar = entry.getHeadVariable();
    long[] assignments = entry.getAssignments();
    for (int j = 0; j < assignments.length; j++) {
      long assignment = assignments[j];
      int varNum = (int) ((assignment >> ASSIGNMENT_VAR_NUM_OFFSET) & VAR_NUM_MASK); 
      if (varNum == headVar) {
        long predicate = (assignment >> ASSIGNMENT_PREDICATE_OFFSET) & PREDICATE_MASK;
        int wordIndex = (int) ((assignment >> ASSIGNMENT_WORD_IND_OFFSET) & WORD_IND_MASK);
        int posTag = currentPosTags[wordIndex];

        long headedRootKeyNum = (headSyntax * headedRootSyntaxOffset)
            + (predicate * headedRootPredicateOffset)
            + (posTag * headedRootPosOffset);
        headedRootProb *= headedRootTensor.get(headedRootKeyNum);
      }
    }

    return rootProb * headedRootProb;
  }

  /**
   * Performs a beam search over possible CCG parses given a
   * {@code chart} initialized with entries for all terminals.
   * 
   * @param chart
   * @param log
   */
  public boolean calculateInsideBeamSingleThreaded(CcgChart chart, LogFunction log, long maxParseTimeMillis) {
    int chartSize = chart.size();
    long currentTime = 0;
    long endTime = System.currentTimeMillis() + maxParseTimeMillis;
    for (int spanSize = 1; spanSize < chartSize; spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chartSize; spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart, log);
        
        if (maxParseTimeMillis >= 0) {
          currentTime = System.currentTimeMillis();
          if (currentTime > endTime) {
            return false;
          }
        }
        
        if (chart.getTotalNumChartEntries() > chart.getMaxChartEntries()) {
          return false;
        }
        // System.out.println(spanStart + "." + spanEnd + " : " +
        // chart.getNumChartEntriesForSpan(spanStart, spanEnd));
      }
    }
    return true;
  }

  public boolean calculateInsideBeamParallel(CcgChart chart, LogFunction log, long maxParseTimeMillis,
      int numThreads) {
    int chartSize = chart.size();
    long currentTime = 0;
    long endTime = System.currentTimeMillis() + maxParseTimeMillis;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    try {
      List<Future<Void>> results = Lists.newArrayListWithCapacity(chartSize);
      for (int spanSize = 1; spanSize < chartSize; spanSize++) {
        results.clear();
        for (int spanStart = 0; spanStart + spanSize < chartSize; spanStart++) {
          int spanEnd = spanStart + spanSize;
          results.add(executor.submit(new CalculateInsideBeamCallable(this, chart, spanStart, spanEnd, log)));
        }

        // Wait for the current set of spans to finish.
        for (Future<Void> result : results) {
          result.get();
        }

        if (maxParseTimeMillis >= 0) {
          currentTime = System.currentTimeMillis();
          if (currentTime > endTime) {
            return false;
          }
        }
        
        if (chart.getTotalNumChartEntries() > chart.getMaxChartEntries()) {
          return false;
        }        
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      e.printStackTrace();
      e.getCause().printStackTrace();
      throw new RuntimeException(e);
    } finally {
      executor.shutdown();
    }
    return true;
  }

  private void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart, LogFunction log) {
    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j only gets used if we allow the skipping of terminals.
      ChartEntry[] leftTrees = chart.getChartEntriesForSpan(spanStart, spanStart + i);
      double[] leftProbs = chart.getChartEntryProbsForSpan(spanStart, spanStart + i);
      IntMultimap leftTypes = chart.getChartEntriesBySyntacticCategoryForSpan(spanStart, spanStart + i);

      ChartEntry[] rightTrees = chart.getChartEntriesForSpan(spanStart + i + 1, spanEnd);
      double[] rightProbs = chart.getChartEntryProbsForSpan(spanStart + i + 1, spanEnd);
      IntMultimap rightTypes = chart.getChartEntriesBySyntacticCategoryForSpan(spanStart + i + 1, spanEnd);

      applySearchMoves(chart, spanStart, spanStart + i, spanStart + i + 1, spanEnd,
          leftTrees, leftProbs, leftTypes, rightTrees, rightProbs, rightTypes, log);
    }

    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);
  }
  
  public final void applySearchMoves(CcgChart chart, int leftSpanStart, int leftSpanEnd,
      int rightSpanStart, int rightSpanEnd, ChartEntry[] leftTrees, double[] leftProbs,
      IntMultimap leftTypes, ChartEntry[] rightTrees, double[] rightProbs, IntMultimap rightTypes,
      LogFunction log) {
    
    Tensor binaryRuleTensor = binaryRuleDistribution.getWeights();
    SparseTensor syntaxDistributionTensor = (SparseTensor) compiledSyntaxDistribution.getWeights();
    long[] syntaxKeyNums = syntaxDistributionTensor.getKeyNums();
    long[] dimensionOffsets = syntaxDistributionTensor.getDimensionOffsets();
    int tensorSize = syntaxDistributionTensor.size();

    if (leftTypes == null || rightTypes == null) {
      // At least one of the partial spans has no possible parses. This may
      // happen if some single-word spans have no lexicon entries.
      return;
    }

    // log.startTimer("ccg_parse/beam_loop");
    for (int leftType : leftTypes.keySetArray()) {
      long keyNumPrefix = leftType * dimensionOffsets[0]; // syntaxDistributionTensor.dimKeyPrefixToKeyNum(key);
      int index = syntaxDistributionTensor.getNearestIndex(keyNumPrefix);
      if (index == -1 || index >= tensorSize) {
        continue;
      }
      long maxKeyNum = keyNumPrefix + dimensionOffsets[0];
      long curKeyNum = syntaxKeyNums[index];

      while (curKeyNum < maxKeyNum && index < tensorSize) {
        int rightType = (int) (((curKeyNum - keyNumPrefix) / dimensionOffsets[1]) % dimensionOffsets[0]);
        if (rightTypes.containsKey(rightType)) {
          // Get the operation we're supposed to apply at this chart entry.
          CcgSearchMove searchMove = (CcgSearchMove) searchMoveType.getValue(
              (int) ((curKeyNum - keyNumPrefix) % dimensionOffsets[1]));

          // Apply the binary rule.
          double ruleProb = binaryRuleTensor.get(searchMove.getBinaryCombinatorKeyNum());

          for (int leftIndex : leftTypes.getArray(leftType)) {
            ChartEntry leftRoot = leftTrees[leftIndex];
            double leftProb = leftProbs[leftIndex];

            long leftUnaryKeyNum = searchMove.getLeftUnaryKeyNum();
            if (leftUnaryKeyNum != -1) {
              leftProb *= unaryRuleTensor.get(leftUnaryKeyNum);
            }

            for (int rightIndex : rightTypes.getArray(rightType)) {
              ChartEntry rightRoot = rightTrees[rightIndex];
              double rightProb = rightProbs[rightIndex];

              long rightUnaryKeyNum = searchMove.getRightUnaryKeyNum();
              if (rightUnaryKeyNum != -1) {
                rightProb *= unaryRuleTensor.get(searchMove.getRightUnaryKeyNum());
              }

              applyBinary(chart, leftSpanStart, leftSpanEnd, leftIndex, leftRoot, leftProb,
                  rightSpanStart, rightSpanEnd, rightIndex, rightRoot, rightProb, searchMove, ruleProb,
                  log);
            }
          } 
        }

        // Advance the iterator over rules.
        index++;
        if (index < tensorSize) {
          curKeyNum = syntaxKeyNums[index];
        }
      }
    }
    // log.stopTimer("ccg_parse/beam_loop");
  }

  public final void applyBinary(CcgChart chart,
      int leftSpanStart, int leftSpanEnd, int leftIndex, ChartEntry leftRoot, double leftProb,
      int rightSpanStart, int rightSpanEnd, int rightIndex, ChartEntry rightRoot, double rightProb,
      CcgSearchMove searchMove, double ruleProb, LogFunction log) {

    int[] assignmentVarIndexAccumulator = chart.getAssignmentVarIndexAccumulator()[leftSpanStart];
    long[] assignmentAccumulator = chart.getAssignmentAccumulator()[leftSpanStart];
    long[] filledDepAccumulator = chart.getFilledDepAccumulator()[leftSpanStart];
    int[] unfilledDepVarIndexAccumulator = chart.getUnfilledDepVarIndexAccumulator()[leftSpanStart];
    long[] unfilledDepAccumulator = chart.getUnfilledDepAccumulator()[leftSpanStart];

    Combinator resultCombinator = searchMove.getBinaryCombinator();
    int resultSyntax = resultCombinator.getSyntax();
    int[] resultSyntaxUniqueVars = resultCombinator.getSyntaxUniqueVars();
    int resultSyntaxHead = resultCombinator.getSyntaxHeadVar();
    
    int[] currentPosTags = chart.getPosTagsInt();
    int[] wordDistances = chart.getWordDistances();
    int[] puncDistances = chart.getPunctuationDistances();
    int[] verbDistances = chart.getVerbDistances();
    int numTerminals = chart.size();

    // Determine if these chart entries can be combined under the
    // normal form constraints. Normal form constraints state that 
    // the result of a forward (backward) composition cannot be the
    // left (right) element of a forward (backward) combinator.
    boolean isProducedByConjunction = false;
    if (normalFormOnly) {
      Combinator.Type leftCombinator = leftRoot.getDerivingCombinatorType();
      Combinator.Type resultCombinatorType = resultCombinator.getType();
      if (leftCombinator == Combinator.Type.FORWARD_COMPOSITION
          && searchMove.getLeftUnaryKeyNum() == -1
          && (resultCombinatorType == Combinator.Type.FORWARD_APPLICATION
          || resultCombinatorType == Combinator.Type.FORWARD_COMPOSITION)) {
        return;
      }

      Combinator.Type rightCombinator = rightRoot.getDerivingCombinatorType();
      if (rightCombinator == Combinator.Type.BACKWARD_COMPOSITION
          && searchMove.getRightUnaryKeyNum() == -1
          && (resultCombinator.getType() == Combinator.Type.BACKWARD_APPLICATION
          || resultCombinator.getType() == Combinator.Type.BACKWARD_COMPOSITION)) {
        return;
      }

      // Restrict the syntactic parses of conjunctions to permit only
      // right branching analyses.
      if (rightCombinator == Combinator.Type.CONJUNCTION
          && resultCombinatorType == Combinator.Type.BACKWARD_APPLICATION) {
        if (leftRoot.isProducedByConjunction()) {
          return;
        } else {
          isProducedByConjunction = true;
        }
      }
    }

    // log.startTimer("ccg_parse/beam_loop/fill_dependencies");
    // Fill dependencies based on the current assignment.
    // (Filling dependencies takes a trivial amount of time.) 
    int numFilledDeps = 0;
    numFilledDeps = fillDependencies(leftRoot.getAssignmentVarIndex(), leftRoot.getAssignments(),
        rightRoot.getUnfilledDependencyVarIndex(), rightRoot.getUnfilledDependencies(), 
        searchMove.getRightDepRelabeling(), filledDepAccumulator, numFilledDeps);
    numFilledDeps = fillDependencies(rightRoot.getAssignmentVarIndex(), rightRoot.getAssignments(),
        leftRoot.getUnfilledDependencyVarIndex(), leftRoot.getUnfilledDependencies(), 
        searchMove.getLeftDepRelabeling(), filledDepAccumulator, numFilledDeps);

    // Fill dependencies created by the binary rule.
    long[] combinatorUnfilledDeps = null;
    int[] combinatorUnfilledDepsVarIndex = null;
    if (resultCombinator.hasUnfilledDependencies()) {
      List<UnfilledDependency> unfilledDeps = resultCombinator.getUnfilledDependencies(rightSpanEnd);
      long[] unfilledDepsOrig = unfilledDependencyArrayToLongArray(unfilledDeps);

      int maxVarNum = Ints.max(Ints.max(resultCombinator.getLeftVariableRelabeling()),
          Ints.max(resultCombinator.getRightVariableRelabeling()));
      combinatorUnfilledDeps = new long[unfilledDepsOrig.length];
      combinatorUnfilledDepsVarIndex = new int[maxVarNum + 2];
      orderUnfilledDependencies(unfilledDepsOrig, combinatorUnfilledDeps, combinatorUnfilledDepsVarIndex);

      numFilledDeps = fillDependencies(leftRoot.getAssignmentVarIndex(), leftRoot.getAssignments(),
          combinatorUnfilledDepsVarIndex, combinatorUnfilledDeps, searchMove.getLeftInverseRelabeling(),
          filledDepAccumulator, numFilledDeps);
      numFilledDeps = fillDependencies(rightRoot.getAssignmentVarIndex(), rightRoot.getAssignments(),
          combinatorUnfilledDepsVarIndex, combinatorUnfilledDeps, searchMove.getRightInverseRelabeling(),
          filledDepAccumulator, numFilledDeps);
    }
    // log.stopTimer("ccg_parse/beam_loop/fill_dependencies");

    if (numFilledDeps == -1) { 
      return;
    }

    // log.startTimer("ccg_parse/beam_loop/relabel_assignment");
    // Determine the variable assignments for the result syntactic
    // category.
    int[] leftInverseRelabeling = searchMove.getLeftToReturnInverseRelabeling();
    int[] rightInverseRelabeling = searchMove.getRightToReturnInverseRelabeling();
    int numResultVars = leftInverseRelabeling.length;

    int[] leftAssignmentVarIndex = leftRoot.getAssignmentVarIndex();
    long[] leftAssignment = leftRoot.getAssignments();
    int[] rightAssignmentVarIndex = rightRoot.getAssignmentVarIndex();
    long[] rightAssignment = rightRoot.getAssignments();

    int numAssignments = 0;
    for (int k = 0; k < numResultVars; k++) {
      assignmentVarIndexAccumulator[k] = numAssignments;
      int leftVarNum = leftInverseRelabeling[k];
      if (leftVarNum != -1) {
        int startIndex = leftAssignmentVarIndex[leftVarNum];
        int endIndex = leftAssignmentVarIndex[leftVarNum + 1];
        for (int l = startIndex; l < endIndex; l++) {
          if (numAssignments >= assignmentAccumulator.length) {
            return;
          }
          assignmentAccumulator[numAssignments] = replaceAssignmentVarNum(leftAssignment[l],
              leftVarNum, k);
          numAssignments++;
        }
      }

      int rightVarNum = rightInverseRelabeling[k];
      if (rightVarNum != -1) {
        int startIndex = rightAssignmentVarIndex[rightVarNum];
        int endIndex = rightAssignmentVarIndex[rightVarNum + 1];
        for (int l = startIndex; l < endIndex; l++) {
          if (numAssignments >= assignmentAccumulator.length) {
            return;
          }
          assignmentAccumulator[numAssignments] = replaceAssignmentVarNum(rightAssignment[l],
              rightVarNum, k);
          numAssignments++;
        }
      }
    }
    assignmentVarIndexAccumulator[leftInverseRelabeling.length] = numAssignments;
    // log.stopTimer("ccg_parse/beam_loop/relabel_assignment");

    // Determine which unfilled dependencies should be propagated to
    // the result.
    // log.startTimer("ccg_parse/beam_loop/propagate_dependencies");
    int[] leftUnfilledDepsVarIndex = leftRoot.getUnfilledDependencyVarIndex();
    long[] leftUnfilledDeps = leftRoot.getUnfilledDependencies();
    int[] rightUnfilledDepsVarIndex = rightRoot.getUnfilledDependencyVarIndex();
    long[] rightUnfilledDeps = rightRoot.getUnfilledDependencies();
    int[] leftToReturnInverseRelabeling = searchMove.getLeftToReturnInverseRelabeling();
    int[] rightToReturnInverseRelabeling = searchMove.getRightToReturnInverseRelabeling();
    int[] combinatorToReturnInverseRelabeling = null;
    if (combinatorUnfilledDeps != null) {
      combinatorToReturnInverseRelabeling = searchMove.getBinaryCombinator().getResultInverseRelabeling();
    }
    int numVars = searchMove.getLeftToReturnInverseRelabeling().length;
    int numUnfilledDeps = 0;
    for (int k = 0; k < numVars; k++) {
      unfilledDepVarIndexAccumulator[k] = numUnfilledDeps;
      if (assignmentVarIndexAccumulator[k] != assignmentVarIndexAccumulator[k + 1]) {
        // This variable has an assignment in the result, meaning any
        // dependencies on this variable have already been filled.
        continue;
      }

      // Unfilled dependencies are copied (with possible variable 
      // relabeling) from the left and right chart entries, and
      // also the combinator (if it creates new dependencies). 
      numUnfilledDeps = propagateUnfilledDependencies(leftUnfilledDeps, leftUnfilledDepsVarIndex, 
          leftToReturnInverseRelabeling, k, unfilledDepAccumulator, numUnfilledDeps);
      if (numUnfilledDeps >= unfilledDepAccumulator.length) {
        return;
      }
      numUnfilledDeps = propagateUnfilledDependencies(rightUnfilledDeps, rightUnfilledDepsVarIndex, 
          rightToReturnInverseRelabeling, k, unfilledDepAccumulator, numUnfilledDeps);
      if (numUnfilledDeps >= unfilledDepAccumulator.length) {
        return;
      }

      if (combinatorUnfilledDeps != null) {
        numUnfilledDeps = propagateUnfilledDependencies(combinatorUnfilledDeps, combinatorUnfilledDepsVarIndex, 
            combinatorToReturnInverseRelabeling, k, unfilledDepAccumulator, numUnfilledDeps);
        if (numUnfilledDeps >= unfilledDepAccumulator.length) {
          return;
        }
      }
    }
    unfilledDepVarIndexAccumulator[numVars] = numUnfilledDeps;
    // log.stopTimer("ccg_parse/beam_loop/propagate_dependencies");

    // log.startTimer("ccg_parse/beam_loop/copy_stuff");
    long[] filledDepArray = Arrays.copyOf(filledDepAccumulator, numFilledDeps);
    int[] unfilledDepVarIndex = Arrays.copyOf(unfilledDepVarIndexAccumulator, numVars + 1);
    long[] unfilledDepArray = Arrays.copyOf(unfilledDepAccumulator, numUnfilledDeps);

    int[] newAssignmentVarIndex = Arrays.copyOfRange(assignmentVarIndexAccumulator, 0,
        searchMove.getLeftToReturnInverseRelabeling().length + 1);
    long[] newAssignments = Arrays.copyOfRange(assignmentAccumulator, 0, numAssignments);

    ChartEntry result = new ChartEntry(resultSyntax, resultSyntaxUniqueVars, resultSyntaxHead,
        null, searchMove.getLeftUnary(), searchMove.getRightUnary(), newAssignmentVarIndex, newAssignments,
        unfilledDepVarIndex, unfilledDepArray, filledDepArray, leftSpanStart, leftSpanEnd,
        leftIndex, rightSpanStart, rightSpanEnd, rightIndex, resultCombinator, isProducedByConjunction);
    // log.stopTimer("ccg_parse/beam_loop/copy_stuff");

    // Get the weights of applying this syntactic combination rule 
    // given the word and POS tag of the result's head.
    // log.startTimer("ccg_parse/beam_loop/headed_rule_weights");
    double headedRuleProb = 1.0;
    long binaryCombinatorKeyNumWithOffset = searchMove.getBinaryCombinatorKeyNum()
        * headedBinaryRuleCombinatorOffset;
    int syntaxStartIndex = newAssignmentVarIndex[resultSyntaxHead];
    int syntaxEndIndex = newAssignmentVarIndex[resultSyntaxHead + 1];
    for (int assignmentIndex = syntaxStartIndex; assignmentIndex < syntaxEndIndex; assignmentIndex++) {
      long assignment = newAssignments[assignmentIndex];
      long predicate = (assignment >> ASSIGNMENT_PREDICATE_OFFSET) & PREDICATE_MASK;
      int wordIndex = (int) ((assignment >> ASSIGNMENT_WORD_IND_OFFSET) & WORD_IND_MASK);
      int posTag = currentPosTags[wordIndex];

      long combinatorWordPosKeyNum = binaryCombinatorKeyNumWithOffset 
          + (predicate * headedBinaryRulePredicateOffset)
          + (posTag * headedBinaryRulePosOffset);
      headedRuleProb *= headedBinaryRuleTensorHash.get(combinatorWordPosKeyNum);
    }
    // log.stopTimer("ccg_parse/beam_loop/headed_rule_weights");

    // log.startTimer("ccg_parse/beam_loop/dependencies");
    // Get the weights of the generated dependencies.
    double depProb = 1.0;
    double curDepProb = 1.0;
    long[] depLongCache = chart.getDepLongCache();
    double[] depProbCache = chart.getDepProbCache();
    int filledDepArrayLength = filledDepArray.length;
    for (int depIndex = 0; depIndex < filledDepArrayLength; depIndex++) {
      // The contents of this loop takes ~1/3 of all parsing time.
      // TODO: possible speed improvement: use a hash table to cache
      // more than one depLong per core.
      long depLong = filledDepArray[depIndex];
      if (depLong == depLongCache[leftSpanStart]) {
        depProb *= depProbCache[leftSpanStart];
        continue;
      }

      // Compute the keyNum containing the weight for
      // depLong in dependencyTensor.
      int headNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM;
      int headSyntaxNum = (int) ((depLong >> SYNTACTIC_CATEGORY_OFFSET) & SYNTACTIC_CATEGORY_MASK);
      int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM;
      int argNumNum = (int) ((depLong >> ARG_NUM_OFFSET) & ARG_NUM_MASK);
      int subjectWordIndex = (int) ((depLong >> SUBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);
      int objectWordIndex = (int) ((depLong >> OBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);

      int headPosNum = currentPosTags[subjectWordIndex];
      int objectPosNum = currentPosTags[objectWordIndex];

      long depNum = (headNum * dependencyHeadOffset) + (headSyntaxNum * dependencySyntaxOffset) 
          + (argNumNum * dependencyArgNumOffset) + (objectNum * dependencyObjectOffset)
          + (headPosNum * dependencyHeadPosOffset) + (objectPosNum * dependencyObjectPosOffset);

      // Get the probability of this
      // predicate-argument combination.
      // log.startTimer("chart_entry/dependency_prob");
      curDepProb = dependencyTensorHash.get(depNum);
      // log.stopTimer("chart_entry/dependency_prob");

      // Compute distance features.
      // log.startTimer("chart_entry/compute_distance");
      int distanceIndex = (subjectWordIndex * numTerminals) + objectWordIndex;
      int wordDistance = wordDistances[distanceIndex];
      int puncDistance = puncDistances[distanceIndex];
      int verbDistance = verbDistances[distanceIndex];
      // log.stopTimer("chart_entry/compute_distance");

      // log.startTimer("chart_entry/lookup_distance");
      long distanceKeyNumBase = (headNum * distanceHeadOffset) 
          + (headSyntaxNum * distanceSyntaxOffset) + (argNumNum * distanceArgNumOffset)
          + (headPosNum * distanceHeadPosOffset);
      long wordDistanceKeyNum = distanceKeyNumBase + (wordDistance * distanceDistanceOffset);
      curDepProb *= wordDistanceTensorHash.get(wordDistanceKeyNum);
      long puncDistanceKeyNum = distanceKeyNumBase + (puncDistance * distanceDistanceOffset);
      curDepProb *= puncDistanceTensorHash.get(puncDistanceKeyNum);
      long verbDistanceKeyNum = distanceKeyNumBase + (verbDistance * distanceDistanceOffset);
      curDepProb *= verbDistanceTensorHash.get(verbDistanceKeyNum);
      // log.stopTimer("chart_entry/lookup_distance");
      // System.out.println(longToUnfilledDependency(depLong)
      // + " " + depProb);

      depProb *= curDepProb;

      depLongCache[leftSpanStart] = depLong;
      depProbCache[leftSpanStart] = curDepProb;
    }
    // log.stopTimer("ccg_parse/beam_loop/dependencies");

    // log.startTimer("chart_entry/add_chart_entry");
    double totalProb = ruleProb * headedRuleProb * leftProb * rightProb * depProb;
    chart.addChartEntryForSpan(result, totalProb, leftSpanStart, rightSpanEnd, syntaxVarType);
    // log.stopTimer("chart_entry/add_chart_entry");
  }

  public final void applyUnaryRules(CcgChart chart, ChartEntry result, double resultProb,
      int spanStart, int spanEnd) {
    int headedSyntax = result.getHeadedSyntax();
    long keyNumPrefix = unaryRuleTensor.dimKeyPrefixToKeyNum(new int[] { headedSyntax });
    int index = unaryRuleTensor.getNearestIndex(keyNumPrefix);
    int tensorSize = unaryRuleTensor.size();
    if (index == -1 || index >= tensorSize) {
      return;
    }
    long[] dimensionOffsets = unaryRuleTensor.getDimensionOffsets();
    long maxKeyNum = keyNumPrefix + dimensionOffsets[0];
    long curKeyNum = unaryRuleTensor.indexToKeyNum(index);

    while (curKeyNum < maxKeyNum && index < tensorSize) {
      int unaryRuleIndex = (int) (curKeyNum % dimensionOffsets[0]);
      UnaryCombinator unaryRuleCombinator = (UnaryCombinator) unaryRuleVarType.getValue(unaryRuleIndex);
      double ruleProb = unaryRuleTensor.getByIndex(index);

      int[] assignmentInverseRelabeling = unaryRuleCombinator.getInverseRelabeling();
      int[] relabeledAssignmentVarIndex = new int[assignmentInverseRelabeling.length + 1];
      long[] relabeledAssignments = result.getAssignmentsRelabeled(relabeledAssignmentVarIndex);
      
      int[] relabeledUnfilledDepVarIndex = new int[assignmentInverseRelabeling.length + 1];
      long[] relabeledUnfilledDeps = result.getUnfilledDependenciesRelabeled(assignmentInverseRelabeling);

      ChartEntry unaryRuleResult = result.applyUnaryRule(unaryRuleCombinator.getSyntax(),
          unaryRuleCombinator.getSyntaxUniqueVars(), unaryRuleCombinator.getSyntaxHeadVar(), unaryRuleCombinator,
          relabeledAssignmentVarIndex, relabeledAssignments, relabeledUnfilledDepVarIndex,
          relabeledUnfilledDeps, result.getDependencies());
      if (unaryRuleResult != null) {
        chart.addChartEntryForSpan(unaryRuleResult, resultProb * ruleProb, spanStart, spanEnd,
            syntaxVarType);
        /*
         * System.out.println(spanStart + "." + spanEnd + " " +
         * unaryRuleResult.getHeadedSyntax() + " " +
         * unaryRuleResult.getDependencies() + " " + totalProb);
         */
      }

      // Advance the iterator over unary rules.
      index++;
      if (index < tensorSize) {
        curKeyNum = unaryRuleTensor.indexToKeyNum(index);
      }
    }
  }

  private static final int fillDependencies(int[] assignmentVarIndex, long[] assignment, int[] unfilledDepVarIndex,
      long[] unfilledDeps, int[] depToAssignmentRelabeling, long[] filledDepAccumulator, int numFilledDeps) {
    if (numFilledDeps == -1) {
      return -1;
    }

    int numVars = depToAssignmentRelabeling.length;
    for (int i = 0; i < numVars; i++) {
      int assignmentVar = depToAssignmentRelabeling[i];
      if (assignmentVar == -1) {
        continue;
      }

      int startIndex = unfilledDepVarIndex[i];
      int endIndex = unfilledDepVarIndex[i + 1];
      int assignmentStartIndex = assignmentVarIndex[assignmentVar];
      int assignmentEndIndex = assignmentVarIndex[assignmentVar + 1];
      if (endIndex == startIndex || assignmentStartIndex == assignmentEndIndex) {
        continue;
      }

      for (int j = startIndex; j < endIndex; j++) {
        long unfilledDependency = unfilledDeps[j];
        for (int k = assignmentStartIndex; k < assignmentEndIndex; k++) {
          if (numFilledDeps >= filledDepAccumulator.length) {
            return -1;
          }
          long curAssignment = assignment[k];

          long filledDep = unfilledDependency - (i << OBJECT_OFFSET);
          filledDep |= (((long) CcgParser.getAssignmentPredicateNum(curAssignment)) + MAX_ARG_NUM) << OBJECT_OFFSET;
          filledDep |= ((long) CcgParser.getAssignmentWordIndex(curAssignment)) << OBJECT_WORD_IND_OFFSET;

          filledDepAccumulator[numFilledDeps] = filledDep;
          numFilledDeps++;
        }
      }
    }
    return numFilledDeps;
  }
  
  private static final int propagateUnfilledDependencies(long[] originalUnfilledDeps, int[] originalUnfilledDepsVarIndex,
      int[] inverseRelabeling, int k, long[] unfilledDepAccumulator, int numUnfilledDeps) {
    int originalVarNum = inverseRelabeling[k];
    long unfilledDependency;
    if (originalVarNum != -1) {
      int startIndex = originalUnfilledDepsVarIndex[originalVarNum];
      int endIndex = originalUnfilledDepsVarIndex[originalVarNum + 1];

      if (startIndex != endIndex) {
        for (int m = startIndex; m < endIndex; m++) {
          if (numUnfilledDeps >= unfilledDepAccumulator.length) {
            return numUnfilledDeps;
          }

          unfilledDependency = originalUnfilledDeps[m];
          unfilledDependency = unfilledDependency - (originalVarNum << OBJECT_OFFSET);
          unfilledDependency |= (k << OBJECT_OFFSET);

          unfilledDepAccumulator[numUnfilledDeps] = unfilledDependency;
          numUnfilledDeps++;
        }
      }
    }
    return numUnfilledDeps;
  }

  // Methods for efficiently encoding dependencies as longs
  // //////////////////////////////

  public long unfilledDependencyToLong(UnfilledDependency dep) {
    long argNum = dep.getArgumentIndex();
    long objectNum, objectWordInd, subjectNum, subjectWordInd, subjectSyntaxNum;

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
      subjectSyntaxNum = dependencySyntaxType.getValueIndex(dep.getSubjectSyntax());
    } else {
      subjectNum = dep.getSubjectIndex();
      subjectSyntaxNum = 0L;
      subjectWordInd = 0L;
    }

    return marshalUnfilledDependency(objectNum, argNum, subjectNum, subjectSyntaxNum,
        objectWordInd, subjectWordInd);
  }

  public final long predicateToLong(String predicate) {
    if (dependencyHeadType.canTakeValue(predicate)) {
      return dependencyHeadType.getValueIndex(predicate);
    } else {
      return -1;
    }
  }

  public static final long marshalUnfilledDependency(long objectNum, long argNum, long subjectNum,
      long subjectSyntaxNum, long objectWordInd, long subjectWordInd) {
    long value = 0L;
    value += objectNum << OBJECT_OFFSET;
    value += argNum << ARG_NUM_OFFSET;
    value += subjectNum << SUBJECT_OFFSET;
    value += subjectSyntaxNum << SYNTACTIC_CATEGORY_OFFSET;
    value += objectWordInd << OBJECT_WORD_IND_OFFSET;
    value += subjectWordInd << SUBJECT_WORD_IND_OFFSET;
    return value;
  }

  public static final long marshalFilledDependency(long objectNum, long argNum, long subjectNum,
      long subjectSyntaxNum, long objectWordInd, long subjectWordInd) {
    long value = 0L;
    value += (objectNum + MAX_ARG_NUM) << OBJECT_OFFSET;
    value += argNum << ARG_NUM_OFFSET;
    value += (subjectNum + MAX_ARG_NUM) << SUBJECT_OFFSET;
    value += subjectSyntaxNum << SYNTACTIC_CATEGORY_OFFSET;
    value += objectWordInd << OBJECT_WORD_IND_OFFSET;
    value += subjectWordInd << SUBJECT_WORD_IND_OFFSET;
    return value;
  }

  private static final int getArgNumFromDep(long depLong) {
    return (int) ((depLong >> ARG_NUM_OFFSET) & ARG_NUM_MASK);
  }

  public static final int getObjectArgNumFromDep(long depLong) {
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK);
    if (objectNum >= MAX_ARG_NUM) {
      return -1;
    } else {
      return objectNum;
    }
  }

  public static final long replaceObjectVarNum(long depLong, long oldValue, long newValue) {
    depLong -= oldValue << OBJECT_OFFSET;
    depLong += newValue << OBJECT_OFFSET;
    return depLong;
  }

  private static final int getObjectPredicateFromDep(long depLong) {
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK);
    if (objectNum >= MAX_ARG_NUM) {
      return objectNum - MAX_ARG_NUM;
    } else {
      return -1;
    }
  }

  public static final int getSubjectArgNumFromDep(long depLong) {
    int subjectNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK);
    if (subjectNum >= MAX_ARG_NUM) {
      return -1;
    } else {
      return subjectNum;
    }
  }

  public static final int getSubjectPredicateFromDep(long depLong) {
    int subjectNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK);
    if (subjectNum >= MAX_ARG_NUM) {
      return subjectNum - MAX_ARG_NUM;
    } else {
      return -1;
    }
  }

  public static final int getSubjectWordIndexFromDep(long depLong) {
    return (int) ((depLong >> SUBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);
  }

  public static final int getObjectWordIndexFromDep(long depLong) {
    return (int) ((depLong >> OBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);
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
    int argNum, objectNum, objectWordInd, subjectNum, subjectSyntaxNum, subjectWordInd;

    objectNum = (int) ((value >> OBJECT_OFFSET) & PREDICATE_MASK);
    argNum = (int) ((value >> ARG_NUM_OFFSET) & ARG_NUM_MASK);
    subjectNum = (int) ((value >> SUBJECT_OFFSET) & PREDICATE_MASK);
    subjectSyntaxNum = (int) ((value >> SYNTACTIC_CATEGORY_OFFSET) & SYNTACTIC_CATEGORY_MASK);
    objectWordInd = (int) ((value >> OBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);
    subjectWordInd = (int) ((value >> SUBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);

    IndexedPredicate sbj = null, obj = null;
    HeadedSyntacticCategory sbjSyntax = null;
    int objectArgIndex = -1, subjectArgIndex = -1;
    if (objectNum >= MAX_ARG_NUM) {
      String objectHead = (String) dependencyHeadType.getValue(objectNum - MAX_ARG_NUM);
      obj = new IndexedPredicate(objectHead, objectWordInd);
    } else {
      objectArgIndex = objectNum;
    }

    if (subjectNum >= MAX_ARG_NUM) {
      String subjectHead = (String) dependencyHeadType.getValue(subjectNum - MAX_ARG_NUM);
      sbjSyntax = (HeadedSyntacticCategory) dependencySyntaxType.getValue(subjectSyntaxNum);
      sbj = new IndexedPredicate(subjectHead, subjectWordInd);
    } else {
      subjectArgIndex = subjectNum;
    }

    return new UnfilledDependency(sbj, sbjSyntax, subjectArgIndex, argNum, obj, objectArgIndex);
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

  private static void orderUnfilledDependencies(long[] unfilledDepsOrig, long[] resultUnfilledDeps, int[] resultVarIndex) {
    int numFilled = 0;
    for (int i = 0; i < resultVarIndex.length - 1; i++) {
      resultVarIndex[i] = numFilled;
      for (int j = 0; j < unfilledDepsOrig.length; j++) {
        long unfilledDep = unfilledDepsOrig[j];
        int objectArgNum = getObjectArgNumFromDep(unfilledDep);
        if (objectArgNum == i) {
          resultUnfilledDeps[numFilled] = unfilledDep;
          numFilled++;
        }
      }
    }
    Preconditions.checkState(numFilled == unfilledDepsOrig.length);
    resultVarIndex[resultVarIndex.length - 1] = numFilled;
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
   * {@code assignments} into a set of {@code IndexedPredicate}.
   * 
   * @param varNum
   * @param assignments
   * @return
   */
  public Set<IndexedPredicate> variableToIndexedPredicateArray(int varNum, long[] assignments) {
    Set<IndexedPredicate> predicates = Sets.newHashSet();
    for (int i = 0; i < assignments.length; i++) {
      if (CcgParser.getAssignmentVarNum(assignments[i]) == varNum) {
        int predicateNum = CcgParser.getAssignmentPredicateNum(assignments[i]);
        int index = CcgParser.getAssignmentWordIndex(assignments[i]);
        predicates.add(new IndexedPredicate((String) dependencyHeadType.getValue(predicateNum), index));
      }
    }
    return predicates;
  }
   
  public static final long marshalAssignment(long variableNum, long predicateNum, long wordInd) {
    return (variableNum << ASSIGNMENT_VAR_NUM_OFFSET) |
        (predicateNum << ASSIGNMENT_PREDICATE_OFFSET) | (wordInd << ASSIGNMENT_WORD_IND_OFFSET);
  }
  
  public static final int getAssignmentVarNum(long assignment) {
    return (int) ((assignment >> ASSIGNMENT_VAR_NUM_OFFSET) & VAR_NUM_MASK);
  }

  public static final int getAssignmentPredicateNum(long assignment) {
    return (int) ((assignment >> ASSIGNMENT_PREDICATE_OFFSET) & PREDICATE_MASK);
  }

  private static final int getAssignmentWordIndex(long assignment) {
    return (int) ((assignment >> ASSIGNMENT_WORD_IND_OFFSET) & WORD_IND_MASK);
  }
  
  public static final long replaceAssignmentVarNum(long assignment, int oldVarNum, int newVarNum) {
    // Switch the variable numbers using a fancy XOR trick.
    return assignment ^ (((long) oldVarNum ^ newVarNum) << ASSIGNMENT_VAR_NUM_OFFSET);
  }
  
  private static class CalculateInsideBeamCallable implements Callable<Void> {
    private final CcgParser parser;
    private final CcgChart chart;

    private final int spanStart;
    private final int spanEnd;

    private final LogFunction log;

    public CalculateInsideBeamCallable(CcgParser parser, CcgChart chart, int spanStart, int spanEnd,
        LogFunction log) {
      this.parser = Preconditions.checkNotNull(parser);
      this.chart = Preconditions.checkNotNull(chart);
      this.spanStart = spanStart;
      this.spanEnd = spanEnd;
      this.log = log;
    }

    @Override
    public Void call() {
      parser.calculateInsideBeam(spanStart, spanEnd, chart, log);
      return null;
    }
  }
}