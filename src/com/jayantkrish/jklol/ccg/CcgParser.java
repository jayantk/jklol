package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.chart.CcgBeamSearchChart;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.CcgExactHashTableChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.chart.ChartFilter;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.ArrayUtils;
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
  private static final int PREDICATE_BITS = 16;
  private static final long PREDICATE_MASK = ~(-1L << PREDICATE_BITS);
  private static final int SYNTACTIC_CATEGORY_BITS = 11;
  private static final long SYNTACTIC_CATEGORY_MASK = ~(-1L << SYNTACTIC_CATEGORY_BITS);
  private static final int ARG_NUM_BITS = 4;
  private static final long ARG_NUM_MASK = ~(-1L << ARG_NUM_BITS);
  private static final int WORD_IND_BITS = 8;
  private static final long WORD_IND_MASK = ~(-1L << WORD_IND_BITS);
  // The largest possible argument number.
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

  // Default names for the variables in the syntactic distribution
  // built by buildSyntacticDistribution
  public static final String LEFT_SYNTAX_VAR_NAME = "leftSyntax";
  public static final String RIGHT_SYNTAX_VAR_NAME = "rightSyntax";
  public static final String PARENT_SYNTAX_VAR_NAME = "parentSyntax";
  public static final String PARENT_MOVE_SYNTAX_VAR_NAME = "parentMoveSyntax";

  public static final String UNARY_RULE_INPUT_VAR_NAME = "unaryRuleInputVar";
  public static final String UNARY_RULE_VAR_NAME = "unaryRuleVar";

  public static final String UNKNOWN_WORD_PREFIX = "UNK-";

  // Member variables ////////////////////////////////////

  // Weights and word -> ccg category mappings for the
  // lexicon (terminals).
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;

  // Weights and pos tag -> syntactic category mappings for the
  // lexicon (terminals).
  private final VariableNumMap terminalPosVar;
  private final VariableNumMap terminalSyntaxVar;
  private final DiscreteFactor terminalPosDistribution;

  // Weights for word -> syntactic category mappings for
  // the lexicon. This factor is defined over terminalVar
  // and terminalSyntaxVar, and provides backoff weights
  // for different semantic realizations of the same word.
  private final DiscreteFactor terminalSyntaxDistribution;

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

  private final VariableNumMap puncDistanceVar;
  private final DiscreteFactor puncDistanceFactor;
  private final Tensor puncDistanceTensor;
  private final Set<String> puncTagSet;

  private final VariableNumMap verbDistanceVar;
  private final DiscreteFactor verbDistanceFactor;
  private final Tensor verbDistanceTensor;
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

  // Indicator tensor specifying possible ways to combine pairs of
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

  private final boolean allowWordSkipping;
  private final boolean normalFormOnly;

  public CcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap terminalPosVar, VariableNumMap terminalSyntaxVar,
      DiscreteFactor terminalPosDistribution, DiscreteFactor terminalSyntaxDistribution,
      VariableNumMap dependencyHeadVar, VariableNumMap dependencySyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyArgVar, VariableNumMap dependencyHeadPosVar, VariableNumMap dependencyArgPosVar,
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
      boolean allowWordSkipping, boolean normalFormOnly) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);
    VariableNumMap expectedTerminalVars = VariableNumMap.unionAll(terminalVar, ccgCategoryVar);
    Preconditions.checkArgument(expectedTerminalVars.equals(terminalDistribution.getVars()));

    this.terminalPosVar = Preconditions.checkNotNull(terminalPosVar);
    this.terminalSyntaxVar = Preconditions.checkNotNull(terminalSyntaxVar);
    this.terminalPosDistribution = Preconditions.checkNotNull(terminalPosDistribution);
    VariableNumMap expectedTerminalPosVars = terminalPosVar.union(terminalSyntaxVar);
    Preconditions.checkArgument(expectedTerminalPosVars.equals(terminalPosDistribution.getVars()));

    this.terminalSyntaxDistribution = Preconditions.checkNotNull(terminalSyntaxDistribution);
    VariableNumMap expectedTerminalSyntaxVars = terminalVar.union(terminalSyntaxVar);
    Preconditions.checkArgument(expectedTerminalSyntaxVars.equals(terminalSyntaxDistribution.getVars()));

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
    Preconditions.checkArgument(terminalPosVar.getDiscreteVariables().get(0).equals(dependencyPosType));
    Preconditions.checkArgument(dependencyArgPosVar.getDiscreteVariables().get(0).equals(dependencyPosType));
    // TODO: This check can be made unnecessary by fixing the
    // representation of unfilled dependencies as longs. Right now, the
    // representation requires this condition to hold.
    for (int i = 0; i < dependencyArgNumType.numValues(); i++) {
      Preconditions.checkArgument((int) ((Integer) dependencyArgNumType.getValue(i)) == i);
    }

    DiscreteVariable dependencyArgType = dependencyArgVar.getDiscreteVariables().get(0);
    Preconditions.checkArgument(dependencyHeadType.equals(dependencyArgType));
    this.dependencyTensor = dependencyDistribution.getWeights();
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

    this.puncDistanceVar = puncDistanceVar;
    this.puncDistanceFactor = puncDistanceFactor;
    VariableNumMap expectedPuncVars = distanceDependencyVars.union(puncDistanceVar);
    Preconditions.checkArgument(expectedPuncVars.equals(puncDistanceFactor.getVars()));
    this.puncDistanceTensor = puncDistanceFactor.getWeights();
    this.puncTagSet = puncTagSet;

    this.verbDistanceVar = verbDistanceVar;
    this.verbDistanceFactor = verbDistanceFactor;
    VariableNumMap expectedVerbVars = distanceDependencyVars.union(verbDistanceVar);
    Preconditions.checkArgument(expectedVerbVars.equals(verbDistanceFactor.getVars()));
    this.verbDistanceTensor = verbDistanceFactor.getWeights();
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

    this.allowWordSkipping = allowWordSkipping;
    this.normalFormOnly = normalFormOnly;
  }

  public static DiscreteVariable buildSyntacticCategoryDictionary(Iterable<HeadedSyntacticCategory> syntacticCategories) {
    Set<String> featureValues = Sets.newHashSet();
    for (HeadedSyntacticCategory cat : syntacticCategories) {
      getAllFeatureValues(cat.getSyntax(), featureValues);
    }

    // Compute the closure of syntactic categories, assuming the only
    // operations are function application and feature assignment.
    Set<HeadedSyntacticCategory> allCategories = Sets.newHashSet();
    for (HeadedSyntacticCategory cat : syntacticCategories) {
      Preconditions.checkArgument(cat.isCanonicalForm());
      allCategories.addAll(canonicalizeCategories(cat.getSubcategories(featureValues)));

      while (!cat.getSyntax().isAtomic()) {
        allCategories.addAll(canonicalizeCategories(cat.getArgumentType().getCanonicalForm().getSubcategories(featureValues)));
        allCategories.addAll(canonicalizeCategories(cat.getReturnType().getCanonicalForm().getSubcategories(featureValues)));
        cat = cat.getReturnType();
      }
    }
    DiscreteVariable syntaxType = new DiscreteVariable("syntacticCategory", allCategories);
    /*
     * for (int i = 0; i < syntaxType.numValues(); i++) {
     * System.out.println(i + " " + syntaxType.getValue(i)); }
     */
    return syntaxType;
  }

  public static DiscreteFactor buildRestrictedBinaryDistribution(DiscreteVariable syntaxType,
      Iterable<CcgRuleSchema> ruleSchema, Iterable<CcgBinaryRule> rules, boolean allowComposition) {
    List<HeadedSyntacticCategory> allCategories = syntaxType.getValuesWithCast(HeadedSyntacticCategory.class);
    Set<List<Object>> validOutcomes = Sets.newHashSet();
    Set<Combinator> combinators = Sets.newHashSet();
    Multimap<SyntacticCategory, HeadedSyntacticCategory> headedCatMap = HashMultimap.create();
    for (HeadedSyntacticCategory cat : allCategories) {
      headedCatMap.put(cat.getSyntax().assignAllFeatures(SyntacticCategory.DEFAULT_FEATURE_VALUE), cat);
    }

    for (CcgRuleSchema rule : ruleSchema) {
      // Try recreating this rule using application or composition.
      for (HeadedSyntacticCategory left : headedCatMap.get(rule.getLeft())) {
        for (HeadedSyntacticCategory right : headedCatMap.get(rule.getRight())) {
          if (syntaxType.canTakeValue(left) && syntaxType.canTakeValue(right)) {
            appendApplicationRules(left, right, syntaxType, validOutcomes, combinators);
            appendApplicationRules(right, left, syntaxType, validOutcomes, combinators);

            appendCompositionRules(left, right, syntaxType, validOutcomes, combinators);
            appendCompositionRules(right, left, syntaxType, validOutcomes, combinators);
          }
        }
      }
    }
    appendBinaryRules(rules, syntaxType, validOutcomes, combinators);
    return buildSyntaxDistribution(syntaxType, validOutcomes, combinators);
  }

  /**
   * Constructs a distribution over binary combination rules for CCG,
   * given a set of syntactic categories. This method compiles out all
   * of the possible ways to combine two adjacent CCG categories using
   * function application, composition, and any other binary rules.
   * 
   * @param syntaxType
   * @param rules
   * @param allowComposition
   * @return
   */
  public static DiscreteFactor buildUnrestrictedBinaryDistribution(DiscreteVariable syntaxType,
      Iterable<CcgBinaryRule> rules, boolean allowComposition) {
    List<HeadedSyntacticCategory> allCategories = syntaxType.getValuesWithCast(HeadedSyntacticCategory.class);
    Set<List<Object>> validOutcomes = Sets.newHashSet();
    Set<Combinator> combinators = Sets.newHashSet();
    // Compute function application rules.
    for (HeadedSyntacticCategory functionCat : allCategories) {
      for (HeadedSyntacticCategory argumentCat : allCategories) {
        appendApplicationRules(functionCat, argumentCat, syntaxType, validOutcomes, combinators);
      }
    }

    if (allowComposition) {
      // Compute function composition rules.
      for (HeadedSyntacticCategory functionCat : allCategories) {
        for (HeadedSyntacticCategory argumentCat : allCategories) {
          appendCompositionRules(functionCat, argumentCat, syntaxType, validOutcomes, combinators);
        }
      }
    }

    appendBinaryRules(rules, syntaxType, validOutcomes, combinators);
    return buildSyntaxDistribution(syntaxType, validOutcomes, combinators);
  }

  private static DiscreteFactor buildSyntaxDistribution(DiscreteVariable syntaxType, Set<List<Object>> validOutcomes,
      Set<Combinator> combinators) {
    // Build an indicator tensor for valid combinations of syntactic
    // categories.
    DiscreteVariable combinatorType = new DiscreteVariable("combinator", combinators);
    VariableNumMap syntaxVars = new VariableNumMap(Arrays.asList(0, 1, 2),
        Arrays.asList(LEFT_SYNTAX_VAR_NAME, RIGHT_SYNTAX_VAR_NAME, PARENT_SYNTAX_VAR_NAME),
        Arrays.asList(syntaxType, syntaxType, combinatorType));
    TableFactorBuilder syntaxDistributionBuilder = new TableFactorBuilder(syntaxVars,
        SparseTensorBuilder.getFactory());
    for (List<Object> outcome : validOutcomes) {
      syntaxDistributionBuilder.setWeight(syntaxVars.outcomeToAssignment(outcome), 1.0);
    }

    DiscreteFactor syntaxDistribution = syntaxDistributionBuilder.build();
    System.out.println("Number of binary rules: " + syntaxDistribution.getTotalUnnormalizedProbability());
    return syntaxDistribution;
  }

  private static void appendBinaryRules(Iterable<CcgBinaryRule> binaryRules, DiscreteVariable syntaxType,
      Set<List<Object>> validOutcomes, Set<Combinator> combinators) {
    // Find which syntactic categories are unifiable with each other.
    // This map is used to determine which categories binary and unary
    // rules may be applied to.
    List<HeadedSyntacticCategory> allCategories = syntaxType.getValuesWithCast(HeadedSyntacticCategory.class);
    SetMultimap<HeadedSyntacticCategory, HeadedSyntacticCategory> unifiabilityMap = buildUnifiabilityMap(allCategories);

    // Create entries for CCG binary rules.
    for (CcgBinaryRule rule : binaryRules) {
      HeadedSyntacticCategory leftCanon = rule.getLeftSyntacticType().getCanonicalForm();
      HeadedSyntacticCategory rightCanon = rule.getRightSyntacticType().getCanonicalForm();

      for (HeadedSyntacticCategory left : unifiabilityMap.get(leftCanon)) {
        for (HeadedSyntacticCategory right : unifiabilityMap.get(rightCanon)) {
          Combinator combinator = getBinaryRuleCombinator(left, right, rule, syntaxType);
          if (combinator != null) {
            List<Object> outcome = Arrays.<Object> asList(left, right, combinator);
            validOutcomes.add(outcome);
            combinators.add(combinator);
          }
        }
      }
    }
  }

  private static void appendApplicationRules(HeadedSyntacticCategory functionCat, HeadedSyntacticCategory argumentCat,
      DiscreteVariable syntaxType, Set<List<Object>> validOutcomes, Set<Combinator> combinators) {
    if (!functionCat.isAtomic() && functionCat.getArgumentType().isUnifiableWith(argumentCat)) {
      Direction direction = functionCat.getSyntax().getDirection();
      Combinator combinator;
      List<Object> outcome;
      if (direction.equals(Direction.LEFT)) {
        combinator = getApplicationCombinator(functionCat, argumentCat, true, syntaxType);
        outcome = Arrays.<Object> asList(argumentCat, functionCat, combinator);
      } else if (direction.equals(Direction.RIGHT)) {
        combinator = getApplicationCombinator(functionCat, argumentCat, false, syntaxType);
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

  private static void appendCompositionRules(HeadedSyntacticCategory functionCat, HeadedSyntacticCategory argumentCat,
      DiscreteVariable syntaxType, Set<List<Object>> validOutcomes, Set<Combinator> combinators) {
    if (!functionCat.isAtomic()) {
      // Find any return categories of argumentCat with which
      // functionCat may be composed.
      HeadedSyntacticCategory returnType = argumentCat;
      int depth = 0;
      while (!returnType.isAtomic()) {
        returnType = returnType.getReturnType();
        depth++;

        if (functionCat.getArgumentType().isUnifiableWith(returnType)) {
          for (int i = 0; i < 2; i++) {
            boolean argumentAsHead = i % 2 == 0;
            Direction direction = functionCat.getSyntax().getDirection();
            Combinator combinator;
            List<Object> outcome;
            if (direction.equals(Direction.LEFT)) {
              combinator = getCompositionCombinator(functionCat, argumentCat, returnType, depth,
                  true, argumentAsHead, syntaxType);
              outcome = Arrays.<Object> asList(argumentCat, functionCat, combinator);
            } else if (direction.equals(Direction.RIGHT)) {
              combinator = getCompositionCombinator(functionCat, argumentCat, returnType, depth,
                  false, argumentAsHead, syntaxType);
              outcome = Arrays.<Object> asList(functionCat, argumentCat, combinator);
            } else {
              // Forward compatible error message, for handling
              // Direction.BOTH if added.
              throw new IllegalArgumentException("Unknown direction type: " + direction);
            }

            if (combinator != null) {
              // It is possible for function composition to
              // return syntactic categories
              // which are not members of the parser's set of
              // valid syntactic categories.
              // Such composition rules are discarded.
              validOutcomes.add(outcome);
              combinators.add(combinator);
            }
          }
        }
      }
    }
  }

  private static void getAllFeatureValues(SyntacticCategory category, Set<String> values) {
    values.add(category.getRootFeature());
    if (!category.isAtomic()) {
      getAllFeatureValues(category.getArgument(), values);
      getAllFeatureValues(category.getReturn(), values);
    }
  }

  private static List<HeadedSyntacticCategory> canonicalizeCategories(Iterable<HeadedSyntacticCategory> cats) {
    List<HeadedSyntacticCategory> canonicalCats = Lists.newArrayList();
    for (HeadedSyntacticCategory cat : cats) {
      canonicalCats.add(cat.getCanonicalForm());
    }
    return canonicalCats;
  }

  private static SetMultimap<HeadedSyntacticCategory, HeadedSyntacticCategory> buildUnifiabilityMap(
      Iterable<HeadedSyntacticCategory> categories) {
    SetMultimap<HeadedSyntacticCategory, HeadedSyntacticCategory> unifiabilityMap = HashMultimap.create();
    for (HeadedSyntacticCategory cat1 : categories) {
      for (HeadedSyntacticCategory cat2 : categories) {
        if (cat1.isUnifiableWith(cat2)) {
          unifiabilityMap.put(cat1, cat2);
        }
      }
    }
    return unifiabilityMap;
  }

  private static Combinator getApplicationCombinator(HeadedSyntacticCategory functionCat,
      HeadedSyntacticCategory argumentCat, boolean argumentOnLeft, DiscreteVariable syntaxVarType) {
    Map<Integer, String> assignedFeatures = Maps.newHashMap();
    Map<Integer, String> otherAssignedFeatures = Maps.newHashMap();
    Map<Integer, Integer> relabeledFeatures = Maps.newHashMap();
    Preconditions.checkArgument(functionCat.getArgumentType().isUnifiableWith(argumentCat,
        assignedFeatures, otherAssignedFeatures, relabeledFeatures));

    HeadedSyntacticCategory functionReturnType = functionCat.getReturnType();
    HeadedSyntacticCategory functionArgumentType = functionCat.getArgumentType();
    Preconditions.checkState(functionReturnType.isCanonicalForm());

    int[] argumentRelabeling = argumentCat.unifyVariables(argumentCat.getUniqueVariables(),
        functionArgumentType, new int[0]);
    int[] functionRelabeling = functionCat.getUniqueVariables();
    int[] resultRelabeling = functionReturnType.getUniqueVariables();
    int[] unifiedVariables = functionArgumentType.getUniqueVariables();

    // The return type may need to inherit some semantic features from
    // argument. Identify said features and update the return type.
    functionReturnType = functionReturnType.assignFeatures(assignedFeatures, relabeledFeatures);

    int functionReturnTypeInt = syntaxVarType.getValueIndex(functionReturnType);
    int[] functionReturnTypeVars = functionReturnType.getUniqueVariables();
    int functionReturnHead = functionReturnType.getHeadVariable();
    if (argumentOnLeft) {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionReturnHead, argumentRelabeling,
          functionRelabeling, resultRelabeling, resultRelabeling, unifiedVariables,
          new String[0], new HeadedSyntacticCategory[0], new int[0], new int[0], argumentOnLeft, 0, null, 
          Combinator.Type.BACKWARD_APPLICATION);
    } else {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionReturnHead, functionRelabeling,
          argumentRelabeling, resultRelabeling, resultRelabeling, unifiedVariables, new String[0],
          new HeadedSyntacticCategory[0], new int[0], new int[0], argumentOnLeft, 0, null,
          Combinator.Type.FORWARD_APPLICATION);
    }
  }

  private static Combinator getCompositionCombinator(HeadedSyntacticCategory functionCat,
      HeadedSyntacticCategory argumentCat, HeadedSyntacticCategory argumentReturnCat,
      int argumentReturnDepth, boolean argumentOnLeft, boolean argumentAsHead,
      DiscreteVariable syntaxVarType) {
    Map<Integer, String> assignedFeatures = Maps.newHashMap();
    Map<Integer, String> otherAssignedFeatures = Maps.newHashMap();
    Map<Integer, Integer> relabeledFeatures = Maps.newHashMap();
    Preconditions.checkArgument(functionCat.getArgumentType().isUnifiableWith(argumentReturnCat,
        assignedFeatures, otherAssignedFeatures, relabeledFeatures));

    // Determine which syntactic category results from composing the
    // two input categories.
    int[] argumentVars = argumentCat.getUniqueVariables();
    int[] argumentRelabeling = argumentReturnCat.unifyVariables(argumentVars,
        functionCat.getArgumentType(), functionCat.getUniqueVariables());
    HeadedSyntacticCategory relabeledArgumentType = argumentCat.relabelVariables(argumentVars,
        argumentRelabeling).assignFeatures(otherAssignedFeatures,
        Collections.<Integer, Integer> emptyMap());
    HeadedSyntacticCategory resultType = functionCat.assignFeatures(assignedFeatures, relabeledFeatures)
        .getReturnType();
    
    Direction firstDirection = null;
    for (int i = argumentReturnDepth; i > 0; i--) {
      HeadedSyntacticCategory curArg = relabeledArgumentType;
      int headVariable = argumentAsHead ? relabeledArgumentType.getHeadVariable() : functionCat.getHeadVariable();
      for (int j = 0; j < (i - 1); j++) {
        curArg = curArg.getReturnType();
        headVariable = curArg.getHeadVariable();
      }
      Direction curDirection = curArg.getDirection();
      if (firstDirection == null) {
        firstDirection = curDirection;
      }
      curArg = curArg.getArgumentType();
      resultType = resultType.addArgument(curArg, curDirection, headVariable);
    }

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

    if (!syntaxVarType.canTakeValue(canonicalResultType)) {
      // It is possible for function composition to return syntactic
      // categories which are not members of the parser's set of valid
      // syntactic categories. Such composition rules are discarded.
      return null;
    }
    
    Combinator.Type combinatorType = null;
    if (firstDirection == functionCat.getDirection()) {
      if (argumentOnLeft) {
        combinatorType = Combinator.Type.BACKWARD_COMPOSITION;
      } else {
        combinatorType = Combinator.Type.FORWARD_COMPOSITION;
      }
    } else {
      // Crossed composition.
      combinatorType = Combinator.Type.OTHER;
    }

    int functionReturnTypeInt = syntaxVarType.getValueIndex(canonicalResultType);
    int[] functionReturnTypeVars = canonicalResultType.getUniqueVariables();
    int functionHead = canonicalResultType.getHeadVariable();
    if (argumentOnLeft) {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionHead, argumentCatRelabeling,
          functionCatRelabeling, resultUniqueVars, resultCatRelabeling, unifiedVariables,
          new String[0], new HeadedSyntacticCategory[0], new int[0], new int[0], argumentOnLeft,
          argumentReturnDepth, null, combinatorType);
    } else {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionHead, functionCatRelabeling,
          argumentCatRelabeling, resultUniqueVars, resultCatRelabeling, unifiedVariables,
          new String[0], new HeadedSyntacticCategory[0], new int[0], new int[0], argumentOnLeft,
          argumentReturnDepth, null, combinatorType);
    }
  }

  private static Combinator getBinaryRuleCombinator(HeadedSyntacticCategory leftCanonical,
      HeadedSyntacticCategory rightCanonical, CcgBinaryRule rule, DiscreteVariable syntaxVarType) {
    // Binary rules work by relabeling both the left and right
    // categories into a single, non-canonical set of variables.
    HeadedSyntacticCategory left = rule.getLeftSyntacticType();
    HeadedSyntacticCategory right = rule.getRightSyntacticType();

    // Identify assignments to the feature variables of left and
    // canonical, then combine them into a single assignment which
    // applies to the return type.
    Map<Integer, String> leftAssignedFeatures = Maps.newHashMap();
    Map<Integer, String> leftOtherAssignedFeatures = Maps.newHashMap();
    Map<Integer, Integer> leftRelabeledFeatures = Maps.newHashMap();
    Preconditions.checkArgument(leftCanonical.isUnifiableWith(left, leftAssignedFeatures,
        leftOtherAssignedFeatures, leftRelabeledFeatures));

    Map<Integer, String> rightAssignedFeatures = Maps.newHashMap();
    Map<Integer, String> rightOtherAssignedFeatures = Maps.newHashMap();
    Map<Integer, Integer> rightRelabeledFeatures = Maps.newHashMap();
    Preconditions.checkArgument(rightCanonical.isUnifiableWith(right, rightAssignedFeatures,
        rightOtherAssignedFeatures, rightRelabeledFeatures));

    Set<Integer> assignedVars = Sets.newHashSet(leftOtherAssignedFeatures.keySet());
    assignedVars.addAll(rightOtherAssignedFeatures.keySet());
    Map<Integer, String> combinedAssignedFeatures = Maps.newHashMap();
    for (int varNum : assignedVars) {
      String leftVal = leftOtherAssignedFeatures.get(varNum);
      String rightVal = rightOtherAssignedFeatures.get(varNum);

      if (leftVal != null && rightVal != null && !(leftVal.equals(rightVal))) {
        // Failure: the two input categories must produce the same
        // assignment to all
        // feature variables.
        return null;
      } else {
        combinedAssignedFeatures.put(varNum, (leftVal != null) ? leftVal : rightVal);
      }
    }

    int[] leftRelabelingArray = leftCanonical.unifyVariables(leftCanonical.getUniqueVariables(),
        left, new int[0]);
    int[] rightRelabelingArray = rightCanonical.unifyVariables(rightCanonical.getUniqueVariables(),
        right, new int[0]);

    Map<Integer, Integer> parentRelabeling = Maps.newHashMap();
    HeadedSyntacticCategory parent = rule.getParentSyntacticType().assignFeatures(
        combinedAssignedFeatures, Collections.<Integer, Integer> emptyMap())
        .getCanonicalForm(parentRelabeling);
    int[] parentOriginalVars = rule.getParentSyntacticType().getUniqueVariables();
    int[] parentRelabelingArray = relabelingMapToArray(parentRelabeling, parentOriginalVars);

    int[] unifiedVariables = Ints.concat(leftRelabelingArray, rightRelabelingArray);

    int parentInt = syntaxVarType.getValueIndex(parent);
    int[] parentVars = parent.getUniqueVariables();
    int parentHead = parent.getHeadVariable();
    return new Combinator(parentInt, parentVars, parentHead, leftRelabelingArray, rightRelabelingArray,
        parentOriginalVars, parentRelabelingArray, unifiedVariables, rule.getSubjects(),
        rule.getSubjectSyntacticCategories(), rule.getArgumentNumbers(), rule.getObjects(),
        false, -1, rule, Combinator.Type.OTHER);
  }

  private static int[] relabelingMapToArray(Map<Integer, Integer> relabelingMap, int[] originalVars) {
    int[] relabeling = new int[relabelingMap.size()];
    for (int i = 0; i < relabeling.length; i++) {
      relabeling[i] = relabelingMap.get(originalVars[i]);
    }
    return relabeling;
  }

  public static DiscreteFactor buildUnaryRuleDistribution(Collection<CcgUnaryRule> unaryRules,
      DiscreteVariable syntaxVariableType) {

    SetMultimap<HeadedSyntacticCategory, HeadedSyntacticCategory> unifiabilityMap =
        buildUnifiabilityMap(syntaxVariableType.getValuesWithCast(HeadedSyntacticCategory.class));

    List<List<Object>> validOutcomes = Lists.newArrayList();
    Set<UnaryCombinator> validCombinators = Sets.newHashSet();
    for (CcgUnaryRule rule : unaryRules) {
      for (HeadedSyntacticCategory cat : unifiabilityMap.get(
          rule.getInputSyntacticCategory().getCanonicalForm())) {

        Map<Integer, String> assignedFeatures = Maps.newHashMap();
        Map<Integer, String> otherAssignedFeatures = Maps.newHashMap();
        Map<Integer, Integer> relabeledFeatures = Maps.newHashMap();

        Preconditions.checkArgument(cat.isUnifiableWith(rule.getInputSyntacticCategory(),
            assignedFeatures, otherAssignedFeatures, relabeledFeatures));

        int[] patternToChart = cat.unifyVariables(cat.getUniqueVariables(),
            rule.getInputSyntacticCategory(), new int[0]);

        HeadedSyntacticCategory returnType = rule.getResultSyntacticCategory().assignFeatures(
            otherAssignedFeatures, Collections.<Integer, Integer> emptyMap());
        int resultAsInt = syntaxVariableType.getValueIndex(returnType);
        UnaryCombinator combinator = new UnaryCombinator(cat, resultAsInt,
            returnType.getUniqueVariables(), returnType.getHeadVariable(), patternToChart, rule);

        validCombinators.add(combinator);
        validOutcomes.add(Lists.<Object> newArrayList(cat, combinator));
      }
    }

    DiscreteVariable unaryRuleVarType = new DiscreteVariable("unaryRuleType", validCombinators);
    VariableNumMap unaryRuleVar = VariableNumMap.singleton(2, UNARY_RULE_VAR_NAME,
        unaryRuleVarType);
    VariableNumMap unaryRuleInputVar = VariableNumMap.singleton(1, UNARY_RULE_INPUT_VAR_NAME,
        syntaxVariableType);

    VariableNumMap unaryRuleVars = unaryRuleInputVar.union(unaryRuleVar);
    TableFactorBuilder unaryRuleBuilder = new TableFactorBuilder(unaryRuleVars,
        SparseTensorBuilder.getFactory());
    for (List<Object> outcome : validOutcomes) {
      unaryRuleBuilder.setWeightList(outcome, 1.0);
    }
    DiscreteFactor unaryRuleDistribution = unaryRuleBuilder.build();
    System.out.println("Number of unary rules: " + unaryRuleDistribution.getTotalUnnormalizedProbability());
    return unaryRuleDistribution;
  }

  public static DiscreteFactor compileUnaryAndBinaryRules(DiscreteFactor unaryRuleDistribution,
      DiscreteFactor binaryRuleDistribution, DiscreteVariable syntaxVariableType) {
    Preconditions.checkArgument(binaryRuleDistribution.getVars().size() == 3);

    // Find out which unary rules can be applied to produce each
    // possible syntactic category.
    Multimap<HeadedSyntacticCategory, Assignment> resultInputMap = HashMultimap.create();
    VariableNumMap unaryRuleVars = unaryRuleDistribution.getVars();
    Tensor unaryRuleTensor = unaryRuleDistribution.getWeights();
    Iterator<Outcome> iter = unaryRuleDistribution.outcomeIterator();
    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      if (outcome.getProbability() != 0.0) {
        Assignment assignment = outcome.getAssignment();
        List<Object> values = assignment.getValues();
        UnaryCombinator combinator = (UnaryCombinator) values.get(1);

        HeadedSyntacticCategory result = (HeadedSyntacticCategory) syntaxVariableType.getValue(
            combinator.getSyntax());
        resultInputMap.put(result, assignment);
      }
    }

    // Construct a set of binary combination outcomes that includes
    // the original outcomes
    // and all possible unary rules applied to those inputs.
    List<List<Object>> validOutcomes = Lists.newArrayList();
    Set<CcgSearchMove> validMoves = Sets.newHashSet();
    iter = binaryRuleDistribution.outcomeIterator();
    VariableNumMap binaryRuleVars = binaryRuleDistribution.getVars();
    Tensor binaryRuleTensor = binaryRuleDistribution.getWeights();
    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      if (outcome.getProbability() != 0.0) {
        Assignment assignment = outcome.getAssignment();
        List<Object> originalOutcome = assignment.getValues();

        HeadedSyntacticCategory leftSyntax = (HeadedSyntacticCategory) originalOutcome.get(0);
        HeadedSyntacticCategory rightSyntax = (HeadedSyntacticCategory) originalOutcome.get(1);
        Combinator result = (Combinator) originalOutcome.get(2);
        long resultKeyNum = binaryRuleTensor.dimKeyToKeyNum(binaryRuleVars.assignmentToIntArray(assignment));

        CcgSearchMove move = getSearchMove(result, null, null, resultKeyNum, -1, -1);
        validOutcomes.add(Lists.<Object> newArrayList(leftSyntax, rightSyntax, move));
        validMoves.add(move);

        for (Assignment leftUnaryAssignment : resultInputMap.get(leftSyntax)) {
          long leftKeyNum = unaryRuleTensor.dimKeyToKeyNum(unaryRuleVars.assignmentToIntArray(leftUnaryAssignment));
          UnaryCombinator leftUnary = (UnaryCombinator) leftUnaryAssignment.getValues().get(1);

          CcgSearchMove combinedMove = getSearchMove(result, leftUnary, null, resultKeyNum, leftKeyNum, -1);
          HeadedSyntacticCategory newLeftSyntax = leftUnary.getInputType().getCanonicalForm();
          validOutcomes.add(Lists.<Object> newArrayList(newLeftSyntax, rightSyntax, combinedMove));
          validMoves.add(combinedMove);
        }

        for (Assignment rightUnaryAssignment : resultInputMap.get(rightSyntax)) {
          long rightKeyNum = unaryRuleTensor.dimKeyToKeyNum(unaryRuleVars.assignmentToIntArray(rightUnaryAssignment));
          UnaryCombinator rightUnary = (UnaryCombinator) rightUnaryAssignment.getValues().get(1);

          CcgSearchMove combinedMove = getSearchMove(result, null, rightUnary, resultKeyNum, -1, rightKeyNum);
          HeadedSyntacticCategory newRightSyntax = rightUnary.getInputType().getCanonicalForm();
          validOutcomes.add(Lists.<Object> newArrayList(leftSyntax, newRightSyntax, combinedMove));
          validMoves.add(combinedMove);
        }

        for (Assignment leftUnaryAssignment : resultInputMap.get(leftSyntax)) {
          for (Assignment rightUnaryAssignment : resultInputMap.get(rightSyntax)) {
            long leftKeyNum = unaryRuleTensor.dimKeyToKeyNum(unaryRuleVars.assignmentToIntArray(leftUnaryAssignment));
            UnaryCombinator leftUnary = (UnaryCombinator) leftUnaryAssignment.getValues().get(1);
            long rightKeyNum = unaryRuleTensor.dimKeyToKeyNum(unaryRuleVars.assignmentToIntArray(rightUnaryAssignment));
            UnaryCombinator rightUnary = (UnaryCombinator) rightUnaryAssignment.getValues().get(1);

            CcgSearchMove combinedMove = getSearchMove(result, leftUnary, rightUnary, resultKeyNum, leftKeyNum, rightKeyNum);
            HeadedSyntacticCategory newLeftSyntax = leftUnary.getInputType().getCanonicalForm();
            HeadedSyntacticCategory newRightSyntax = rightUnary.getInputType().getCanonicalForm();
            validOutcomes.add(Lists.<Object> newArrayList(newLeftSyntax, newRightSyntax, combinedMove));
            validMoves.add(combinedMove);
          }
        }
      }
    }

    // Build an indicator tensor for valid combinations of syntactic
    // categories.
    DiscreteVariable searchMoveType = new DiscreteVariable("searchMoves", validMoves);
    VariableNumMap syntaxVars = new VariableNumMap(Arrays.asList(0, 1, 2),
        Arrays.asList(LEFT_SYNTAX_VAR_NAME, RIGHT_SYNTAX_VAR_NAME, PARENT_MOVE_SYNTAX_VAR_NAME),
        Arrays.asList(syntaxVariableType, syntaxVariableType, searchMoveType));
    TableFactorBuilder syntaxDistributionBuilder = new TableFactorBuilder(syntaxVars,
        SparseTensorBuilder.getFactory());
    for (List<Object> outcome : validOutcomes) {
      syntaxDistributionBuilder.setWeight(syntaxVars.outcomeToAssignment(outcome), 1.0);
    }

    DiscreteFactor syntaxDistribution = syntaxDistributionBuilder.build();
    System.out.println("Number of compiled (unary and binary) rules: " + syntaxDistribution.getTotalUnnormalizedProbability());
    return syntaxDistribution;
  }

  private static CcgSearchMove getSearchMove(Combinator combinator, UnaryCombinator leftUnary,
      UnaryCombinator rightUnary, long combinatorKeyNum, long leftKeyNum, long rightKeyNum) {
    int[] leftRelabeling = combinator.getLeftVariableRelabeling();
    int[] rightRelabeling = combinator.getRightVariableRelabeling();

    int[] newLeftRelabeling = leftRelabeling;
    int[] newRightRelabeling = rightRelabeling;

    if (leftUnary != null) {
      int[] leftUnaryRelabeling = leftUnary.getVariableRelabeling();
      newLeftRelabeling = new int[leftUnaryRelabeling.length];
      for (int i = 0; i < newLeftRelabeling.length; i++) {
        if (leftUnaryRelabeling[i] < leftRelabeling.length) {
          newLeftRelabeling[i] = leftRelabeling[leftUnaryRelabeling[i]];
        } else {
          // This variable isn't referenced in the syntactic category
          // returned by the unary rule. -1 is a special value which
          // causes the value of this variable to be dropped.
          newLeftRelabeling[i] = -1;
        }
      }
    }

    if (rightUnary != null) {
      int[] rightUnaryRelabeling = rightUnary.getVariableRelabeling();
      newRightRelabeling = new int[rightUnaryRelabeling.length];
      for (int i = 0; i < newRightRelabeling.length; i++) {
        if (rightUnaryRelabeling[i] < rightRelabeling.length) {
          newRightRelabeling[i] = rightRelabeling[rightUnaryRelabeling[i]];
        } else {
          // See comment above for the left unary rule.
          newRightRelabeling[i] = -1;
        }
      }
    }

    return new CcgSearchMove(combinator, leftUnary, rightUnary, combinatorKeyNum, leftKeyNum, rightKeyNum,
        newLeftRelabeling, newRightRelabeling);
  }

  private static List<String> preprocessInput(List<String> terminals) {
    List<String> preprocessedTerminals = Lists.newArrayList();
    for (String terminal : terminals) {
      preprocessedTerminals.add(terminal.toLowerCase());
    }
    return preprocessedTerminals;
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

  public boolean isPossibleLexiconEntry(List<String> originalWords, List<String> posTags, HeadedSyntacticCategory category) {
    Preconditions.checkArgument(originalWords.size() == posTags.size());

    List<String> words = preprocessInput(originalWords);
    List<List<String>> terminalOutcomes = Lists.newArrayList();
    terminalOutcomes.add(words);
    if (words.size() == 1) {
      List<String> posTagBackoff = preprocessInput(Arrays.asList(UNKNOWN_WORD_PREFIX + posTags.get(0)));
      terminalOutcomes.add(posTagBackoff);
    }

    for (List<String> terminalOutcome : terminalOutcomes) {
      if (terminalVar.isValidOutcomeArray(terminalOutcome)) {
        Assignment assignment = terminalVar.outcomeArrayToAssignment(terminalOutcome);

        Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
        while (iterator.hasNext()) {
          Outcome bestOutcome = iterator.next();
          CcgCategory lexicalEntry = (CcgCategory) bestOutcome.getAssignment().getValue(
              ccgCategoryVar.getOnlyVariableNum());
          if (lexicalEntry.getSyntax().equals(category)) {
            return true;
          }
        }
      }
    }
    // System.out.println("No such lexicon entry: " + words + " -> " +
    // category);
    return false;
  }

  public boolean isPossibleBinaryRule(SyntacticCategory left, SyntacticCategory right,
      SyntacticCategory parent, Multimap<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap) {
    // System.out.println("checking: " + left + " " +right + " -> "+
    // parent);
    // System.out.println(syntacticCategoryMap.keySet());
    for (HeadedSyntacticCategory leftHeaded : syntacticCategoryMap.get(left)) {
      for (HeadedSyntacticCategory rightHeaded : syntacticCategoryMap.get(right)) {
        // System.out.println(leftHeaded + " " +rightHeaded);
        Assignment assignment = leftSyntaxVar.outcomeArrayToAssignment(leftHeaded)
            .union(rightSyntaxVar.outcomeArrayToAssignment(rightHeaded));

        if (leftSyntaxVar.union(rightSyntaxVar).isValidAssignment(assignment)) {
          Iterator<Outcome> outcomeIter = binaryRuleDistribution.outcomePrefixIterator(assignment);
          while (outcomeIter.hasNext()) {
            Outcome outcome = outcomeIter.next();
            Combinator resultCombinator = (Combinator) outcome.getAssignment().getValue(
                combinatorVar.getOnlyVariableNum());
            HeadedSyntacticCategory parentCategory = (HeadedSyntacticCategory)
                syntaxVarType.getValue(resultCombinator.getSyntax());

            if (syntacticCategoryMap.get(parent).contains(parentCategory)) {
              return true;
            }
          }
        }
      }
    }

    System.out.println("No such binary rule: " + left + " " + right + " -> " + parent);
    System.out.println("  left: " + syntacticCategoryMap.get(left));
    System.out.println("  right: " + syntacticCategoryMap.get(right));
    System.out.println("  parent: " + syntacticCategoryMap.get(parent));
    return false;
  }

  public boolean isPossibleSyntacticTree(CcgSyntaxTree tree,
      Multimap<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap) {
    // If a unary rule was applied, confirm that the rule is
    // one of the rules in this parser.
    SyntacticCategory preUnarySyntax = tree.getPreUnaryRuleSyntax();
    SyntacticCategory result = tree.getRootSyntax();

    if (!preUnarySyntax.equals(result)) {
      boolean valid = false;
      for (HeadedSyntacticCategory headedInput : syntacticCategoryMap.get(preUnarySyntax)) {
        Assignment syntaxPrefixAssignment = unaryRuleInputVar.outcomeArrayToAssignment(headedInput);
        Iterator<Outcome> unaryRuleIter = unaryRuleFactor.outcomePrefixIterator(syntaxPrefixAssignment);
        while (unaryRuleIter.hasNext()) {
          Outcome outcome = unaryRuleIter.next();
          CcgUnaryRule rule = ((UnaryCombinator) outcome.getAssignment().getValue(unaryRuleVarNum)).getUnaryRule();

          if (rule.getResultSyntacticCategory().getSyntax()
              .assignAllFeatures(SyntacticCategory.DEFAULT_FEATURE_VALUE).equals(result)) {
            valid = true;
            break;
          }
        }
      }

      if (!valid) {
        System.out.println("No unary rule for: " + preUnarySyntax + " -> " + result);
        return false;
      }
    }

    if (tree.isTerminal()) {
      HeadedSyntacticCategory annotatedCategory = tree.getHeadedSyntacticCategory();
      boolean isPossible = false;
      for (HeadedSyntacticCategory category : syntacticCategoryMap.get(tree.getPreUnaryRuleSyntax())) {
        if (isPossibleLexiconEntry(tree.getWords(), tree.getPosTags(), category)) {
          if (annotatedCategory == null ||  category.equals(annotatedCategory)) {
            isPossible = true;
          }
        }
      }

      if (!isPossible) {
        String annotatedCatString = (annotatedCategory == null) ? "(no head info)" : ("headed: " + annotatedCategory); 
        System.out.println("No such lexicon entry: " + tree.getWords() + " -> " +
            syntacticCategoryMap.get(tree.getPreUnaryRuleSyntax()) + " " + annotatedCatString);
      }

      if (annotatedCategory != null && !annotatedCategory.getSyntax()
          .assignAllFeatures(SyntacticCategory.DEFAULT_FEATURE_VALUE).equals(tree.getPreUnaryRuleSyntax())) {
        System.out.println("Incompatible syntactic annotations: " + tree.getWords() + " -> " +
            annotatedCategory + " "+ tree.getPreUnaryRuleSyntax());
        isPossible = false;;
      } 

      return isPossible;
    } else {
      return isPossibleBinaryRule(tree.getLeft().getRootSyntax(), tree.getRight().getRootSyntax(),
          tree.getPreUnaryRuleSyntax(), syntacticCategoryMap)
          && isPossibleSyntacticTree(tree.getLeft(), syntacticCategoryMap)
          && isPossibleSyntacticTree(tree.getRight(), syntacticCategoryMap);
    }
  }

  public boolean isPossibleExample(CcgExample example,
      Multimap<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap) {
    if (example.hasDependencies()) {
      for (DependencyStructure dependency : example.getDependencies()) {
        if (!isPossibleDependencyStructure(dependency, example.getPosTags())) {
          System.out.println("Invalid dependency: " + dependency);
          return false;
        }
      }
    }

    if (example.hasSyntacticParse()) {
      if (!isPossibleSyntacticTree(example.getSyntacticParse(), syntacticCategoryMap)) {
        return false;
      }
    }
    return true;
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

  /**
   * Checks whether each example in {@code examples} can be produced
   * by this parser. If {@code errorOnInvalidExample = true}, then
   * this method throws an error if an invalid example is encountered.
   * Otherwise, invalid examples are simply filtered out of the
   * returned examples.
   * 
   * @param examples
   * @param errorOnInvalidExample
   * @return
   */
  public <T extends CcgExample> List<T> filterExampleCollection(Iterable<T> examples,
      boolean errorOnInvalidExample, Multimap<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap) {
    List<T> filteredExamples = Lists.newArrayList();
    for (T example : examples) {
      if (isPossibleExample(example, syntacticCategoryMap)) {
        filteredExamples.add(example);
      } else {
        Preconditions.checkState(!errorOnInvalidExample, "Invalid example: %s", example);
        System.out.println("Discarding example: " + example);
      }
    }
    return filteredExamples;
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
    return new CcgParser(terminalVar, ccgCategoryVar, terminalDistribution, terminalPosVar, terminalSyntaxVar,
        terminalPosDistribution, terminalSyntaxDistribution, dependencyHeadVar, dependencySyntaxVar,
        dependencyArgNumVar, dependencyArgVar, dependencyHeadPosVar, dependencyArgPosVar, dependencyDistribution,
        wordDistanceVar, wordDistanceFactor, puncDistanceVar, puncDistanceFactor, puncTagSet,
        verbDistanceVar, verbDistanceFactor, verbTagSet, leftSyntaxVar, rightSyntaxVar, combinatorVar,
        binaryRuleDistribution, unaryRuleInputVar, unaryRuleVar, unaryRuleFactor, 
        headedBinaryPredicateVar, headedBinaryPosVar, headedBinaryRuleDistribution,
        searchMoveVar, newCompiledSyntaxDistribution, rootSyntaxVar, rootPredicateVar, rootPosVar, 
        rootSyntaxDistribution, headedRootSyntaxDistribution, allowWordSkipping, normalFormOnly);
  }

  public DiscreteFactor getBinaryRuleDistribution() {
    return binaryRuleDistribution;
  }

  /**
   * Performs a beam search to find the best CCG parses of
   * {@code terminals}. Note that this is an approximate inference
   * strategy, and the returned parses may not be the best parses if
   * at any point during the search more than {@code beamSize} parse
   * trees exist for a span of the sentence.
   * 
   * @param terminals
   * @param posTags
   * @param beamSize
   * @param log
   * @return {@code beamSize} best parses for {@code terminals}.
   */
  public List<CcgParse> beamSearch(List<String> terminals, List<String> posTags, int beamSize, LogFunction log) {
    return beamSearch(terminals, posTags, beamSize, null, log, -1);
  }

  public List<CcgParse> beamSearch(List<String> terminals, List<String> posTags, int beamSize) {
    return beamSearch(terminals, posTags, beamSize, new NullLogFunction());
  }

  public List<CcgParse> beamSearch(List<String> terminals, int beamSize) {
    List<String> posTags = Collections.nCopies(terminals.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    return beamSearch(terminals, posTags, beamSize, new NullLogFunction());
  }
  
  public CcgParse parse(List<String> terminals) {
    List<String> posTags = Collections.nCopies(terminals.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    return parse(terminals, posTags, null, new NullLogFunction(), -1);
  }

  /**
   * 
   * @param terminals
   * @param posTags
   * @param beamSize
   * @param beamFilter May be {@code null}, in which case all beam
   * entries are retained.
   * @param log
   * @param maxParseTimeMillis (Approximate) maximum amount of time to
   * spend parsing. Returns an empty list of parses if the time limit
   * is exceeded. If negative, there is no time limit.
   * @return
   */
  public List<CcgParse> beamSearch(List<String> terminals, List<String> posTags, int beamSize,
      ChartFilter beamFilter, LogFunction log, long maxParseTimeMillis) {
    CcgBeamSearchChart chart = new CcgBeamSearchChart(terminals, posTags, beamSize);
    parseCommon(chart, terminals, posTags, beamFilter, log, maxParseTimeMillis);

    if (chart.isFinishedParsing()) {
      int numParses = Math.min(beamSize, chart.getNumChartEntriesForSpan(0, chart.size() - 1));
      return chart.decodeBestParsesForSpan(0, chart.size() - 1, numParses, this);
    } else {
      System.out.println("CCG Parser Timeout");
      return Lists.newArrayList();
    }
  }

  public CcgParse parse(List<String> terminals, List<String> posTags, ChartFilter beamFilter,
      LogFunction log, long maxParseTimeMillis) {
    CcgExactHashTableChart chart = new CcgExactHashTableChart(terminals, posTags);
    parseCommon(chart, terminals, posTags, beamFilter, log, maxParseTimeMillis);

    if (chart.isFinishedParsing()) {
      return chart.decodeBestParseForSpan(0, chart.size() - 1, this);
    } else {
      System.out.println("CCG Parser Timeout");
      return null;
    }
  }

  public void parseCommon(CcgChart chart, List<String> terminals, List<String> posTags,
      ChartFilter beamFilter, LogFunction log, long maxParseTimeMillis) {
    initializeChart(chart, terminals, posTags, beamFilter);
    initializeChartTerminals(terminals, posTags, chart, log);
    chart.applyChartFilterToTerminals();

    boolean finishedParsing = calculateInsideBeam(chart, log, maxParseTimeMillis);

    if (finishedParsing) {
      reweightRootEntries(chart);
    }
    chart.setFinishedParsing(finishedParsing);
  }

  private void initializeChart(CcgChart chart, List<String> terminals, List<String> posTags,
      ChartFilter chartFilter) {
    int numWords = terminals.size();
    int[] puncCounts = computeDistanceCounts(posTags, puncTagSet);
    int[] verbCounts = computeDistanceCounts(posTags, verbTagSet);

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

    chart.setPosTagsInt(getPosTagsInt(posTags));
    chart.setWordDistances(wordDistances);
    chart.setPuncDistances(puncDistances);
    chart.setVerbDistances(verbDistances);
    chart.setChartFilter(chartFilter);

    initializeChartDistributions(chart);
  }

  private void initializeChartDistributions(CcgChart chart) {
    // Default: initialize chart with no dependency distribution
    // pruning.
    chart.setDependencyTensor(dependencyTensor);
    chart.setWordDistanceTensor(wordDistanceTensor);
    chart.setPuncDistanceTensor(puncDistanceTensor);
    chart.setVerbDistanceTensor(verbDistanceTensor);
    chart.setSyntaxDistribution(compiledSyntaxDistribution);

    // Sparsifying the dependencies actually slows the code down.
    // (Possibly a cache issue?)
    // sparsifyDependencyDistribution(chart);
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
    for (int i = 0; i < entries.length; i++) {
      applyUnaryRules(chart, entries[i], probs[i], spanStart, spanEnd);
    }

    // Apply root factor.
    numChartEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd);
    entries = CcgBeamSearchChart.copyChartEntryArray(chart.getChartEntriesForSpan(spanStart, spanEnd),
        numChartEntries);
    probs = ArrayUtils.copyOf(chart.getChartEntryProbsForSpan(spanStart, spanEnd),
        numChartEntries);
    chart.clearChartEntriesForSpan(spanStart, spanEnd);

    Tensor rootSyntaxTensor = rootSyntaxDistribution.getWeights();
    Tensor headedRootTensor = headedRootSyntaxDistribution.getWeights();
    int[] currentPosTags = chart.getPosTagsInt();
    for (int i = 0; i < entries.length; i++) {
      ChartEntry entry = entries[i];
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
      chart.addChartEntryForSpan(entry, probs[i] * rootProb * headedRootProb, spanStart, spanEnd,
          syntaxVarType);
    }
    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);
  }

  /**
   * Performs a beam search over possible CCG parses given a
   * {@code chart} initialized with entries for all terminals.
   * 
   * @param chart
   * @param log
   */
  public boolean calculateInsideBeam(CcgChart chart, LogFunction log, long maxParseTimeMillis) {
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
        // System.out.println(spanStart + "." + spanEnd + " : " +
        // chart.getNumChartEntriesForSpan(spanStart, spanEnd));
      }
    }
    return true;
  }

  /**
   * Gets the possible lexicon entries for {@code wordSequence} that
   * can be used in this parser. The returned entries do not include
   * lexicon entries for unknown words, which may occur in the parse
   * if {@code wordSequence} is unrecognized.
   * 
   * @param wordSequence
   * @return
   */
  public List<LexiconEntry> getLexiconEntries(List<String> wordSequence) {
    return getLexiconEntriesFromFactor(wordSequence, terminalDistribution, terminalVar, ccgCategoryVar);
  }
  
  public List<LexiconEntry> getLexiconEntriesWithUnknown(List<String> originalWords, List<String> posTags) {
    Preconditions.checkArgument(originalWords.size() == posTags.size());
    List<String> words = preprocessInput(originalWords);    
    if (terminalVar.isValidOutcomeArray(words)) {
      return getLexiconEntries(words);
    } else if (words.size() == 1) {
      List<String> posTagBackoff = preprocessInput(Arrays.asList(UNKNOWN_WORD_PREFIX + posTags.get(0)));
      return getLexiconEntries(posTagBackoff);
    } else {
      return Collections.emptyList();
    }
  }
  
  public List<LexiconEntry> getLexiconEntriesWithUnknown(String word, String posTag) {
    return getLexiconEntriesWithUnknown(Arrays.asList(word), Arrays.asList(posTag));
  }

  /**
   * Gets the possible lexicon entries for {@code wordSequence} from
   * {@code terminalDistribution}, a distribution over CCG categories
   * given word sequences.
   * 
   * @param wordSequence
   * @param terminalDistribution
   * @param terminalVar
   * @param ccgCategoryVar
   * @return
   */
  public static List<LexiconEntry> getLexiconEntriesFromFactor(List<String> wordSequence,
      DiscreteFactor terminalDistribution, VariableNumMap terminalVar, VariableNumMap ccgCategoryVar) {
    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    if (terminalVar.isValidOutcomeArray(wordSequence)) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(wordSequence);

      Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory ccgCategory = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());

        lexiconEntries.add(new LexiconEntry(wordSequence, ccgCategory));
      }
    }
    return lexiconEntries;
  }

  /**
   * Initializes the parse chart with entries from the CCG lexicon for
   * {@code terminals}.
   * 
   * @param terminals
   * @param chart
   */
  public void initializeChartTerminals(List<String> terminals, List<String> posTags, CcgChart chart,
      LogFunction log) {
    initializeChartWithDistribution(terminals, posTags, chart, log, terminalVar, ccgCategoryVar,
        terminalDistribution, true);
  }

  /**
   * This method is a hack.
   * 
   * @param terminals
   * @param posTags
   * @param chart
   * @param log
   * @param terminalVar
   * @param terminalSyntaxVar
   * @param ccgCategoryVar
   * @param terminalPosVar
   * @param terminalDistribution
   * @param terminalPosDistribution
   */
  public void initializeChartWithDistribution(List<String> terminals, List<String> posTags, CcgChart chart,
      LogFunction log, VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, boolean useUnknownWords) {
    Preconditions.checkArgument(terminals.size() == posTags.size());

    List<String> preprocessedTerminals = preprocessInput(terminals);
    for (int i = 0; i < preprocessedTerminals.size(); i++) {
      for (int j = i; j < preprocessedTerminals.size(); j++) {
        List<String> terminalValue = preprocessedTerminals.subList(i, j + 1);
        String posTag = posTags.get(j);
        int numAdded = addChartEntriesForTerminal(terminalValue, posTag, i, j, chart,
            log, terminalVar, ccgCategoryVar, terminalDistribution);
        if (numAdded == 0 && i == j && useUnknownWords) {
          // Backoff to POS tags if the input is unknown.
          terminalValue = preprocessInput(Arrays.asList(UNKNOWN_WORD_PREFIX + posTags.get(i)));
          addChartEntriesForTerminal(terminalValue, posTag, i, j, chart, log,
              terminalVar, ccgCategoryVar, terminalDistribution);
        }
      }
    }
  }

  private int addChartEntriesForTerminal(List<String> terminalValue, String posTag,
      int spanStart, int spanEnd, CcgChart chart, LogFunction log,
      VariableNumMap terminalVar, VariableNumMap ccgCategoryVar, DiscreteFactor terminalDistribution) {
    int ccgCategoryVarNum = ccgCategoryVar.getOnlyVariableNum();
    Assignment assignment = terminalVar.outcomeArrayToAssignment(terminalValue);
    if (!terminalVar.isValidAssignment(assignment)) {
      return 0;
    }

    Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
    int numEntries = 0;
    while (iterator.hasNext()) {
      Outcome bestOutcome = iterator.next();
      CcgCategory category = (CcgCategory) bestOutcome.getAssignment().getValue(ccgCategoryVarNum);

      // Look up how likely this syntactic entry is to occur with
      // this part of speech.
      double posProb = getTerminalPosProbability(posTag, category.getSyntax());
      double syntaxProb = getTerminalSyntaxProbability(terminalValue, category.getSyntax());

      // Add all possible chart entries to the ccg chart.
      ChartEntry entry = ccgCategoryToChartEntry(terminalValue, category, spanStart, spanEnd);
      chart.addChartEntryForSpan(entry, bestOutcome.getProbability() * posProb * syntaxProb,
          spanStart, spanEnd, syntaxVarType);
      numEntries++;
    }
    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);
    return numEntries;
  }

  public double getTerminalPosProbability(String posTag, HeadedSyntacticCategory syntax) {
    Assignment posAssignment = terminalPosVar.outcomeArrayToAssignment(posTag);
    Assignment syntaxAssignment = terminalSyntaxVar.outcomeArrayToAssignment(syntax);
    // TODO: this check should be made unnecessary by preprocessing.
    if (terminalPosVar.isValidAssignment(posAssignment)) {
      return terminalPosDistribution.getUnnormalizedProbability(posAssignment.union(syntaxAssignment));
    } else {
      return 1.0;
    }
  }

  public double getTerminalSyntaxProbability(List<String> terminal, HeadedSyntacticCategory syntax) {
    if (terminalVar.isValidAssignment(terminalVar.outcomeArrayToAssignment(terminal))) {
      return terminalSyntaxDistribution.getUnnormalizedProbability(terminal, syntax);
    } else {
      return 1.0;
    }
  }

  public void sparsifyDependencyDistribution(CcgChart chart) {
    // Identify all possible assignments to the dependency head and
    // argument variables, so that we can look up probabilities in a
    // sparser tensor.
    Set<Long> possiblePredicates = Sets.newHashSet(predicatesInRules);
    int numTerminals = chart.size();
    for (int spanStart = 0; spanStart < numTerminals; spanStart++) {
      for (int spanEnd = spanStart; spanEnd < numTerminals; spanEnd++) {
        int numEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd);
        ChartEntry[] entries = chart.getChartEntriesForSpan(spanStart, spanEnd);
        for (int i = 0; i < numEntries; i++) {
          // Identify possible predicates.
          for (long assignment : entries[i].getAssignments()) {
            possiblePredicates.add((assignment >> ASSIGNMENT_PREDICATE_OFFSET) & PREDICATE_MASK);
          }
          for (long depLong : entries[i].getUnfilledDependencies()) {
            possiblePredicates.add(((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM);
          }
        }
      }
    }

    // Sparsify the dependency tensor for faster parsing.
    long[] keyNums = Longs.toArray(possiblePredicates);
    double[] values = new double[keyNums.length];
    Arrays.fill(values, 1.0);

    int headVarNum = dependencyTensor.getDimensionNumbers()[0];
    int argVarNum = dependencyTensor.getDimensionNumbers()[3];
    int predVarSize = dependencyTensor.getDimensionSizes()[0];

    SparseTensor keyIndicator = SparseTensor.fromUnorderedKeyValues(new int[] { headVarNum },
        new int[] { predVarSize }, keyNums, values);

    Tensor smallDependencyTensor = dependencyTensor.retainKeys(keyIndicator)
        .retainKeys(keyIndicator.relabelDimensions(new int[] { argVarNum }));

    Tensor smallWordDistanceTensor = wordDistanceTensor.retainKeys(keyIndicator);
    Tensor smallPuncDistanceTensor = puncDistanceTensor.retainKeys(keyIndicator);
    Tensor smallVerbDistanceTensor = verbDistanceTensor.retainKeys(keyIndicator);

    chart.setDependencyTensor(smallDependencyTensor);
    chart.setWordDistanceTensor(smallWordDistanceTensor);
    chart.setPuncDistanceTensor(smallPuncDistanceTensor);
    chart.setVerbDistanceTensor(smallVerbDistanceTensor);
  }

  public ChartEntry ccgCategoryToChartEntry(List<String> terminalWords, CcgCategory result,
      int spanStart, int spanEnd) {
    // Assign each predicate in this category a unique word index.
    List<Long> assignments = Lists.newArrayList();
    List<Set<String>> values = result.getAssignment();
    int[] semanticVariables = result.getSemanticVariables();
    for (int i = 0; i < values.size(); i++) {
      for (String value : values.get(i)) {
        long assignment = marshalAssignment(semanticVariables[i],
            dependencyHeadType.getValueIndex(value), spanEnd);
        assignments.add(assignment);
      }
    }

    List<UnfilledDependency> deps = Lists.newArrayList();
    List<UnfilledDependency> unfilledDeps = result.createUnfilledDependencies(spanEnd, deps);

    long[] unfilledDepArray = unfilledDependencyArrayToLongArray(unfilledDeps);
    long[] depArray = unfilledDependencyArrayToLongArray(deps);

    int syntaxAsInt = syntaxVarType.getValueIndex(result.getSyntax());
    int syntaxHeadVar = result.getSyntax().getHeadVariable();
    return new ChartEntry(syntaxAsInt, result.getSyntax().getUniqueVariables(), syntaxHeadVar, 
        result, terminalWords, null, Longs.toArray(assignments), unfilledDepArray, depArray,
        spanStart, spanEnd);
  }

  private void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart, LogFunction log) {
    long[] depFillingAccumulator = new long[20];
    long[] assignmentAccumulator = new long[50];
    long[] filledDepAccumulator = new long[20];
    long[] unfilledDepAccumulator = new long[20];

    Tensor syntaxDistributionTensor = chart.getSyntaxDistribution().getWeights();
    Tensor binaryRuleTensor = binaryRuleDistribution.getWeights();

    Tensor currentDependencyTensor = chart.getDependencyTensor();
    Tensor currentWordTensor = chart.getWordDistanceTensor();
    Tensor currentPuncTensor = chart.getPuncDistanceTensor();
    Tensor currentVerbTensor = chart.getVerbDistanceTensor();

    int[] currentPosTags = chart.getPosTagsInt();
    int[] wordDistances = chart.getWordDistances();
    int[] puncDistances = chart.getPunctuationDistances();
    int[] verbDistances = chart.getVerbDistances();
    int numTerminals = chart.size();

    long depCache = -1;
    double depProbCache = 0.0;

    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j is for forward compatibility for skipping terminal
      // symbols.
      int maxInd = allowWordSkipping ? spanEnd - spanStart : i + 2;
      for (int j = i + 1; j < maxInd; j++) {
        ChartEntry[] leftTrees = chart.getChartEntriesForSpan(spanStart, spanStart + i);
        double[] leftProbs = chart.getChartEntryProbsForSpan(spanStart, spanStart + i);
        int numLeftTrees = chart.getNumChartEntriesForSpan(spanStart, spanStart + i);
        Multimap<Integer, Integer> leftTypes = aggregateBySyntacticType(leftTrees, numLeftTrees);

        ChartEntry[] rightTrees = chart.getChartEntriesForSpan(spanStart + j, spanEnd);
        double[] rightProbs = chart.getChartEntryProbsForSpan(spanStart + j, spanEnd);
        int numRightTrees = chart.getNumChartEntriesForSpan(spanStart + j, spanEnd);
        Multimap<Integer, Integer> rightTypes = aggregateBySyntacticType(rightTrees, numRightTrees);

        int[] key = new int[1];
        // log.startTimer("ccg_parse/beam_loop");
        for (int leftType : leftTypes.keySet()) {
          key[0] = leftType;
          long keyNumPrefix = syntaxDistributionTensor.dimKeyPrefixToKeyNum(key);
          int index = syntaxDistributionTensor.getNearestIndex(keyNumPrefix);
          double[] syntaxValues = syntaxDistributionTensor.getValues();
          long[] dimensionOffsets = syntaxDistributionTensor.getDimensionOffsets();
          int tensorSize = syntaxDistributionTensor.size();
          if (index == -1 || index >= tensorSize) {
            continue;
          }
          long maxKeyNum = keyNumPrefix + dimensionOffsets[0];
          long curKeyNum = syntaxDistributionTensor.indexToKeyNum(index);

          while (curKeyNum < maxKeyNum && index < tensorSize) {
            if (syntaxValues[index] != 0.0) {
              int rightType = (int) (((curKeyNum - keyNumPrefix) / dimensionOffsets[1]) % dimensionOffsets[0]);
              if (rightTypes.containsKey(rightType)) {
                // Get the operation we're supposed to apply at this chart entry.
                CcgSearchMove searchMove = (CcgSearchMove) searchMoveType.getValue(
                    (int) ((curKeyNum - keyNumPrefix) % dimensionOffsets[1]));
                Combinator resultCombinator = searchMove.getBinaryCombinator();

                // Apply the binary rule.
                // log.startTimer("ccg_parse/beam_loop/binary");
                double ruleProb = binaryRuleTensor.get(searchMove.getBinaryCombinatorKeyNum());
                // log.stopTimer("ccg_parse/beam_loop/binary");

                int resultSyntax = resultCombinator.getSyntax();
                int[] resultSyntaxUniqueVars = resultCombinator.getSyntaxUniqueVars();
                int resultSyntaxHead = resultCombinator.getSyntaxHeadVar();

                for (int leftIndex : leftTypes.get(leftType)) {
                  ChartEntry leftRoot = leftTrees[leftIndex];
                  double leftProb = leftProbs[leftIndex];

                  // log.startTimer("ccg_parse/beam_loop/unary");
                  long leftUnaryKeyNum = searchMove.getLeftUnaryKeyNum();
                  if (leftUnaryKeyNum != -1) {
                    leftProb *= unaryRuleTensor.get(leftUnaryKeyNum);
                  }
                  // log.stopTimer("ccg_parse/beam_loop/unary");

                  deploop: for (int rightIndex : rightTypes.get(rightType)) {
                    ChartEntry rightRoot = rightTrees[rightIndex];
                    double rightProb = rightProbs[rightIndex];

                    // Determine if these chart entries can be combined under the
                    // normal form constraints. Normal form constraints state that 
                    // the result of a forward (backward) composition cannot be the
                    // left (right) element of a forward (backward) combinator.
                    if (normalFormOnly) {
                      Combinator.Type leftCombinator = leftRoot.getDerivingCombinatorType();
                      Combinator.Type resultCombinatorType = resultCombinator.getType();
                      if (leftCombinator == Combinator.Type.FORWARD_COMPOSITION
                          && searchMove.getLeftUnaryKeyNum() == -1
                          && (resultCombinatorType == Combinator.Type.FORWARD_APPLICATION
                              || resultCombinatorType == Combinator.Type.FORWARD_COMPOSITION)) {
                        continue;
                      }

                      Combinator.Type rightCombinator = rightRoot.getDerivingCombinatorType();
                      if (rightCombinator == Combinator.Type.BACKWARD_COMPOSITION
                          && searchMove.getRightUnaryKeyNum() == -1
                          && (resultCombinator.getType() == Combinator.Type.BACKWARD_APPLICATION
                          || resultCombinator.getType() == Combinator.Type.BACKWARD_COMPOSITION)) {
                        continue;
                      }
                    }

                    // log.startTimer("ccg_parse/beam_loop/unary");
                    long rightUnaryKeyNum = searchMove.getRightUnaryKeyNum();
                    if (rightUnaryKeyNum != -1) {
                      rightProb *= unaryRuleTensor.get(searchMove.getRightUnaryKeyNum());
                    }
                    // log.stopTimer("ccg_parse/beam_loop/unary");

                    // log.startTimer("ccg_parse/beam_loop/relabel");
                    // Relabel assignments from the left and right
                    // chart entries.
                    int numAssignments = relabelAssignment(leftRoot, searchMove.getLeftRelabeling(),
                        assignmentAccumulator, 0);
                    numAssignments = relabelAssignment(rightRoot, searchMove.getRightRelabeling(),
                        assignmentAccumulator, numAssignments);

                    // Relabel dependencies from the left and right chart entries, 
                    // and the combinator (if necessary) so the assignment and the 
                    // dependencies use a common variable numbering scheme.
                    int numDepFillingAccumulator = 0;
                    numDepFillingAccumulator = leftRoot.getUnfilledDependenciesRelabeled(
                        searchMove.getLeftRelabeling(), depFillingAccumulator, numDepFillingAccumulator);
                    if (numDepFillingAccumulator == -1) { continue; }
                    numDepFillingAccumulator = rightRoot.getUnfilledDependenciesRelabeled(
                        searchMove.getRightRelabeling(), depFillingAccumulator, numDepFillingAccumulator);
                    if (numDepFillingAccumulator == -1) { continue; }
                    if (resultCombinator.hasUnfilledDependencies()) {
                      numDepFillingAccumulator = resultCombinator.getUnfilledDependencies(this, spanEnd, 
                          depFillingAccumulator, numDepFillingAccumulator);
                      if (numDepFillingAccumulator == -1) { continue; }
                    }
                    
                    // Fill dependencies based on the current assignment.
                    int numFilledDeps = 0;
                    int numUnfilledDeps = 0;
                    int[] variablesToUnify = resultCombinator.getUnifiedVariables();
                    int[] returnOriginalVars = resultCombinator.getResultOriginalVars();
                    int[] returnVarsRelabeling = resultCombinator.getResultVariableRelabeling();
                    for (int k = 0; k < numDepFillingAccumulator; k++) {
                      long unfilledDependency = depFillingAccumulator[k];
                      int objectArgNum = getObjectArgNumFromDep(unfilledDependency);

                      boolean depWasFilled = false;
                      if (Ints.contains(variablesToUnify, objectArgNum)) {
                        // Determine if the assignment contains a value for this variable.
                        for (int l = 0; l < numAssignments; l++) {
                          long curAssignment = assignmentAccumulator[l];
                          if (CcgParser.getAssignmentVarNum(curAssignment) == objectArgNum) {
                            // Create a new filled dependency by substituting in the
                            // current object.
                            long filledDep = unfilledDependency - (objectArgNum << OBJECT_OFFSET);
                            filledDep |= (((long) CcgParser.getAssignmentPredicateNum(curAssignment)) + MAX_ARG_NUM) << OBJECT_OFFSET;
                            filledDep |= ((long) CcgParser.getAssignmentWordIndex(curAssignment)) << OBJECT_WORD_IND_OFFSET;

                            if (numFilledDeps >= filledDepAccumulator.length) {
                              continue deploop;
                            }
                            filledDepAccumulator[numFilledDeps] = filledDep;
                            numFilledDeps++;
                            depWasFilled = true;
                          }
                        }
                      }

                      int returnOriginalVarIndex = Ints.indexOf(returnOriginalVars, objectArgNum);
                      if (!depWasFilled && returnOriginalVarIndex != -1) {
                        if (numUnfilledDeps >= unfilledDepAccumulator.length) {
                          continue deploop;
                        }
                        int relabeledVarNum = returnVarsRelabeling[returnOriginalVarIndex];

                        long relabeledDep = unfilledDependency - (objectArgNum << OBJECT_OFFSET);
                        relabeledDep |= (relabeledVarNum << OBJECT_OFFSET);

                        unfilledDepAccumulator[numUnfilledDeps] = relabeledDep;
                        numUnfilledDeps++;
                      }
                    }
                    long[] filledDepArray = Arrays.copyOf(filledDepAccumulator, numFilledDeps);
                    long[] unfilledDepArray = Arrays.copyOf(unfilledDepAccumulator, numUnfilledDeps);
                    // log.stopTimer("ccg_parse/beam_loop/relabel");

                    // log.startTimer("ccg_parse/beam_loop/create_assignment_and_chart");
                    numAssignments = filterAssignmentVariables(assignmentAccumulator,
                        resultCombinator.getResultOriginalVars(), resultCombinator.getResultVariableRelabeling(), 
                        numAssignments);
                    long[] newAssignments = ArrayUtils.copyOfRange(assignmentAccumulator, 0, numAssignments);

                    ChartEntry result = new ChartEntry(resultSyntax, resultSyntaxUniqueVars, resultSyntaxHead,
                        null, searchMove.getLeftUnary(), searchMove.getRightUnary(), newAssignments,
                        unfilledDepArray, filledDepArray, spanStart, spanStart + i,
                        leftIndex, spanStart + j, spanEnd, rightIndex, resultCombinator);
                    // log.stopTimer("ccg_parse/beam_loop/create_assignment_and_chart");

                    // Get the weights of applying this syntactic combination rule 
                    // given the word and POS tag of the result's head.
                    double headedRuleProb = 1.0;
                    long binaryCombinatorKeyNumWithOffset = searchMove.getBinaryCombinatorKeyNum()
                        * headedBinaryRuleCombinatorOffset;
                    for (int assignmentIndex = 0; assignmentIndex < newAssignments.length; assignmentIndex++) {
                      long assignment = newAssignments[assignmentIndex];
                      int varNum = (int) ((assignment >> ASSIGNMENT_VAR_NUM_OFFSET) & VAR_NUM_MASK); 
                      if (varNum == resultSyntaxHead) {
                        long predicate = (assignment >> ASSIGNMENT_PREDICATE_OFFSET) & PREDICATE_MASK;
                        int wordIndex = (int) ((assignment >> ASSIGNMENT_WORD_IND_OFFSET) & WORD_IND_MASK);
                        int posTag = currentPosTags[wordIndex];

                        long combinatorWordPosKeyNum = binaryCombinatorKeyNumWithOffset 
                            + (predicate * headedBinaryRulePredicateOffset)
                            + (posTag * headedBinaryRulePosOffset);
                        headedRuleProb *= headedBinaryRuleTensor.get(combinatorWordPosKeyNum);
                      }
                    }

                    // log.startTimer("ccg_parse/beam_loop/dependencies");
                    // Get the weights of the generated dependencies.
                    double depProb = 1.0;
                    double curDepProb = 1.0;
                    int filledDepArrayLength = filledDepArray.length;
                    for (int depIndex = 0; depIndex < filledDepArrayLength; depIndex++) {
                      long depLong = filledDepArray[depIndex];
                      if (depLong == depCache) {
                        depProb *= depProbCache;
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
                      curDepProb = currentDependencyTensor.get(depNum);
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
                      curDepProb *= currentWordTensor.get(wordDistanceKeyNum);
                      long puncDistanceKeyNum = distanceKeyNumBase + (puncDistance * distanceDistanceOffset);
                      curDepProb *= currentPuncTensor.get(puncDistanceKeyNum);
                      long verbDistanceKeyNum = distanceKeyNumBase + (verbDistance * distanceDistanceOffset);
                      curDepProb *= currentVerbTensor.get(verbDistanceKeyNum);
                      // log.stopTimer("chart_entry/lookup_distance");
                      // System.out.println(longToUnfilledDependency(depLong)
                      // + " " + depProb);

                      depProb *= curDepProb;

                      depCache = depLong;
                      depProbCache = curDepProb;
                    }
                    // log.stopTimer("ccg_parse/beam_loop/dependencies");

                    // log.startTimer("chart_entry/add_chart_entry");
                    double totalProb = ruleProb * headedRuleProb * leftProb * rightProb * depProb;
                    chart.addChartEntryForSpan(result, totalProb, spanStart, spanEnd, syntaxVarType);
                    // log.stopTimer("chart_entry/add_chart_entry");
                  }
                }
              }
            }

            // Advance the iterator over rules.
            index++;
            if (index < tensorSize) {
              curKeyNum = syntaxDistributionTensor.indexToKeyNum(index);
            }
          }
        }
        // log.stopTimer("ccg_parse/beam_loop");
      }
    }

    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);
  }

  private Multimap<Integer, Integer> aggregateBySyntacticType(
      ChartEntry[] entries, int numEntries) {
    Multimap<Integer, Integer> map = HashMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      map.put(entries[i].getHeadedSyntax(), i);
    }
    return map;
  }

  /**
   * Calculates the probability of any new dependencies in
   * {@code result}, then inserts it into {@code chart}.
   * 
   * @param result
   * @param chart
   * @param leftRightProb
   * @param spanStart
   * @param spanEnd
   */
  public void addChartEntryWithDependencies(ChartEntry result, CcgChart chart, double leftRightProb,
      int spanStart, int spanEnd, LogFunction log, long[] depCache, double[] depProbCache) {
  }

  private void applyUnaryRules(CcgChart chart, ChartEntry result, double resultProb, int spanStart, int spanEnd) {
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

      long[] relabeledAssignments = result.getAssignmentsRelabeled(unaryRuleCombinator.getVariableRelabeling());
      long[] relabeledUnfilledDeps = result.getUnfilledDependenciesRelabeled(unaryRuleCombinator.getVariableRelabeling());

      ChartEntry unaryRuleResult = result.applyUnaryRule(unaryRuleCombinator.getSyntax(),
          unaryRuleCombinator.getSyntaxUniqueVars(), unaryRuleCombinator.getSyntaxHeadVar(), unaryRuleCombinator,
          relabeledAssignments, relabeledUnfilledDeps, result.getDependencies());
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

  private int relabelAssignment(ChartEntry entry, int[] relabeling,
      long[] assignmentAccumulator, int startIndex) {
    long[] assignments = entry.getAssignments();

    int numAssigned = 0;
    for (int i = 0; i < assignments.length; i++) {
      int assignmentVarNum = (int) ((assignments[i] >> ASSIGNMENT_VAR_NUM_OFFSET) & VAR_NUM_MASK);
      int newVarNum = relabeling[assignmentVarNum];
      if (newVarNum != -1) {
        assignmentAccumulator[startIndex + numAssigned] = CcgParser.replaceAssignmentVarNum(assignments[i],
            assignmentVarNum, newVarNum);
        numAssigned++;
      }
    }

    return startIndex + numAssigned;
  }

  private int filterAssignmentVariables(long[] assignmentAccumulator,
      int[] varsToRetain, int[] relabeling, int accumulatorSize) {
    int numRemoved = 0;
    for (int i = 0; i < accumulatorSize; i++) {
      long assignment = assignmentAccumulator[i];
      int assignmentVarNum = (int) ((assignment >> ASSIGNMENT_VAR_NUM_OFFSET) & VAR_NUM_MASK);
      int index = Ints.indexOf(varsToRetain, assignmentVarNum);
      if (index != -1) {
        int newVarNum = relabeling[index];
        assignmentAccumulator[i - numRemoved] = CcgParser.replaceAssignmentVarNum(assignment,
            assignmentVarNum, newVarNum);
      } else {
        numRemoved++;
      }
    }
    return accumulatorSize - numRemoved;
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
}