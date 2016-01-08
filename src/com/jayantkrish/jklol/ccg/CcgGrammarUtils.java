package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Static methods for compiling the grammar of a 
 * CCG parser.
 * 
 * @author jayantk
 *
 */
public class CcgGrammarUtils {
  
  /**
   * Gets the closure of a set of syntactic categories under function
   * application and feature assignment.
   * 
   * @param syntacticCategories - the categories to get the closure of.
   * Must be provided in canonical form.
   *
   * @return closure of the argument categories, in canonical form.
   */
  public static Set<HeadedSyntacticCategory> getSyntacticCategoryClosure(
      Collection<HeadedSyntacticCategory> syntacticCategories) {
    Set<String> featureValues = Sets.newHashSet();
    for (HeadedSyntacticCategory cat : syntacticCategories) {
      getAllFeatureValues(cat.getSyntax(), featureValues);
    }

    // Compute the closure of syntactic categories, assuming the only
    // operations are function application and feature assignment.
    Queue<HeadedSyntacticCategory> unprocessed = new LinkedList<HeadedSyntacticCategory>();
    unprocessed.addAll(syntacticCategories);
    
    Set<HeadedSyntacticCategory> allCategories = Sets.newHashSet();
    while (unprocessed.size() > 0) {
      HeadedSyntacticCategory cat = unprocessed.poll();
      Preconditions.checkArgument(cat.isCanonicalForm());
      
      allCategories.addAll(canonicalizeCategories(cat.getSubcategories(featureValues)));
      
      if (!cat.isAtomic()) {
        HeadedSyntacticCategory ret = cat.getReturnType().getCanonicalForm();
        if (!allCategories.contains(ret) && !unprocessed.contains(ret)) {
          unprocessed.offer(ret);
        }

        HeadedSyntacticCategory arg = cat.getArgumentType().getCanonicalForm();
        if (!allCategories.contains(arg) && !unprocessed.contains(arg)) {
          unprocessed.offer(arg);
        }
      }
    }

    // XXX: jayantk 1/8/2016 I think this loop does exactly the same thing as 
    // the previous one.
    for (HeadedSyntacticCategory cat : syntacticCategories) {
      Preconditions.checkArgument(cat.isCanonicalForm());
      allCategories.addAll(canonicalizeCategories(cat.getSubcategories(featureValues)));

      while (!cat.getSyntax().isAtomic()) {
        allCategories.addAll(canonicalizeCategories(cat.getArgumentType().getCanonicalForm().getSubcategories(featureValues)));
        allCategories.addAll(canonicalizeCategories(cat.getReturnType().getCanonicalForm().getSubcategories(featureValues)));
        cat = cat.getReturnType();
      }
    }
    return allCategories;
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
        Arrays.asList(CcgParser.LEFT_SYNTAX_VAR_NAME, CcgParser.RIGHT_SYNTAX_VAR_NAME, CcgParser.PARENT_SYNTAX_VAR_NAME),
        Arrays.asList(syntaxType, syntaxType, combinatorType));
    TableFactorBuilder syntaxDistributionBuilder = new TableFactorBuilder(syntaxVars,
        SparseTensorBuilder.getFactory());
    for (List<Object> outcome : validOutcomes) {
      syntaxDistributionBuilder.setWeight(syntaxVars.outcomeToAssignment(outcome), 1.0);
    }

    DiscreteFactor syntaxDistribution = syntaxDistributionBuilder.build();
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
      Preconditions.checkState(direction == Direction.RIGHT || direction == Direction.LEFT
          || direction == Direction.BOTH, "Unsupported direction %s", direction);

      Combinator combinator;
      List<Object> outcome;
      if (direction.equals(Direction.LEFT) || direction.equals(Direction.BOTH)) {
        combinator = getApplicationCombinator(functionCat, argumentCat, true, syntaxType);
        outcome = Arrays.<Object> asList(argumentCat, functionCat, combinator);

        combinators.add(combinator);
        validOutcomes.add(outcome);
      } 

      if (direction.equals(Direction.RIGHT) || direction.equals(Direction.BOTH)) {
        combinator = getApplicationCombinator(functionCat, argumentCat, false, syntaxType);
        outcome = Arrays.<Object> asList(functionCat, argumentCat, combinator);
        
        combinators.add(combinator);
        validOutcomes.add(outcome);
      }
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

