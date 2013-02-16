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
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.ChartFilter;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
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

  // Weights on dependency structures.
  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
  private final DiscreteFactor dependencyDistribution;
  private final DiscreteVariable dependencyHeadType;
  private final DiscreteVariable dependencyArgNumType;

  private final Tensor dependencyTensor;
  private final long dependencyHeadOffset;
  private final long dependencyArgNumOffset;
  private final long dependencyObjectOffset;

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
  private final long distanceArgNumOffset;
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

  // Weights on binary combination rules syntactic structures.
  private final VariableNumMap leftSyntaxVar;
  private final VariableNumMap rightSyntaxVar;
  private final VariableNumMap parentSyntaxVar;
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

  private final VariableNumMap searchMoveVar;
  private final DiscreteVariable searchMoveType;
  private final DiscreteFactor compiledSyntaxDistribution;

  // Weights on the syntactic category of the root of the CCG parse.
  private final VariableNumMap rootSyntaxVar;
  private final DiscreteFactor rootSyntaxDistribution;

  // All predicates used in CCG rules.
  private final Set<Long> predicatesInRules;

  public CcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap terminalPosVar, VariableNumMap terminalSyntaxVar,
      DiscreteFactor terminalPosDistribution, VariableNumMap dependencyHeadVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      DiscreteFactor dependencyDistribution, VariableNumMap wordDistanceVar,
      DiscreteFactor wordDistanceFactor, VariableNumMap puncDistanceVar,
      DiscreteFactor puncDistanceFactor, Set<String> puncTagSet, VariableNumMap verbDistanceVar,
      DiscreteFactor verbDistanceFactor, Set<String> verbTagSet, VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      DiscreteFactor binaryRuleDistribution, VariableNumMap unaryRuleInputVar,
      VariableNumMap unaryRuleVar, DiscreteFactor unaryRuleFactor, VariableNumMap searchMoveVar,
      DiscreteFactor compiledSyntaxDistribution, VariableNumMap rootSyntaxVar, DiscreteFactor rootSyntaxDistribution) {
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

    Preconditions.checkArgument(dependencyDistribution.getVars().equals(
        VariableNumMap.unionAll(dependencyHeadVar, dependencyArgNumVar, dependencyArgVar)));
    Preconditions.checkArgument(dependencyHeadVar.getOnlyVariableNum() < dependencyArgNumVar.getOnlyVariableNum());
    Preconditions.checkArgument(dependencyArgNumVar.getOnlyVariableNum() < dependencyArgVar.getOnlyVariableNum());
    this.dependencyHeadVar = dependencyHeadVar;
    this.dependencyArgNumVar = dependencyArgNumVar;
    this.dependencyArgVar = dependencyArgVar;
    this.dependencyDistribution = dependencyDistribution;
    this.dependencyHeadType = dependencyHeadVar.getDiscreteVariables().get(0);
    this.dependencyArgNumType = dependencyArgNumVar.getDiscreteVariables().get(0);
    DiscreteVariable dependencyArgType = dependencyArgVar.getDiscreteVariables().get(0);
    Preconditions.checkArgument(dependencyHeadType.equals(dependencyArgType));
    this.dependencyTensor = dependencyDistribution.getWeights();
    this.dependencyHeadOffset = dependencyTensor.getDimensionOffsets()[0];
    this.dependencyArgNumOffset = dependencyTensor.getDimensionOffsets()[1];
    this.dependencyObjectOffset = dependencyTensor.getDimensionOffsets()[2];

    this.wordDistanceVar = wordDistanceVar;
    this.wordDistanceFactor = wordDistanceFactor;
    VariableNumMap expectedWordVars = VariableNumMap.unionAll(dependencyHeadVar, dependencyArgNumVar, wordDistanceVar);
    Preconditions.checkArgument(expectedWordVars.equals(wordDistanceFactor.getVars()));
    this.wordDistanceTensor = wordDistanceFactor.getWeights();

    this.puncDistanceVar = puncDistanceVar;
    this.puncDistanceFactor = puncDistanceFactor;
    VariableNumMap expectedPuncVars = VariableNumMap.unionAll(dependencyHeadVar, dependencyArgNumVar, puncDistanceVar);
    Preconditions.checkArgument(expectedPuncVars.equals(puncDistanceFactor.getVars()));
    this.puncDistanceTensor = puncDistanceFactor.getWeights();
    this.puncTagSet = puncTagSet;

    this.verbDistanceVar = verbDistanceVar;
    this.verbDistanceFactor = verbDistanceFactor;
    VariableNumMap expectedVerbVars = VariableNumMap.unionAll(dependencyHeadVar, dependencyArgNumVar, verbDistanceVar);
    Preconditions.checkArgument(expectedVerbVars.equals(verbDistanceFactor.getVars()));
    this.verbDistanceTensor = verbDistanceFactor.getWeights();
    this.verbTagSet = verbTagSet;

    this.distanceHeadOffset = verbDistanceTensor.getDimensionOffsets()[0];
    this.distanceArgNumOffset = verbDistanceTensor.getDimensionOffsets()[1];
    this.distanceDistanceOffset = verbDistanceTensor.getDimensionOffsets()[2];

    this.leftSyntaxVar = Preconditions.checkNotNull(leftSyntaxVar);
    this.rightSyntaxVar = Preconditions.checkNotNull(rightSyntaxVar);
    this.parentSyntaxVar = Preconditions.checkNotNull(parentSyntaxVar);
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

    this.searchMoveVar = Preconditions.checkNotNull(searchMoveVar);
    this.searchMoveType = (DiscreteVariable) searchMoveVar.getOnlyVariable();
    Preconditions.checkArgument(compiledSyntaxDistribution.getVars().equals(
        VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, searchMoveVar)));
    this.compiledSyntaxDistribution = Preconditions.checkNotNull(compiledSyntaxDistribution);

    this.rootSyntaxVar = Preconditions.checkNotNull(rootSyntaxVar);
    this.rootSyntaxDistribution = Preconditions.checkNotNull(rootSyntaxDistribution);

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
    for (int i = 0; i < syntaxType.numValues(); i++) {
      System.out.println(i + " " + syntaxType.getValue(i));
    }
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
    if (argumentOnLeft) {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, argumentRelabeling,
          functionRelabeling, resultRelabeling, resultRelabeling, unifiedVariables,
          new String[0], new int[0], new int[0], argumentOnLeft, 0, null);
    } else {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionRelabeling,
          argumentRelabeling, resultRelabeling, resultRelabeling, unifiedVariables, new String[0],
          new int[0], new int[0], argumentOnLeft, 0, null);
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
    for (int i = argumentReturnDepth; i > 0; i--) {
      HeadedSyntacticCategory curArg = relabeledArgumentType;
      int headVariable = argumentAsHead ? relabeledArgumentType.getRootVariable() : functionCat.getRootVariable();
      for (int j = 0; j < (i - 1); j++) {
        curArg = curArg.getReturnType();
        headVariable = curArg.getRootVariable();
      }
      Direction curDirection = curArg.getDirection();
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

    int functionReturnTypeInt = syntaxVarType.getValueIndex(canonicalResultType);
    int[] functionReturnTypeVars = canonicalResultType.getUniqueVariables();
    if (argumentOnLeft) {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, argumentCatRelabeling,
          functionCatRelabeling, resultUniqueVars, resultCatRelabeling, unifiedVariables,
          new String[0], new int[0], new int[0], argumentOnLeft, argumentReturnDepth, null);
    } else {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionCatRelabeling,
          argumentCatRelabeling, resultUniqueVars, resultCatRelabeling, unifiedVariables,
          new String[0], new int[0], new int[0], argumentOnLeft, argumentReturnDepth, null);
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
    return new Combinator(parentInt, parentVars, leftRelabelingArray, rightRelabelingArray,
        parentOriginalVars, parentRelabelingArray, unifiedVariables, rule.getSubjects(),
        rule.getArgumentNumbers(), rule.getObjects(), false, -1, rule);
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
            returnType.getUniqueVariables(), patternToChart, rule);

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
        newLeftRelabeling[i] = leftRelabeling[leftUnaryRelabeling[i]];
      }
    }

    if (rightUnary != null) {
      int[] rightUnaryRelabeling = rightUnary.getVariableRelabeling();
      newRightRelabeling = new int[rightUnaryRelabeling.length];
      for (int i = 0; i < newRightRelabeling.length; i++) {
        newRightRelabeling[i] = rightRelabeling[rightUnaryRelabeling[i]];
      }
    }

    return new CcgSearchMove(combinator, leftUnary, rightUnary, combinatorKeyNum, leftKeyNum, rightKeyNum,
        newLeftRelabeling, newRightRelabeling);
  }

  public boolean isPossibleDependencyStructure(DependencyStructure dependency) {
    Assignment assignment = Assignment.unionAll(
        dependencyHeadVar.outcomeArrayToAssignment(dependency.getHead()),
        dependencyArgNumVar.outcomeArrayToAssignment(dependency.getArgIndex()),
        dependencyArgVar.outcomeArrayToAssignment(dependency.getObject()));

    return dependencyDistribution.getVars().isValidAssignment(assignment) &&
        dependencyDistribution.getUnnormalizedLogProbability(assignment) != Double.NEGATIVE_INFINITY;
  }

  private static List<String> preprocessInput(List<String> terminals) {
    List<String> preprocessedTerminals = Lists.newArrayList();
    for (String terminal : terminals) {
      preprocessedTerminals.add(terminal.toLowerCase());
    }
    return preprocessedTerminals;
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
    // System.out.println("No such lexicon entry: " + words + " -> " + category);
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
                parentSyntaxVar.getOnlyVariableNum());
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
      boolean isPossible = false;
      for (HeadedSyntacticCategory category : syntacticCategoryMap.get(tree.getPreUnaryRuleSyntax())) {
        isPossible = isPossible || isPossibleLexiconEntry(tree.getWords(), tree.getPosTags(), category);
      }
      
      if (!isPossible) {
        System.out.println("No such lexicon entry: " + tree.getWords() + " -> " +
            syntacticCategoryMap.get(tree.getPreUnaryRuleSyntax()));
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
        if (!isPossibleDependencyStructure(dependency)) {
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
  public List<CcgExample> filterExampleCollection(Iterable<CcgExample> examples,
      boolean errorOnInvalidExample, Multimap<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap) {
    List<CcgExample> filteredExamples = Lists.newArrayList();
    for (CcgExample example : examples) {
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
  public List<CcgParse> beamSearch(List<String> terminals, List<String> posTags, int beamSize, LogFunction log) {
    return beamSearch(terminals, posTags, beamSize, null, log);
  }

  public List<CcgParse> beamSearch(List<String> terminals, List<String> posTags, int beamSize) {
    return beamSearch(terminals, posTags, beamSize, new NullLogFunction());
  }

  public List<CcgParse> beamSearch(List<String> terminals, int beamSize) {
    List<String> posTags = Collections.nCopies(terminals.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    return beamSearch(terminals, posTags, beamSize, new NullLogFunction());
  }

  /**
   * 
   * @param terminals
   * @param posTags
   * @param beamSize
   * @param beamFilter May be {@code null}, in which case all beam
   * entries are retained.
   * @param log
   * @return
   */
  public List<CcgParse> beamSearch(List<String> terminals, List<String> posTags, int beamSize,
      ChartFilter beamFilter, LogFunction log) {

    CcgChart chart = buildChartForInput(terminals, posTags, beamSize, beamFilter);
    System.out.println(terminals);
    System.out.println(posTags);

    log.startTimer("ccg_parse/initialize_chart");
    initializeChart(terminals, posTags, chart, log);
    log.stopTimer("ccg_parse/initialize_chart");
    
    chart.applyChartFilterToTerminals();

    // Prune dependencies, etc., which will not be used 
    // while parsing this sentence.
    sparsifyDependencyDistribution(chart);

    log.startTimer("ccg_parse/calculate_inside_beam");
    calculateInsideBeam(chart, log);
    log.stopTimer("ccg_parse/calculate_inside_beam");

    reweightRootEntries(chart);

    return decodeParsesForRoot(chart);
  }
  
  public CcgChart buildChartForInput(List<String> terminals, List<String> posTags, int beamSize,
      ChartFilter beamFilter) {
    int numWords = terminals.size();
    int[] puncCounts = computeDistanceCounts(posTags, puncTagSet);
    int[] verbCounts = computeDistanceCounts(posTags, verbTagSet);

    int[] wordDistances = new int[numWords * numWords];
    int[] puncDistances = new int[numWords * numWords];
    int[] verbDistances = new int[numWords * numWords];
    for (int i = 0; i < numWords; i++) {
      for (int j = 0 ;j < numWords; j++) {
        wordDistances[(i * numWords) + j] = computeWordDistance(i, j);
        puncDistances[(i * numWords) + j] = computeArrayDistance(puncCounts, i, j);
        verbDistances[(i * numWords) + j] = computeArrayDistance(verbCounts, i, j);
      }
    }

    CcgChart chart = new CcgChart(terminals, posTags, wordDistances, puncDistances, verbDistances,
        beamSize, beamFilter);
    
    // Default: initialize chart with no dependency distribution pruning.
    chart.setDependencyTensor(dependencyTensor);
    chart.setWordDistanceTensor(wordDistanceTensor);
    chart.setPuncDistanceTensor(puncDistanceTensor);
    chart.setVerbDistanceTensor(verbDistanceTensor);
    chart.setSyntaxDistribution(compiledSyntaxDistribution);

    return chart;
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
    ChartEntry[] entries = CcgChart.copyChartEntryArray(chart.getChartEntriesForSpan(spanStart, spanEnd),
        numChartEntries);
    double[] probs = ArrayUtils.copyOf(chart.getChartEntryProbsForSpan(spanStart, spanEnd),
        numChartEntries);
    for (int i = 0; i < entries.length; i++) {
      applyUnaryRules(chart, entries[i], probs[i], spanStart, spanEnd);
    }

    // Apply root factor.
    numChartEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd);
    entries = CcgChart.copyChartEntryArray(chart.getChartEntriesForSpan(spanStart, spanEnd),
        numChartEntries);
    probs = ArrayUtils.copyOf(chart.getChartEntryProbsForSpan(spanStart, spanEnd),
        numChartEntries);
    chart.clearChartEntriesForSpan(spanStart, spanEnd);

    Tensor rootSyntaxTensor = rootSyntaxDistribution.getWeights();
    for (int i = 0; i < entries.length; i++) {
      ChartEntry entry = entries[i];
      double rootProb = rootSyntaxTensor.get(entry.getHeadedSyntax());
      chart.addChartEntryForSpan(entry, probs[i] * rootProb, spanStart, spanEnd, syntaxVarType);
    }
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
        // System.out.println(spanStart + "." + spanEnd + " : " +
        // chart.getNumChartEntriesForSpan(spanStart, spanEnd));
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
    return chart.decodeBestParsesForSpan(0, chart.size() - 1, numParses, this, syntaxVarType);
  }

  /**
   * Initializes the parse chart with entries from the CCG lexicon for
   * {@code terminals}.
   * 
   * @param terminals
   * @param chart
   */
  public void initializeChart(List<String> terminals, List<String> posTags, CcgChart chart,
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

      // Add all possible chart entries to the ccg chart.
      ChartEntry entry = ccgCategoryToChartEntry(terminalValue, category, spanStart, spanEnd);
      addChartEntryWithDependencies(entry, chart, bestOutcome.getProbability() * posProb, spanStart, spanEnd, log);
      numEntries++;
    }
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
          for (int assignmentPredicateNum : entries[i].getAssignmentPredicateNums()) {
            possiblePredicates.add((long) assignmentPredicateNum);
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

    int syntaxAsInt = syntaxVarType.getValueIndex(result.getSyntax());
    return new ChartEntry(syntaxAsInt, result.getSyntax().getUniqueVariables(), result, terminalWords,
        null, Ints.toArray(variableNums), Ints.toArray(predicateNums), Ints.toArray(indexes),
        unfilledDepArray, depArray, spanStart, spanEnd);
  }

  private void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart, LogFunction log) {
    long[] depAccumulator = new long[20];
    int[] assignmentVariableAccumulator = new int[50];
    int[] assignmentPredicateAccumulator = new int[50];
    int[] assignmentIndexAccumulator = new int[50];

    Tensor syntaxDistributionTensor = chart.getSyntaxDistribution().getWeights();
    Tensor binaryRuleTensor = binaryRuleDistribution.getWeights();

    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j is for forward compatibility for skipping terminal
      // symbols.
      for (int j = i + 1; j < i + 2; j++) {
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
                CcgSearchMove searchMove = (CcgSearchMove) searchMoveType.getValue(
                    (int) ((curKeyNum - keyNumPrefix) % dimensionOffsets[1]));

                // Apply the binary rule.
                Combinator resultCombinator = searchMove.getBinaryCombinator();
                // double ruleProb =
                // syntaxDistributionTensor.getByIndex(index);
                // log.startTimer("ccg_parse/beam_loop/binary");
                double ruleProb = binaryRuleTensor.get(searchMove.getBinaryCombinatorKeyNum());
                // log.stopTimer("ccg_parse/beam_loop/binary");

                int resultSyntax = resultCombinator.getSyntax();
                int[] resultSyntaxUniqueVars = resultCombinator.getSyntaxUniqueVars();

                for (Integer leftIndex : leftTypes.get(leftType)) {
                  ChartEntry leftRoot = leftTrees[leftIndex];
                  double leftProb = leftProbs[leftIndex];

                  // log.startTimer("ccg_parse/beam_loop/unary");
                  long leftUnaryKeyNum = searchMove.getLeftUnaryKeyNum();
                  if (leftUnaryKeyNum != -1) {
                    leftProb *= unaryRuleTensor.get(leftUnaryKeyNum);
                  }
                  // log.stopTimer("ccg_parse/beam_loop/unary");

                  for (Integer rightIndex : rightTypes.get(rightType)) {
                    ChartEntry rightRoot = rightTrees[rightIndex];
                    double rightProb = rightProbs[rightIndex];

                    // log.startTimer("ccg_parse/beam_loop/unary");
                    long rightUnaryKeyNum = searchMove.getRightUnaryKeyNum();
                    if (rightUnaryKeyNum != -1) {
                      rightProb *= unaryRuleTensor.get(searchMove.getRightUnaryKeyNum());
                    }
                    // log.stopTimer("ccg_parse/beam_loop/unary");

                    // log.startTimer("ccg_parse/beam_loop/relabel");
                    // Relabel assignments from the left and right chart
                    // entries.
                    int numAssignments = relabelAssignment(leftRoot, searchMove.getLeftRelabeling(),
                        assignmentVariableAccumulator, assignmentPredicateAccumulator,
                        assignmentIndexAccumulator, 0);
                    numAssignments = relabelAssignment(rightRoot, searchMove.getRightRelabeling(),
                        assignmentVariableAccumulator, assignmentPredicateAccumulator,
                        assignmentIndexAccumulator, numAssignments);

                    // Relabel and fill dependencies from the left and
                    // right chart entries.
                    long[] leftUnfilledDependenciesRelabeled = leftRoot.getUnfilledDependenciesRelabeled(
                        searchMove.getLeftRelabeling());
                    long[] rightUnfilledDependenciesRelabeled = rightRoot.getUnfilledDependenciesRelabeled(
                        searchMove.getRightRelabeling());

                    int numDeps = 0;
                    numDeps = accumulateDependencies(leftUnfilledDependenciesRelabeled,
                        resultCombinator.getUnifiedVariables(), assignmentVariableAccumulator,
                        assignmentPredicateAccumulator, assignmentIndexAccumulator, numAssignments, depAccumulator,
                        resultCombinator.getResultOriginalVars(), resultCombinator.getResultVariableRelabeling(),
                        resultSyntaxUniqueVars, numDeps);
                    if (numDeps == -1) {
                      continue;
                    }
                    numDeps = accumulateDependencies(rightUnfilledDependenciesRelabeled,
                        resultCombinator.getUnifiedVariables(), assignmentVariableAccumulator,
                        assignmentPredicateAccumulator, assignmentIndexAccumulator, numAssignments, depAccumulator,
                        resultCombinator.getResultOriginalVars(), resultCombinator.getResultVariableRelabeling(),
                        resultSyntaxUniqueVars, numDeps);
                    if (numDeps == -1) {
                      continue;
                    }
                    // Fill any dependencies from the combinator itself.
                    numDeps = accumulateDependencies(resultCombinator.getUnfilledDependencies(this, spanEnd),
                        resultCombinator.getUnifiedVariables(), assignmentVariableAccumulator,
                        assignmentPredicateAccumulator, assignmentIndexAccumulator, numAssignments, depAccumulator,
                        resultCombinator.getResultOriginalVars(), resultCombinator.getResultVariableRelabeling(),
                        resultSyntaxUniqueVars, numDeps);
                    if (numDeps == -1) {
                      continue;
                    }

                    long[] filledDepArray = separateDependencies(depAccumulator, numDeps, true);
                    long[] unfilledDepArray = separateDependencies(depAccumulator, numDeps, false);

                    // log.stopTimer("ccg_parse/beam_loop/relabel");

                    numAssignments = filterAssignmentVariables(assignmentVariableAccumulator, assignmentPredicateAccumulator,
                        assignmentIndexAccumulator, resultCombinator.getResultOriginalVars(),
                        resultCombinator.getResultVariableRelabeling(), numAssignments);
                    int[] newAssignmentVariableNums = ArrayUtils.copyOfRange(assignmentVariableAccumulator, 0, numAssignments);
                    int[] newAssignmentPredicateNums = ArrayUtils.copyOfRange(assignmentPredicateAccumulator, 0, numAssignments);
                    int[] newAssignmentIndexes = ArrayUtils.copyOfRange(assignmentIndexAccumulator, 0, numAssignments);

                    ChartEntry result = new ChartEntry(resultSyntax, resultSyntaxUniqueVars,
                        null, searchMove.getLeftUnary(), searchMove.getRightUnary(), newAssignmentVariableNums,
                        newAssignmentPredicateNums, newAssignmentIndexes, unfilledDepArray, filledDepArray,
                        spanStart, spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex, resultCombinator);
                    // log.startTimer("ccg_parse/beam_loop/dependencies");
                    addChartEntryWithDependencies(result, chart, ruleProb * leftProb * rightProb,
                        spanStart, spanEnd, log);
                    // log.stopTimer("ccg_parse/beam_loop/dependencies");
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
      int spanStart, int spanEnd, LogFunction log) {
    // Get the probabilities of the generated dependencies.
    double depProb = 1.0;
    Tensor currentParseTensor = chart.getDependencyTensor();
    Tensor currentWordTensor = chart.getWordDistanceTensor();
    Tensor currentPuncTensor = chart.getPuncDistanceTensor();
    Tensor currentVerbTensor = chart.getVerbDistanceTensor();

    int[] wordDistances = chart.getWordDistances();
    int[] puncDistances = chart.getPunctuationDistances();
    int[] verbDistances = chart.getVerbDistances();
    int numTerminals = chart.size();

    long[] resultDependencies = result.getDependencies();
    for (int i = 0; i < resultDependencies.length; i++) {
      long depLong = resultDependencies[i];
      Preconditions.checkState(isFilledDependency(depLong));
      // Compute the keyNum containing the weight for depLong
      // in dependencyTensor.
      int headNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM;
      int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM;
      int argNumNum = dependencyArgNumType.getValueIndex(
          (int) ((depLong >> ARG_NUM_OFFSET) & ARG_NUM_MASK));

      long depNum = (headNum * dependencyHeadOffset) + (argNumNum * dependencyArgNumOffset)
          + objectNum * dependencyObjectOffset;

      /*
      int[] depKeyNum = new int[] {headNum, argNumNum, objectNum};
      System.out.println(Arrays.toString(depKeyNum));
      System.out.println(dependencyHeadOffset + " " + dependencyArgNumOffset + " " + dependencyObjectOffset);
      System.out.println(depNum);
      System.out.println(dependencyDistribution.getVars().intArrayToAssignment(depKeyNum));
      */
      
      // Get the probability of this predicate-argument combination.
      // log.startTimer("chart_entry/dependency_prob");
      depProb *= currentParseTensor.get(depNum);
      // log.stopTimer("chart_entry/dependency_prob");

      // Compute distance features.
      int subjectWordIndex = (int) ((depLong >> SUBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);
      int objectWordIndex = (int) ((depLong >> OBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);

      // log.startTimer("chart_entry/compute_distance");
      int distanceIndex = (subjectWordIndex * numTerminals) +  objectWordIndex;
      int wordDistance = wordDistances[distanceIndex];
      int puncDistance = puncDistances[distanceIndex];
      int verbDistance = verbDistances[distanceIndex];
      // log.stopTimer("chart_entry/compute_distance");

      // log.startTimer("chart_entry/lookup_distance");
      long distanceKeyNumBase = (headNum * distanceHeadOffset) + (argNumNum * distanceArgNumOffset);
      long wordDistanceKeyNum = distanceKeyNumBase + (wordDistance * distanceDistanceOffset);
      depProb *= currentWordTensor.get(wordDistanceKeyNum);
      long puncDistanceKeyNum = distanceKeyNumBase + (puncDistance * distanceDistanceOffset);
      depProb *= currentPuncTensor.get(puncDistanceKeyNum);
      long verbDistanceKeyNum = distanceKeyNumBase + (verbDistance * distanceDistanceOffset);
      depProb *= currentVerbTensor.get(verbDistanceKeyNum);
      // log.stopTimer("chart_entry/lookup_distance");
      // System.out.println(longToUnfilledDependency(depLong) + " " + depProb);
    }

    double totalProb = leftRightProb * depProb;
    chart.addChartEntryForSpan(result, totalProb, spanStart, spanEnd, syntaxVarType);
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

      int[] relabeledVars = result.getAssignmentVariableNumsRelabeled(unaryRuleCombinator.getVariableRelabeling());
      long[] relabeledUnfilledDeps = result.getUnfilledDependenciesRelabeled(unaryRuleCombinator.getVariableRelabeling());

      ChartEntry unaryRuleResult = result.applyUnaryRule(unaryRuleCombinator.getSyntax(),
          unaryRuleCombinator.getSyntaxUniqueVars(), unaryRuleCombinator,
          relabeledVars, result.getAssignmentPredicateNums(), result.getAssignmentIndexes(),
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

  private int relabelAssignment(ChartEntry entry, int[] relabeling,
      int[] variableAccumulator, int[] predicateAccumulator, int[] indexAccumulator,
      int startIndex) {
    int[] assignmentVariableNums = entry.getAssignmentVariableNums();
    int[] assignmentPredicateNums = entry.getAssignmentPredicateNums();
    int[] assignmentIndexes = entry.getAssignmentIndexes();

    for (int i = 0; i < assignmentVariableNums.length; i++) {
      variableAccumulator[i + startIndex] = relabeling[assignmentVariableNums[i]];
      predicateAccumulator[i + startIndex] = assignmentPredicateNums[i];
      indexAccumulator[i + startIndex] = assignmentIndexes[i];
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
      int assignmentLength, long[] depAccumulator, int[] returnOriginalVars, int[] returnVarsRelabeling,
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
        for (int i = 0; i < assignmentLength; i++) {
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

  public long unfilledDependencyToLong(UnfilledDependency dep) {
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
    System.out.println(predicate + " " + dependencyHeadType.canTakeValue(predicate));
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