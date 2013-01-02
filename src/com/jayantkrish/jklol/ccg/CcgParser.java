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
import com.google.common.collect.Iterables;
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
import com.jayantkrish.jklol.models.Variable;
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
  
  public static final String UNARY_RULE_INPUT_VAR_NAME = "unaryRuleInputVar";
  public static final String UNARY_RULE_VAR_NAME = "unaryRuleVar";

  // Member variables ////////////////////////////////////

  // Weights and word -> ccg category mappings for the lexicon
  // (terminals).
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;

  // Weights on dependency structures.
  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
  private final DiscreteFactor dependencyDistribution;
  private final DiscreteVariable dependencyHeadType; 
  private final DiscreteVariable dependencyArgNumType;
  private final Tensor dependencyTensor;

  // Weights on syntactic structures.
  private final VariableNumMap leftSyntaxVar;
  private final VariableNumMap rightSyntaxVar;
  private final VariableNumMap parentSyntaxVar;
  private final DiscreteVariable syntaxVarType;
  private final DiscreteVariable combinatorVarType;
  private final DiscreteFactor syntaxDistribution;
  private final Tensor syntaxTensor;

  // Unary type changing/raising rules.
  private final VariableNumMap unaryRuleInputVar;
  private final VariableNumMap unaryRuleVar;
  private final int unaryRuleVarNum;
  private final DiscreteFactor unaryRuleFactor;
  private final DiscreteVariable unaryRuleVarType;
  private final Tensor unaryRuleTensor;

  // Weights on the syntactic category of the root of the CCG parse.
  private final VariableNumMap rootSyntaxVar;
  private final DiscreteFactor rootSyntaxDistribution;

  // All predicates used in CCG rules.
  private final Set<Long> predicatesInRules;

  // Mapping from unheaded syntactic categories to headed syntactic categories.
  private final Multimap<SyntacticCategory, HeadedSyntacticCategory> syntacticCategoryMap;

  public CcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap dependencyHeadVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      DiscreteFactor dependencyDistribution, VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      DiscreteFactor syntaxDistribution, VariableNumMap unaryRuleInputVar, 
      VariableNumMap unaryRuleVar, DiscreteFactor unaryRuleFactor, VariableNumMap rootSyntaxVar,
      DiscreteFactor rootSyntaxDistribution) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);

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

    this.leftSyntaxVar = Preconditions.checkNotNull(leftSyntaxVar);
    this.rightSyntaxVar = Preconditions.checkNotNull(rightSyntaxVar);
    this.parentSyntaxVar = Preconditions.checkNotNull(parentSyntaxVar);
    Preconditions.checkArgument(syntaxDistribution.getVars().equals(
        VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, parentSyntaxVar)));
    this.syntaxVarType = leftSyntaxVar.getDiscreteVariables().get(0);
    this.combinatorVarType = parentSyntaxVar.getDiscreteVariables().get(0);
    this.syntaxDistribution = syntaxDistribution;
    this.syntaxTensor = syntaxDistribution.getWeights();
    
    this.unaryRuleInputVar = Preconditions.checkNotNull(unaryRuleInputVar);
    this.unaryRuleVar = Preconditions.checkNotNull(unaryRuleVar);
    this.unaryRuleVarNum = unaryRuleVar.getOnlyVariableNum();
    this.unaryRuleFactor = Preconditions.checkNotNull(unaryRuleFactor);
    this.unaryRuleVarType = unaryRuleVar.getDiscreteVariables().get(0);
    this.unaryRuleTensor = unaryRuleFactor.getWeights();
    
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
      for (String predicate : ((CcgUnaryRule) rule).getSubjects()) {
        predicatesInRules.add((long) dependencyHeadType.getValueIndex(predicate));
      }
    }
    
    this.syntacticCategoryMap = HashMultimap.create();
    for (Object syntacticCategory : leftSyntaxVar.getDiscreteVariables().get(0).getValues()) {
      HeadedSyntacticCategory headedCat = (HeadedSyntacticCategory) syntacticCategory;
      syntacticCategoryMap.put(headedCat.getSyntax(), headedCat);
    }
  }

  public static DiscreteFactor buildSyntacticDistribution(
      Iterable<HeadedSyntacticCategory> syntacticCategories, Iterable<CcgBinaryRule> rules, 
      boolean allowComposition) {
    Set<String> featureValues = Sets.newHashSet();
    for (HeadedSyntacticCategory cat : syntacticCategories) {
      getAllFeatureValues(cat.getSyntax(), featureValues);
    }
    
    // Compute the closure of syntactic categories, assuming the only
    // operations are function application and feature assignment.
    Set<HeadedSyntacticCategory> allCategories = Sets.newHashSet();
    for (HeadedSyntacticCategory cat : syntacticCategories) {
      Preconditions.checkArgument(cat.isCanonicalForm());
      allCategories.addAll(cat.getSubcategories(featureValues));

      while (!cat.getSyntax().isAtomic()) {
        allCategories.addAll(cat.getArgumentType().getCanonicalForm().getSubcategories(featureValues));
        allCategories.addAll(cat.getReturnType().getCanonicalForm().getSubcategories(featureValues));
        cat = cat.getReturnType();
      }
    }
    DiscreteVariable syntaxType = new DiscreteVariable("syntacticCategory", allCategories);
    for (int i = 0; i < syntaxType.numValues(); i++) {
      System.out.println(i + " " + syntaxType.getValue(i));
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
    }
    
    if (allowComposition) {
      // Compute function composition rules.
      for (HeadedSyntacticCategory functionCat : allCategories) {
        for (HeadedSyntacticCategory argumentCat : allCategories) {
          if (!functionCat.isAtomic() && !argumentCat.isAtomic() 
              && functionCat.getArgumentType().isUnifiableWith(argumentCat.getReturnType())) {
            for (int i = 0; i < 2; i++) {
              boolean argumentAsHead = i % 2 == 0;
              Direction direction = functionCat.getSyntax().getDirection();
              Combinator combinator;
              List<Object> outcome;
              if (direction.equals(Direction.LEFT)) {
                combinator = getCompositionCombinator(functionCat, argumentCat, true,
                    argumentAsHead, syntaxType);
                outcome = Arrays.<Object> asList(argumentCat, functionCat, combinator);
              } else if (direction.equals(Direction.RIGHT)) {
                combinator = getCompositionCombinator(functionCat, argumentCat, false,
                    argumentAsHead, syntaxType);
                outcome = Arrays.<Object>asList(functionCat, argumentCat, combinator);
              } else {
                // Forward compatible error message, for handling
                // Direction.BOTH if added.
                throw new IllegalArgumentException("Unknown direction type: " + direction);
              }
              
              if (combinator != null) {
                // It is possible for function composition to return syntactic categories
                // which are not members of the parser's set of valid syntactic categories.
                // Such composition rules are discarded.
                validOutcomes.add(outcome);
                combinators.add(combinator);
              }
            }
          }
        }
      }
    }
    
    // Find which syntactic categories are unifiable with each other. This map is used to
    // determine which categories binary and unary rules may be applied to.
    SetMultimap<HeadedSyntacticCategory, HeadedSyntacticCategory> unifiabilityMap = buildUnifiabilityMap(allCategories);
    
    // Create entries for CCG binary rules.
    for (CcgBinaryRule rule : rules) {
      HeadedSyntacticCategory leftCanon = rule.getLeftSyntacticType().getCanonicalForm();
      HeadedSyntacticCategory rightCanon = rule.getRightSyntacticType().getCanonicalForm();

      for (HeadedSyntacticCategory left : unifiabilityMap.get(leftCanon)) {
        for (HeadedSyntacticCategory right : unifiabilityMap.get(rightCanon)) {
          Combinator combinator = getBinaryRuleCombinator(left, right, rule, syntaxType);
          if (combinator != null) {
            List<Object> outcome = Arrays.<Object>asList(left, right, combinator);
            validOutcomes.add(outcome);
            combinators.add(combinator);
          }
        }
      }
    }

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
    System.out.println(syntaxDistribution.getParameterDescription());
    return syntaxDistribution;
  }
  
  private static void getAllFeatureValues(SyntacticCategory category, Set<String> values) {
     values.add(category.getRootFeature());
     if (!category.isAtomic()) {
       getAllFeatureValues(category.getArgument(), values);
       getAllFeatureValues(category.getReturn(), values);
     }
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
          new String[0], new int[0], new int[0]);
    } else {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionRelabeling,
          argumentRelabeling, resultRelabeling, resultRelabeling, unifiedVariables, new String[0],
          new int[0], new int[0]);
    }
  }
  
  private static Combinator getCompositionCombinator(HeadedSyntacticCategory functionCat,
      HeadedSyntacticCategory argumentCat, boolean argumentOnLeft, boolean argumentAsHead,
      DiscreteVariable syntaxVarType) {
    Map<Integer, String> assignedFeatures = Maps.newHashMap();
    Map<Integer, String> otherAssignedFeatures = Maps.newHashMap();
    Map<Integer, Integer> relabeledFeatures = Maps.newHashMap();
    Preconditions.checkArgument(functionCat.getArgumentType().isUnifiableWith(argumentCat.getReturnType(),
        assignedFeatures, otherAssignedFeatures, relabeledFeatures));
    // Determine which syntactic category results from composing the
    // two input categories.
    int[] argumentVars = argumentCat.getUniqueVariables();
    int[] argumentRelabeling = argumentCat.getReturnType().unifyVariables(argumentVars, 
        functionCat.getArgumentType(), functionCat.getUniqueVariables());

    HeadedSyntacticCategory relabeledArgumentType = argumentCat.relabelVariables(argumentVars, argumentRelabeling)
        .assignFeatures(otherAssignedFeatures, Collections.<Integer, Integer>emptyMap());
    int headVariable = argumentAsHead ? relabeledArgumentType.getRootVariable() : functionCat.getRootVariable();
    HeadedSyntacticCategory resultType = functionCat.assignFeatures(assignedFeatures, relabeledFeatures)
        .getReturnType().addArgument(relabeledArgumentType.getArgumentType(), argumentCat.getDirection(), 
            headVariable);

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
      // It is possible for function composition to return syntactic categories
      // which are not members of the parser's set of valid syntactic categories.
      // Such composition rules are discarded.
      System.out.println("Discarding composition rule: " + functionCat + " " + argumentCat
          + " -> " + canonicalResultType);
      return null;
    }

    int functionReturnTypeInt = syntaxVarType.getValueIndex(canonicalResultType);
    int[] functionReturnTypeVars = canonicalResultType.getUniqueVariables();
    if (argumentOnLeft) {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, argumentCatRelabeling,
          functionCatRelabeling, resultUniqueVars, resultCatRelabeling, unifiedVariables,
          new String[0], new int[0], new int[0]);
    } else {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionCatRelabeling,
          argumentCatRelabeling, resultUniqueVars, resultCatRelabeling, unifiedVariables, 
          new String[0], new int[0], new int[0]);
    }
  }
  
  private static Combinator getBinaryRuleCombinator(HeadedSyntacticCategory leftCanonical, 
      HeadedSyntacticCategory rightCanonical, CcgBinaryRule rule, DiscreteVariable syntaxVarType) {
    // Binary rules work by relabeling both the left and right categories 
    // into a single, non-canonical set of variables.
    HeadedSyntacticCategory left = rule.getLeftSyntacticType();
    HeadedSyntacticCategory right = rule.getRightSyntacticType();
    
    // Identify assignments to the feature variables of left and canonical,
    // then combine them into a single assignment which applies to the return
    // type.
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
        // Failure: the two input categories must produce the same assignment to all 
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
        combinedAssignedFeatures, Collections.<Integer, Integer>emptyMap())
        .getCanonicalForm(parentRelabeling);
    int[] parentOriginalVars = rule.getParentSyntacticType().getUniqueVariables();
    int[] parentRelabelingArray = relabelingMapToArray(parentRelabeling, parentOriginalVars);

    int[] unifiedVariables = Ints.concat(leftRelabelingArray, rightRelabelingArray);
    
    int parentInt = syntaxVarType.getValueIndex(parent);
    int[] parentVars = parent.getUniqueVariables();
    return new Combinator(parentInt, parentVars, leftRelabelingArray, rightRelabelingArray,
        parentOriginalVars, parentRelabelingArray, unifiedVariables, rule.getSubjects(),
        rule.getArgumentNumbers(), rule.getObjects());
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

    @SuppressWarnings("unchecked")
    SetMultimap<HeadedSyntacticCategory, HeadedSyntacticCategory> unifiabilityMap = 
        buildUnifiabilityMap(((Iterable<HeadedSyntacticCategory>)(Iterable<?>) syntaxVariableType.getValues()));
    VariableNumMap unaryRuleInputVar = VariableNumMap.singleton(1,  UNARY_RULE_INPUT_VAR_NAME,
        syntaxVariableType);

    List<CcgUnaryRule> validRules = Lists.newArrayList(unaryRules);
    DiscreteVariable unaryRuleVarType = new DiscreteVariable("unaryRuleType", validRules);
    VariableNumMap unaryRuleVar = VariableNumMap.singleton(2, UNARY_RULE_VAR_NAME, 
        unaryRuleVarType);

    VariableNumMap unaryRuleVars = unaryRuleInputVar.union(unaryRuleVar);
    TableFactorBuilder unaryRuleBuilder = new TableFactorBuilder(unaryRuleVars,
        SparseTensorBuilder.getFactory());
    for (CcgUnaryRule rule : unaryRules) {
      for (HeadedSyntacticCategory cat : unifiabilityMap.get(
          rule.getInputSyntacticCategory().getCanonicalForm())) {
        unaryRuleBuilder.setWeightList(Arrays.asList(cat, rule), 1.0);
      }
    }
    return unaryRuleBuilder.build();
  }

  /*
  public static DiscreteFactor buildAllowableCombinations(DiscreteFactor binaryRuleFactor,
      DiscreteFactor unaryRuleFactor, DiscreteVariable syntaxVariableType) {
    Set<List<Object>> allowableCombinations = Sets.newHashSet();
    for (Object leftObject : syntaxVariableType.getValues()) {
      HeadedSyntacticCategory leftSyntax = (HeadedSyntacticCategory) leftObject;
      for (Object rightObject : syntaxVariableType.getValues()) {
        HeadedSyntacticCategory rightSyntax = (HeadedSyntacticCategory) rightObject;
        
        Iterator<Outcome> leftUnaryRuleIter = unaryRuleFactor.outcomePrefixIterator(
            unaryRuleInputVar.outcomeArrayToAssignment(leftSyntax));
        while (leftUnaryRuleIter.hasNext()) {
          Outcome leftOutcome = leftUnaryRuleIter.next();
          HeadedSyntacticCategory leftRaisedType = leftUnaryRule.apply(leftSyntax);
          Iterator<Outcome> rightUnaryRuleIter = unaryRuleFactor.outcomePrefixIterator(
            unaryRuleInputVar.outcomeArrayToAssignment(rightSyntax));
          while (rightUnaryRuleIter.hasNext()) {
            Outcome rightOutcome = rightUnaryRuleIter.next();
            HeadedSyntacticCategory rightRaisedType = rightUnaryRule.apply(rightSyntax);
            
            Assignment assignment = syntaxVars.outcomeArrayToAssignment(leftRaisedType, rightRaisedType);
            
          }
        }
      }
    }
  }
  */
  
  public boolean isPossibleDependencyStructure(DependencyStructure dependency) {
    Assignment assignment = Assignment.unionAll(
        dependencyHeadVar.outcomeArrayToAssignment(dependency.getHead()),
        dependencyArgNumVar.outcomeArrayToAssignment(dependency.getArgIndex()),
        dependencyArgVar.outcomeArrayToAssignment(dependency.getObject()));

    return dependencyDistribution.getVars().isValidAssignment(assignment) &&
        dependencyDistribution.getUnnormalizedLogProbability(assignment) != Double.NEGATIVE_INFINITY;
  }
  
  public boolean isPossibleLexiconEntry(List<String> words, SyntacticCategory category) {
    if (terminalVar.isValidOutcomeArray(words)) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(words);

      Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory lexicalEntry = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());
        if (lexicalEntry.getSyntax().getSyntax().equals(category)) {
          return true;
        }
      }
    }
    System.out.println("No such lexicon entry: " + words + " -> " + category);
    return false;
  }
  
  public boolean isPossibleBinaryRule(SyntacticCategory left, SyntacticCategory right,
      SyntacticCategory parent) {
    for (HeadedSyntacticCategory leftHeaded : syntacticCategoryMap.get(left)) {
      for (HeadedSyntacticCategory rightHeaded : syntacticCategoryMap.get(right)) {
        Assignment assignment = leftSyntaxVar.outcomeArrayToAssignment(leftHeaded)
            .union(rightSyntaxVar.outcomeArrayToAssignment(rightHeaded));
        
        Iterator<Outcome> outcomeIter = syntaxDistribution.outcomePrefixIterator(assignment);
        while (outcomeIter.hasNext()) {
          Outcome outcome = outcomeIter.next();
          Combinator resultCombinator = (Combinator) outcome.getAssignment().getValue(
              parentSyntaxVar.getOnlyVariableNum());
          HeadedSyntacticCategory parentCategory = (HeadedSyntacticCategory)
              syntaxVarType.getValue(resultCombinator.getSyntax());
          
          if (parentCategory.getSyntax().equals(parent)) {
            return true;
          }
        }
      }
    }
    
    System.out.println("No such binary rule: " + left + " " + right + " -> " + parent);
    return false;
  }
  
  public boolean isPossibleSyntacticTree(CcgSyntaxTree tree) {
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
          CcgUnaryRule rule = (CcgUnaryRule) outcome.getAssignment().getValue(unaryRuleVarNum);

          if (rule.getResultSyntacticCategory().getSyntax().equals(result)) {
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
      return isPossibleLexiconEntry(tree.getWords(), tree.getPreUnaryRuleSyntax());
    } else {
      return isPossibleBinaryRule(tree.getLeft().getRootSyntax(), tree.getRight().getRootSyntax(),
          tree.getPreUnaryRuleSyntax()) && isPossibleSyntacticTree(tree.getLeft()) 
          && isPossibleSyntacticTree(tree.getRight()); 
    }
  }
  
  public boolean isPossibleExample(CcgExample example) {
    if (example.hasDependencies()) {
      for (DependencyStructure dependency : example.getDependencies()) {
        if (!isPossibleDependencyStructure(dependency)) {
          System.out.println("Invalid dependency: " + dependency);
          return false;
        }
      }
    }
    
    if (example.hasSyntacticParse()) {
      if (!isPossibleSyntacticTree(example.getSyntacticParse())) {
        return false;
      }
    }
    return true;
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

  public List<CcgParse> beamSearch(List<String> terminals, int beamSize) {
    return beamSearch(terminals, beamSize, new NullLogFunction());
  }

  public List<CcgParse> beamSearch(int beamSize, String... terminals) {
    return beamSearch(Arrays.asList(terminals), beamSize);
  }

  public List<CcgParse> beamSearch(List<String> terminals, int beamSize, ChartFilter beamFilter,
      LogFunction log) {
    CcgChart chart = new CcgChart(terminals, beamSize, beamFilter);

    log.startTimer("ccg_parse/initialize_chart");
    initializeChart(terminals, chart, log);
    log.stopTimer("ccg_parse/initialize_chart");

    log.startTimer("ccg_parse/calculate_inside_beam");
    calculateInsideBeam(chart, log);
    log.stopTimer("ccg_parse/calculate_inside_beam");

    reweightRootEntries(chart);
    
    return decodeParsesForRoot(chart);
  }
  
  /**
   * Updates entries in the beam for the root node with a factor for
   * the root syntactic category.
   * 
   * @param chart
   */
  private void reweightRootEntries(CcgChart chart) {
    int spanStart = 0;
    int spanEnd = chart.size() - 1;
    int numChartEntries = chart.getNumChartEntriesForSpan(spanStart, spanEnd); 
    ChartEntry[] entries = copyChartEntryArray(chart.getChartEntriesForSpan(spanStart, spanEnd),
        numChartEntries);
    double[] probs = ArrayUtils.copyOf(chart.getChartEntryProbsForSpan(spanStart, spanEnd),
        numChartEntries);
    
    chart.clearChartEntriesForSpan(spanStart, spanEnd);
    
    Tensor rootSyntaxTensor = rootSyntaxDistribution.getWeights();
    for (int i = 0 ; i < entries.length; i++) {
      ChartEntry entry = entries[i];
      double rootProb = rootSyntaxTensor.get(entry.getHeadedSyntax());
      chart.addChartEntryForSpan(entry, probs[i] * rootProb, spanStart, spanEnd, syntaxVarType);
    }
  }
  
  private ChartEntry[] copyChartEntryArray(ChartEntry[] entries, int numEntries) {
    ChartEntry[] returnValue = new ChartEntry[numEntries];
    for (int i = 0; i < numEntries; i++) {
      returnValue[i] = entries[i];
    }
    return returnValue;
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
        // System.out.println(spanStart + "." + spanEnd + " : " + chart.getNumChartEntriesForSpan(spanStart, spanEnd));
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
  public void initializeChart(List<String> terminals, CcgChart chart, LogFunction log) {
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
            addChartEntryWithUnaryRules(entry, chart, bestOutcome.getProbability(), i, j, log);

            // Identify possible predicates.
            for (int assignmentPredicateNum : entry.getAssignmentPredicateNums()) {
              possiblePredicates.add((long) assignmentPredicateNum);
            }
          }
        }
      }
    }
    
    /*
    for (int i = 0; i < terminals.size(); i++) {
      System.out.println(i + "." + i + " : " + chart.getNumChartEntriesForSpan(i, i));
    }
    */

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

    int syntaxAsInt = syntaxVarType.getValueIndex(result.getSyntax());
    return new ChartEntry(syntaxAsInt, result.getSyntax().getUniqueVariables(), result,
        null, Ints.toArray(variableNums), Ints.toArray(predicateNums), Ints.toArray(indexes),
        unfilledDepArray, depArray, spanStart, spanEnd);
  }

  private void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart, LogFunction log) {
    long[] depAccumulator = new long[20];
    int[] assignmentVariableAccumulator = new int[50];
    int[] assignmentPredicateAccumulator = new int[50];
    int[] assignmentIndexAccumulator = new int[50];
    
    Tensor syntaxDistributionTensor = syntaxDistribution.getWeights();

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
          long[] dimensionOffsets = syntaxDistributionTensor.getDimensionOffsets();
          int tensorSize = syntaxDistributionTensor.size();
          if (index == -1 || index >= tensorSize) {
            continue;
          }
          long maxKeyNum = keyNumPrefix + dimensionOffsets[0];
          long curKeyNum = syntaxDistributionTensor.indexToKeyNum(index);
          
          while (curKeyNum < maxKeyNum && index < tensorSize) {
            int rightType = (int) (((curKeyNum - keyNumPrefix) / dimensionOffsets[1]) % dimensionOffsets[0]);
            if (rightTypes.containsKey(rightType)) {
              Combinator resultCombinator = (Combinator) combinatorVarType.getValue(
                  (int) ((curKeyNum - keyNumPrefix) % dimensionOffsets[1]));
              double ruleProb = syntaxDistributionTensor.getByIndex(index);
              // System.out.println("  " + resultCombinator);

              int resultSyntax = resultCombinator.getSyntax();
              int[] resultSyntaxUniqueVars = resultCombinator.getSyntaxUniqueVars();

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

                  // Relabel and fill dependencies from the left and
                  // right chart entries.
                  long[] leftUnfilledDependenciesRelabeled = leftRoot.getUnfilledDependenciesRelabeled(
                      resultCombinator.getLeftVariableRelabeling());
                  long[] rightUnfilledDependenciesRelabeled = rightRoot.getUnfilledDependenciesRelabeled(
                      resultCombinator.getRightVariableRelabeling());

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

                  numAssignments = filterAssignmentVariables(assignmentVariableAccumulator, assignmentPredicateAccumulator,
                      assignmentIndexAccumulator, resultCombinator.getResultOriginalVars(), 
                      resultCombinator.getResultVariableRelabeling(), numAssignments);
                  int[] newAssignmentVariableNums = ArrayUtils.copyOfRange(assignmentVariableAccumulator, 0, numAssignments);
                  int[] newAssignmentPredicateNums = ArrayUtils.copyOfRange(assignmentPredicateAccumulator, 0, numAssignments);
                  int[] newAssignmentIndexes = ArrayUtils.copyOfRange(assignmentIndexAccumulator, 0, numAssignments);

                  ChartEntry result = new ChartEntry(resultSyntax, resultSyntaxUniqueVars, 
                      null, newAssignmentVariableNums, newAssignmentPredicateNums, newAssignmentIndexes,
                      unfilledDepArray, filledDepArray, spanStart, spanStart + i, leftIndex, 
                      spanStart + j, spanEnd, rightIndex, resultCombinator);
                  // log.startTimer("ccg_parse/add_chart_entry_with_unary_rules");
                  addChartEntryWithUnaryRules(result, chart, ruleProb * leftProb * rightProb, spanStart, spanEnd, log);
                  // log.stopTimer("ccg_parse/add_chart_entry_with_unary_rules");
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
      int spanStart, int spanEnd, LogFunction log) {
    // log.startTimer("ccg_parse/dep_probs");
    // Get the probabilities of the generated dependencies.
    double depProb = 1.0;
    Tensor currentParseTensor = chart.getDependencyTensor();
    for (long dep : result.getDependencies()) {
      long depNum = dependencyLongToTensorKeyNum(dep);
      depProb *= currentParseTensor.get(depNum);
    }

    double totalProb = leftRightProb * depProb;
    chart.addChartEntryForSpan(result, totalProb, spanStart, spanEnd, syntaxVarType);
    // log.stopTimer("ccg_parse/dep_probs");
    /*
     * System.out.println(spanStart + "." + spanEnd + " " +
     * result.getHeadedSyntax() + " " +
     * Arrays.toString(result.getDependencies()) + " " + totalProb);
     */
    // log.startTimer("ccg_parse/unary_rules");
    int headedSyntax = result.getHeadedSyntax();
    long keyNumPrefix = unaryRuleTensor.dimKeyPrefixToKeyNum(new int[] {headedSyntax});
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
      CcgUnaryRule unaryRule = (CcgUnaryRule) unaryRuleVarType.getValue(unaryRuleIndex);
      double ruleProb = unaryRuleTensor.getByIndex(index);
      ChartEntry unaryRuleResult = unaryRule.apply(result, syntaxVarType);
      if (unaryRuleResult != null) {
        chart.addChartEntryForSpan(unaryRuleResult, totalProb * ruleProb, spanStart, spanEnd,
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
    // log.stopTimer("ccg_parse/unary_rules");
  }

  private int relabelAssignment(ChartEntry entry, int[] relabeling, int[] variableAccumulator,
      int[] predicateAccumulator, int[] indexAccumulator, int startIndex) {

    int[] uniqueVars = entry.getHeadedSyntaxUniqueVars();
    int[] assignmentVariableNums = entry.getAssignmentVariableNums();
    int[] assignmentPredicateNums = entry.getAssignmentPredicateNums();
    int[] assignmentIndexes = entry.getAssignmentIndexes();

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