        // Creating composition rules requires us to guess what the correct
        // head of the resulting syntactic category is. The rule implemented here
        // is to first try making the right word the semantic head, and if
        // that fails, to try the left word.
        if (functionCat.getArgumentType().isUnifiableWith(returnType)) {
          Direction direction = functionCat.getSyntax().getDirection();
          Preconditions.checkState(direction == Direction.RIGHT || direction == Direction.LEFT
              || direction == Direction.BOTH, "Unsupported direction %s", direction);

          Combinator combinator = null;
          List<Object> outcome = null;
          if (direction.equals(Direction.LEFT) || direction.equals(Direction.BOTH)) {
            combinator = getCompositionCombinator(functionCat, argumentCat, returnType, depth,
                true, false, syntaxType);
            if (combinator == null) {
              combinator = getCompositionCombinator(functionCat, argumentCat, returnType, depth,
                true, true, syntaxType);
            }
            outcome = Arrays.<Object> asList(argumentCat, functionCat, combinator);
          }

          if (combinator != null) {
            // It is possible for function composition to return syntactic categories
            // which are not members of the parser's set of valid syntactic categories.
            // Such composition rules are discarded.
            validOutcomes.add(outcome);
            combinators.add(combinator);
          }

          if (direction.equals(Direction.RIGHT) || direction.equals(Direction.BOTH)) {
            combinator = getCompositionCombinator(functionCat, argumentCat, returnType, depth,
                false, true, syntaxType);
            if (combinator == null) {
              combinator = getCompositionCombinator(functionCat, argumentCat, returnType, depth,
                  false, false, syntaxType);
            }
            outcome = Arrays.<Object> asList(functionCat, argumentCat, combinator);
          }
          
          if (combinator != null) {
            // See comment in the equivalent if statement above.
            validOutcomes.add(outcome);
            combinators.add(combinator);
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
    
    int[] argumentInverseRelabeling = invertRelabeling(argumentRelabeling,
        argumentCat.getUniqueVariables(), Ints.max(functionRelabeling));
    int[] functionInverseRelabeling = invertRelabeling(functionRelabeling,
        functionCat.getUniqueVariables(), Ints.max(functionRelabeling));
    int[] resultInverseRelabeling = functionReturnType.getUniqueVariables();

    // The return type may need to inherit some semantic features from
    // argument. Identify said features and update the return type.
    functionReturnType = functionReturnType.assignFeatures(assignedFeatures, relabeledFeatures);

    Preconditions.checkState(syntaxVarType.canTakeValue(functionReturnType),
        "Could not build application combinator for %s applied to %s producing %s",
        functionCat, argumentCat, functionReturnType);
    
    int functionReturnTypeInt = syntaxVarType.getValueIndex(functionReturnType);
    int[] functionReturnTypeVars = functionReturnType.getUniqueVariables();
    int functionReturnHead = functionReturnType.getHeadVariable();
    if (argumentOnLeft) {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionReturnHead, argumentRelabeling,
          argumentInverseRelabeling, functionRelabeling, functionInverseRelabeling, resultRelabeling,
          resultRelabeling, resultInverseRelabeling, unifiedVariables,
          new String[0], new HeadedSyntacticCategory[0], new int[0], new int[0], argumentOnLeft, 0, null, 
          Combinator.Type.BACKWARD_APPLICATION);
    } else {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionReturnHead, functionRelabeling,
          functionInverseRelabeling, argumentRelabeling, argumentInverseRelabeling, resultRelabeling, resultRelabeling,
          resultInverseRelabeling, unifiedVariables, new String[0],
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
    int maxVarNum = Ints.max(Ints.max(functionCatRelabeling), Ints.max(argumentCatRelabeling));
    int[] argumentCatInverseRelabeling = invertRelabeling(argumentCatRelabeling,
        argumentVars, maxVarNum);
    int[] functionCatInverseRelabeling = invertRelabeling(functionCatRelabeling,
        functionCat.getUniqueVariables(), maxVarNum);
    int[] resultCatInverseRelabeling = invertRelabeling(resultCatRelabeling,
        resultType.getUniqueVariables(), Ints.max(canonicalResultType.getUniqueVariables()));

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
          argumentCatInverseRelabeling, functionCatRelabeling, functionCatInverseRelabeling, resultUniqueVars,
          resultCatRelabeling, resultCatInverseRelabeling, unifiedVariables,
          new String[0], new HeadedSyntacticCategory[0], new int[0], new int[0], argumentOnLeft,
          argumentReturnDepth, null, combinatorType);
    } else {
      return new Combinator(functionReturnTypeInt, functionReturnTypeVars, functionHead, functionCatRelabeling,
          functionCatInverseRelabeling, argumentCatRelabeling, argumentCatInverseRelabeling, resultUniqueVars,
          resultCatRelabeling, resultCatInverseRelabeling, unifiedVariables,
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
        // assignment to all feature variables.
        return null;
      } else {
        combinedAssignedFeatures.put(varNum, (leftVal != null) ? leftVal : rightVal);
      }
    }

    int[] leftRelabelingArray = leftCanonical.unifyVariables(leftCanonical.getUniqueVariables(),
        left, new int[0]);
    int[] rightRelabelingArray = rightCanonical.unifyVariables(rightCanonical.getUniqueVariables(),
        right, new int[0]);
    int maxVarNum = Ints.max(Ints.max(leftRelabelingArray), Ints.max(rightRelabelingArray));
    int[] leftInverseRelabelingArray = invertRelabeling(leftRelabelingArray,
        leftCanonical.getUniqueVariables(), maxVarNum);
    int[] rightInverseRelabelingArray = invertRelabeling(rightRelabelingArray,
        rightCanonical.getUniqueVariables(), maxVarNum);

    Map<Integer, Integer> parentRelabeling = Maps.newHashMap();
    HeadedSyntacticCategory parent = rule.getParentSyntacticType().assignFeatures(
        combinedAssignedFeatures, Collections.<Integer, Integer> emptyMap())
        .getCanonicalForm(parentRelabeling);
    int[] parentOriginalVars = rule.getParentSyntacticType().getUniqueVariables();
    int[] parentRelabelingArray = relabelingMapToArray(parentRelabeling, parentOriginalVars);
    int[] inverseParentRelabelingArray = invertRelabeling(parentRelabelingArray,
        parentOriginalVars, Ints.max(parent.getUniqueVariables()));

    int[] unifiedVariables = Ints.concat(leftRelabelingArray, rightRelabelingArray);

    int parentInt = syntaxVarType.getValueIndex(parent);
    int[] parentVars = parent.getUniqueVariables();
    int parentHead = parent.getHeadVariable();
    return new Combinator(parentInt, parentVars, parentHead, leftRelabelingArray, leftInverseRelabelingArray, 
        rightRelabelingArray, rightInverseRelabelingArray, parentOriginalVars,
        parentRelabelingArray, inverseParentRelabelingArray, unifiedVariables, rule.getSubjects(),
        rule.getSubjectSyntacticCategories(), rule.getArgumentNumbers(), rule.getObjects(),
        false, -1, rule, rule.getCombinatorType());
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

        HeadedSyntacticCategory ruleCategory = rule.getInputSyntacticCategory();
        int[] patternToChart = cat.unifyVariables(cat.getUniqueVariables(),
            ruleCategory, new int[0]);
        int[] chartToPattern = invertRelabeling(patternToChart, cat.getUniqueVariables(), Ints.max(patternToChart));

        HeadedSyntacticCategory returnType = rule.getResultSyntacticCategory().assignFeatures(
            otherAssignedFeatures, Collections.<Integer, Integer> emptyMap());
        int resultAsInt = syntaxVariableType.getValueIndex(returnType);
        UnaryCombinator combinator = new UnaryCombinator(cat, resultAsInt,
            returnType.getUniqueVariables(), returnType.getHeadVariable(), patternToChart,
            chartToPattern, rule);

        validCombinators.add(combinator);
        validOutcomes.add(Lists.<Object> newArrayList(cat, combinator));
      }
    }

    DiscreteVariable unaryRuleVarType = new DiscreteVariable("unaryRuleType", validCombinators);
    VariableNumMap unaryRuleVar = VariableNumMap.singleton(2, CcgParser.UNARY_RULE_VAR_NAME,
        unaryRuleVarType);
    VariableNumMap unaryRuleInputVar = VariableNumMap.singleton(1, CcgParser.UNARY_RULE_INPUT_VAR_NAME,
        syntaxVariableType);

    VariableNumMap unaryRuleVars = unaryRuleInputVar.union(unaryRuleVar);
    TableFactorBuilder unaryRuleBuilder = new TableFactorBuilder(unaryRuleVars,
        SparseTensorBuilder.getFactory());
    for (List<Object> outcome : validOutcomes) {
      unaryRuleBuilder.setWeightList(outcome, 1.0);
    }
    DiscreteFactor unaryRuleDistribution = unaryRuleBuilder.build();
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
        Arrays.asList(CcgParser.LEFT_SYNTAX_VAR_NAME, CcgParser.RIGHT_SYNTAX_VAR_NAME,
            CcgParser.PARENT_MOVE_SYNTAX_VAR_NAME),
        Arrays.asList(syntaxVariableType, syntaxVariableType, searchMoveType));
    TableFactorBuilder syntaxDistributionBuilder = new TableFactorBuilder(syntaxVars,
        SparseTensorBuilder.getFactory());
    for (List<Object> outcome : validOutcomes) {
      syntaxDistributionBuilder.setWeight(syntaxVars.outcomeToAssignment(outcome), 1.0);
    }

    DiscreteFactor syntaxDistribution = syntaxDistributionBuilder.build();
    return syntaxDistribution;
  }

  private static CcgSearchMove getSearchMove(Combinator combinator, UnaryCombinator leftUnary,
      UnaryCombinator rightUnary, long combinatorKeyNum, long leftKeyNum, long rightKeyNum) {
    int[] leftRelabeling = combinator.getLeftVariableRelabeling();
    int[] leftInverseRelabeling = combinator.getLeftInverseRelabeling();
    int[] rightRelabeling = combinator.getRightVariableRelabeling();
    int[] rightInverseRelabeling = combinator.getRightInverseRelabeling();

    int[] newLeftRelabeling = leftRelabeling;
    int[] newLeftInverseRelabeling = leftInverseRelabeling;

    int[] newRightRelabeling = rightRelabeling;
    int[] newRightInverseRelabeling = rightInverseRelabeling;

    if (leftUnary != null) {
      newLeftRelabeling = composeRelabelings(leftUnary.getVariableRelabeling(), leftRelabeling);
      newLeftInverseRelabeling = composeRelabelings(leftInverseRelabeling, leftUnary.getInverseRelabeling());
    }

    if (rightUnary != null) {
      newRightRelabeling = composeRelabelings(rightUnary.getVariableRelabeling(), rightRelabeling);
      newRightInverseRelabeling = composeRelabelings(rightInverseRelabeling, rightUnary.getInverseRelabeling());
    }
    
    int[] newLeftToReturnInverseRelabeling = composeRelabelings(combinator.getResultInverseRelabeling(),
        newLeftInverseRelabeling);
    int[] newLeftDepRelabeling = composeRelabelings(newLeftRelabeling, newRightInverseRelabeling);
    int[] newRightToReturnInverseRelabeling = composeRelabelings(combinator.getResultInverseRelabeling(),
        newRightInverseRelabeling);
    int[] newRightDepRelabeling = composeRelabelings(newRightRelabeling, newLeftInverseRelabeling);

    return new CcgSearchMove(combinator, leftUnary, rightUnary, combinatorKeyNum, leftKeyNum, rightKeyNum,
        newLeftRelabeling, newLeftInverseRelabeling, newLeftToReturnInverseRelabeling, newLeftDepRelabeling,
        newRightRelabeling, newRightInverseRelabeling, newRightToReturnInverseRelabeling, newRightDepRelabeling);
  }
  
  private static int[] invertRelabeling(int[] relabeling, int[] uniqueVars, int maxVarNum) {
    int[] inverseRelabeling = new int[maxVarNum + 1];
    Arrays.fill(inverseRelabeling, -1);
    for (int i = 0; i < relabeling.length; i++) {
      if (relabeling[i] != -1) {
        inverseRelabeling[relabeling[i]] = uniqueVars[i];
      }
    }
    return inverseRelabeling;
  }
  
  private static int[] composeRelabelings(int[] first, int[] second) {
    int[] result = new int[first.length];
    for (int i = 0; i < first.length; i++) {
      if (first[i] != -1 && first[i] < second.length) {
        result[i] = second[first[i]];
      } else {
        // This variable isn't referenced in the syntactic category
        // returned by the unary rule. -1 is a special value which
        // causes the value of this variable to be dropped.
        result[i] = -1;
      }
    }
    return result;
  }
}
