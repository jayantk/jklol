package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lexicon.CcgLexicon;
import com.jayantkrish.jklol.ccg.lexicon.ParametricCcgLexicon;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Parameterized CCG grammar. This class instantiates CCG parsers
 * given parameter vectors.
 * 
 * @author jayant
 */
public class ParametricCcgParser implements ParametricFamily<CcgParser> {

  private static final long serialVersionUID = 1L;

  private final ParametricCcgLexicon lexiconFamily;

  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencySyntaxVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
  private final VariableNumMap dependencyHeadPosVar;
  private final VariableNumMap dependencyArgPosVar;
  private final ParametricFactor dependencyFamily;

  private final VariableNumMap wordDistanceVar;
  private final ParametricFactor wordDistanceFamily;
  private final VariableNumMap puncDistanceVar;
  private final ParametricFactor puncDistanceFamily;
  private final Set<String> puncTagSet;
  private final VariableNumMap verbDistanceVar;
  private final ParametricFactor verbDistanceFamily;
  private final Set<String> verbTagSet;

  private final VariableNumMap leftSyntaxVar;
  private final VariableNumMap rightSyntaxVar;
  private final VariableNumMap parentSyntaxVar;
  private final ParametricFactor syntaxFamily;

  private final VariableNumMap unaryRuleInputVar;
  private final VariableNumMap unaryRuleVar;
  private final ParametricFactor unaryRuleFamily;

  private final VariableNumMap headedBinaryRulePredicateVar;
  private final VariableNumMap headedBinaryRulePosVar;
  private final ParametricFactor headedBinaryRuleFamily;

  private final VariableNumMap searchMoveVar;
  private final DiscreteFactor compiledSyntaxDistribution;

  private final VariableNumMap rootSyntaxVar;
  private final VariableNumMap rootPredicateVar;
  private final VariableNumMap rootPosVar;
  private final ParametricFactor rootSyntaxFamily;
  private final ParametricFactor headedRootSyntaxFamily;

  private final boolean allowWordSkipping;
  private final boolean normalFormOnly;

  /**
   * Name of the parameters governing lexicon entries.
   */
  public static final String LEXICON_PARAMETERS = "lexicon";

  /**
   * Name of the parameter vector governing dependency structures.
   */
  public static final String DEPENDENCY_PARAMETERS = "dependencies";

  public static final String WORD_DISTANCE_PARAMETERS = "wordDistance";
  public static final String PUNC_DISTANCE_PARAMETERS = "puncDistance";
  public static final String VERB_DISTANCE_PARAMETERS = "verbDistance";

  /**
   * Name of the parameter vector governing combinations of syntactic
   * categories.
   */
  public static final String SYNTAX_PARAMETERS = "syntax";
  public static final String UNARY_RULE_PARAMETERS = "unaryRules";
  public static final String HEADED_SYNTAX_PARAMETERS = "headedBinaryRules";
  public static final String ROOT_SYNTAX_PARAMETERS = "rootSyntax";
  public static final String HEADED_ROOT_SYNTAX_PARAMETERS = "headedRootSyntax";

  /**
   * Default part-of-speech tag.
   */
  public static final String DEFAULT_POS_TAG = "UNK-POS";

  /**
   * Default part-of-speech tags that qualify as punctuation.
   */
  public static final Set<String> DEFAULT_PUNC_TAGS = Sets.newHashSet(",", ":", ";", ".");

  /**
   * Default part-of-speech tags that qualify as verbs.
   */
  public static final Set<String> DEFAULT_VERB_TAGS = Sets.newHashSet("VB", "VBD", "VBG", "VBN", "VBP", "VBZ");
  
  private static final IndexedList<String> STATISTIC_NAME_LIST = IndexedList.create(Arrays.asList(
      LEXICON_PARAMETERS,DEPENDENCY_PARAMETERS, WORD_DISTANCE_PARAMETERS,
      PUNC_DISTANCE_PARAMETERS, VERB_DISTANCE_PARAMETERS, SYNTAX_PARAMETERS,
      UNARY_RULE_PARAMETERS, HEADED_SYNTAX_PARAMETERS, ROOT_SYNTAX_PARAMETERS,
      HEADED_ROOT_SYNTAX_PARAMETERS)); 

  public ParametricCcgParser(ParametricCcgLexicon lexiconFamily,
      VariableNumMap dependencyHeadVar, VariableNumMap dependencySyntaxVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar, VariableNumMap dependencyHeadPosVar,
      VariableNumMap dependencyArgPosVar, ParametricFactor dependencyFamily, VariableNumMap wordDistanceVar,
      ParametricFactor wordDistanceFamily, VariableNumMap puncDistanceVar,
      ParametricFactor puncDistanceFamily, Set<String> puncTagSet, VariableNumMap verbDistanceVar,
      ParametricFactor verbDistanceFamily, Set<String> verbTagSet, VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      ParametricFactor syntaxFamily, VariableNumMap unaryRuleInputVar,
      VariableNumMap unaryRuleVar, ParametricFactor unaryRuleFamily,
      VariableNumMap headedBinaryRulePredicateVar, VariableNumMap headedBinaryRulePosVar,
      ParametricFactor headedBinaryRuleFamily,
      VariableNumMap searchMoveVar, DiscreteFactor compiledSyntaxDistribution,
      VariableNumMap rootSyntaxVar, VariableNumMap rootPredicateVar, VariableNumMap rootPosVar,
      ParametricFactor rootSyntaxFamily, ParametricFactor headedRootSyntaxFamily, boolean allowWordSkipping,
      boolean normalFormOnly) {
    this.lexiconFamily = Preconditions.checkNotNull(lexiconFamily);

    this.dependencyHeadVar = Preconditions.checkNotNull(dependencyHeadVar);
    this.dependencySyntaxVar = Preconditions.checkNotNull(dependencySyntaxVar);
    this.dependencyArgNumVar = Preconditions.checkNotNull(dependencyArgNumVar);
    this.dependencyArgVar = Preconditions.checkNotNull(dependencyArgVar);
    this.dependencyHeadPosVar = Preconditions.checkNotNull(dependencyHeadPosVar);
    this.dependencyArgPosVar = Preconditions.checkNotNull(dependencyArgPosVar);
    this.dependencyFamily = Preconditions.checkNotNull(dependencyFamily);

    this.wordDistanceVar = wordDistanceVar;
    this.wordDistanceFamily = wordDistanceFamily;
    this.puncDistanceVar = puncDistanceVar;
    this.puncDistanceFamily = puncDistanceFamily;
    this.puncTagSet = puncTagSet;
    this.verbDistanceVar = verbDistanceVar;
    this.verbDistanceFamily = verbDistanceFamily;
    this.verbTagSet = verbTagSet;

    this.leftSyntaxVar = Preconditions.checkNotNull(leftSyntaxVar);
    this.rightSyntaxVar = Preconditions.checkNotNull(rightSyntaxVar);
    this.parentSyntaxVar = Preconditions.checkNotNull(parentSyntaxVar);
    this.syntaxFamily = Preconditions.checkNotNull(syntaxFamily);

    this.unaryRuleInputVar = Preconditions.checkNotNull(unaryRuleInputVar);
    this.unaryRuleVar = Preconditions.checkNotNull(unaryRuleVar);
    this.unaryRuleFamily = Preconditions.checkNotNull(unaryRuleFamily);

    this.headedBinaryRulePredicateVar = Preconditions.checkNotNull(headedBinaryRulePredicateVar);
    this.headedBinaryRulePosVar = Preconditions.checkNotNull(headedBinaryRulePosVar);
    this.headedBinaryRuleFamily = Preconditions.checkNotNull(headedBinaryRuleFamily);

    this.searchMoveVar = Preconditions.checkNotNull(searchMoveVar);
    this.compiledSyntaxDistribution = Preconditions.checkNotNull(compiledSyntaxDistribution);

    this.rootSyntaxVar = Preconditions.checkNotNull(rootSyntaxVar);
    this.rootPredicateVar = Preconditions.checkNotNull(rootPredicateVar);
    this.rootPosVar = Preconditions.checkNotNull(rootPosVar);
    this.rootSyntaxFamily = Preconditions.checkNotNull(rootSyntaxFamily);
    this.headedRootSyntaxFamily = Preconditions.checkNotNull(headedRootSyntaxFamily);

    this.allowWordSkipping = allowWordSkipping;
    this.normalFormOnly = normalFormOnly;
  }

  /**
   * Produces a parametric CCG parser from a lexicon, represented a
   * series of phrase/CCG category mappings. Each mapping is given as
   * a comma separated string, whose first value is the phrase and
   * whose second value is the CCG category string. Lines beginning
   * with # are interpreted as comments and skipped over.
   * 
   * @param unfilteredLexiconLines
   * @param unfilteredRuleLines
   * @param featureFactory a factory for building the parser's feature
   * sets.
   * @param posTagSet set of POS tags in the data. If null,
   * {@code ParametricCcgParser.DEFAULT_POS_TAG} is the only POS tag.
   * @param allowComposition allow function composition in addition to
   * other CCG rules.
   * @param allowedCombinationRules
   * @param allowWordSkipping
   * @param normalFormOnly
   * @return
   */
  public static ParametricCcgParser parseFromLexicon(Iterable<String> unfilteredLexiconLines,
      Iterable<String> unfilteredRuleLines, CcgFeatureFactory featureFactory,
      Set<String> posTagSet, boolean allowComposition,
      Iterable<CcgRuleSchema> allowedCombinationRules, boolean allowWordSkipping,
      boolean normalFormOnly) {
    Preconditions.checkNotNull(featureFactory);

    System.out.println("Reading lexicon and rules...");
    List<CcgBinaryRule> binaryRules = Lists.newArrayList();
    List<CcgUnaryRule> unaryRules = Lists.newArrayList();
    for (String line : unfilteredRuleLines) {
      System.out.println(line);
      if (!line.startsWith("#")) {
        try {
          binaryRules.add(CcgBinaryRule.parseFrom(line));
        } catch (IllegalArgumentException e) {
          unaryRules.add(CcgUnaryRule.parseFrom(line));
        }
      }
    }

    // Remove comments, which are lines that begin with "#".
    List<String> lexiconLines = Lists.newArrayList();
    for (String line : unfilteredLexiconLines) {
      if (!line.startsWith("#")) {
        lexiconLines.add(line);
      }
    }

    // Parse out all of the categories, words, and semanticPredicates
    // from the lexicon.
    IndexedList<CcgCategory> categories = IndexedList.create();
    IndexedList<List<String>> words = IndexedList.create();
    IndexedList<String> semanticPredicates = IndexedList.create();
    Map<Integer, Integer> maxNumArgs = Maps.newHashMap();
    Set<HeadedSyntacticCategory> syntacticCategories = Sets.newHashSet();
    for (String lexiconLine : lexiconLines) {
      // Create the CCG category.
      LexiconEntry lexiconEntry = LexiconEntry.parseLexiconEntry(lexiconLine);
      words.add(lexiconEntry.getWords());
      categories.add(lexiconEntry.getCategory());
      syntacticCategories.add(lexiconEntry.getCategory().getSyntax().getCanonicalForm());

      // Store the values of any assignments as semantic predicates.
      semanticPredicates.addAll(Iterables.concat(lexiconEntry.getCategory().getAssignment()));
      // Store any predicates from the subjects of the dependency
      // structures.
      addSubjectsToPredicateList(lexiconEntry.getCategory().getSubjects(),
          lexiconEntry.getCategory().getArgumentNumbers(), semanticPredicates, maxNumArgs);
    }

    // Add any predicates from binary and unary rules.
    for (CcgBinaryRule rule : binaryRules) {
      syntacticCategories.add(rule.getLeftSyntacticType().getCanonicalForm());
      syntacticCategories.add(rule.getRightSyntacticType().getCanonicalForm());
      syntacticCategories.add(rule.getParentSyntacticType().getCanonicalForm());

      addSubjectsToPredicateList(Arrays.asList(rule.getSubjects()), Ints.asList(rule.getArgumentNumbers()),
          semanticPredicates, maxNumArgs);
    }
    for (CcgUnaryRule rule : unaryRules) {
      syntacticCategories.add(rule.getInputSyntacticCategory().getCanonicalForm());
      syntacticCategories.add(rule.getResultSyntacticCategory().getCanonicalForm());

      addSubjectsToPredicateList(rule.getSubjects(), rule.getArgumentNumbers(), semanticPredicates,
          maxNumArgs);
    }

    // Create features over ways to combine syntactic categories.
    System.out.println("Building syntactic distribution...");
    DiscreteVariable syntaxType = CcgParser.buildSyntacticCategoryDictionary(syntacticCategories);
    DiscreteFactor binaryRuleDistribution = null;
    if (allowedCombinationRules == null) {
      binaryRuleDistribution = CcgParser.buildUnrestrictedBinaryDistribution(syntaxType, binaryRules, allowComposition);
    } else {
      binaryRuleDistribution = CcgParser.buildRestrictedBinaryDistribution(syntaxType, allowedCombinationRules, binaryRules,
          allowComposition);
    }
    VariableNumMap leftSyntaxVar = binaryRuleDistribution.getVars().getVariablesByName(CcgParser.LEFT_SYNTAX_VAR_NAME);
    VariableNumMap rightSyntaxVar = binaryRuleDistribution.getVars().getVariablesByName(CcgParser.RIGHT_SYNTAX_VAR_NAME);
    VariableNumMap parentSyntaxVar = binaryRuleDistribution.getVars().getVariablesByName(CcgParser.PARENT_SYNTAX_VAR_NAME);
    ParametricFactor parametricBinaryRuleDistribution = featureFactory.getBinaryRuleFeatures(
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, binaryRuleDistribution);

    // Create features over unary rules.
    DiscreteFactor unaryRuleDistribution = CcgParser.buildUnaryRuleDistribution(unaryRules, syntaxType);
    VariableNumMap unaryRuleInputVar = unaryRuleDistribution.getVars().getVariablesByName(CcgParser.UNARY_RULE_INPUT_VAR_NAME);
    VariableNumMap unaryRuleVar = unaryRuleDistribution.getVars().getVariablesByName(CcgParser.UNARY_RULE_VAR_NAME);
    ParametricFactor parametricUnaryRuleDistribution = featureFactory.getUnaryRuleFeatures(
        unaryRuleInputVar, unaryRuleVar, unaryRuleDistribution);

    // Build an indicator distribution over CCG parsing operations.
    DiscreteFactor compiledSyntaxDistribution = CcgParser.compileUnaryAndBinaryRules(unaryRuleDistribution,
        binaryRuleDistribution, syntaxType);
    VariableNumMap searchMoveVar = compiledSyntaxDistribution.getVars().getVariablesByName(
        CcgParser.PARENT_MOVE_SYNTAX_VAR_NAME);

    // Build the terminal distribution. This maps word sequences to
    // CCG categories, with one possible mapping per entry in the
    // lexicon.
    DiscreteVariable wordType = new DiscreteVariable("words", words.items());
    DiscreteVariable ccgCategoryType = new DiscreteVariable("ccgCategory", categories.items());
    DiscreteVariable ccgSyntaxType = leftSyntaxVar.getDiscreteVariables().get(0);

    VariableNumMap terminalVar = VariableNumMap.singleton(0, "words", wordType);
    VariableNumMap ccgCategoryVar = VariableNumMap.singleton(1, "ccgCategory", ccgCategoryType);
    VariableNumMap terminalSyntaxVar = VariableNumMap.singleton(1, "terminalSyntax", ccgSyntaxType);

    VariableNumMap terminalWordVars = VariableNumMap.unionAll(terminalVar, ccgCategoryVar);
    VariableNumMap terminalWordSyntaxVars = VariableNumMap.unionAll(terminalVar, terminalSyntaxVar);
    posTagSet = posTagSet != null ? posTagSet : Sets.newHashSet(DEFAULT_POS_TAG);
    DiscreteVariable posType = new DiscreteVariable("pos", posTagSet);
    VariableNumMap posVar = VariableNumMap.singleton(0, "pos", posType);

    TableFactorBuilder terminalBuilder = new TableFactorBuilder(terminalWordVars, SparseTensorBuilder.getFactory());
    TableFactorBuilder terminalSyntaxBuilder = new TableFactorBuilder(terminalWordSyntaxVars, SparseTensorBuilder.getFactory());
    for (String lexiconLine : lexiconLines) {
      LexiconEntry lexiconEntry = LexiconEntry.parseLexiconEntry(lexiconLine);
      List<String> lexiconWords = lexiconEntry.getWords();
      for (String word : lexiconWords) {
        Preconditions.checkArgument(word.toLowerCase().equals(word), "Lexicon entry is not lowercased: " + lexiconEntry);
      }

      terminalBuilder.setWeight(terminalWordVars.outcomeArrayToAssignment(lexiconWords,
          lexiconEntry.getCategory()), 1.0);
      terminalSyntaxBuilder.setWeight(terminalWordSyntaxVars.outcomeArrayToAssignment(lexiconWords,
          lexiconEntry.getCategory().getSyntax()), 1.0);
    }

    ParametricCcgLexicon lexiconFamily = featureFactory.getLexiconFeatures(terminalVar,
        ccgCategoryVar, posVar, terminalSyntaxVar, terminalBuilder.build());

    // Create variables for representing the CCG parser's dependency
    // structures.
    DiscreteVariable semanticPredicateType = new DiscreteVariable("semanticPredicates",
        semanticPredicates.items());
    // The set of possible argument numbers depends on the entries
    // provided in the lexicon.
    int maxArgNum = Collections.max(maxNumArgs.values());
    List<Integer> argNumValues = Lists.newArrayList();
    for (int i = 0; i <= maxArgNum; i++) {
      argNumValues.add(i);
    }
    DiscreteVariable argumentNums = new DiscreteVariable("argNums", argNumValues);

    VariableNumMap dependencyHeadVar = VariableNumMap.singleton(0, "dependencyHead", semanticPredicateType);
    VariableNumMap dependencySyntaxVar = VariableNumMap.singleton(1, "dependencySyntax", ccgSyntaxType);
    VariableNumMap dependencyArgNumVar = VariableNumMap.singleton(2, "dependencyArgNum", argumentNums);
    VariableNumMap dependencyArgVar = VariableNumMap.singleton(3, "dependencyArg", semanticPredicateType);
    VariableNumMap dependencyHeadPosVar = VariableNumMap.singleton(4, "dependencyHeadPos", posType);
    VariableNumMap dependencyArgPosVar = VariableNumMap.singleton(5, "dependencyArgPos", posType);

    // Create features over argument distances for each dependency.
    VariableNumMap wordDistanceVar = VariableNumMap.singleton(6, "wordDistance", CcgParser.wordDistanceVarType);
    ParametricFactor wordDistanceFactor = featureFactory.getDependencyWordDistanceFeatures(
        dependencyHeadVar, dependencySyntaxVar, dependencyArgNumVar, dependencyHeadPosVar, wordDistanceVar);
    VariableNumMap puncDistanceVar = VariableNumMap.singleton(6, "puncDistance", CcgParser.puncDistanceVarType);
    ParametricFactor puncDistanceFactor = featureFactory.getDependencyWordDistanceFeatures(
        dependencyHeadVar, dependencySyntaxVar, dependencyArgNumVar, dependencyHeadPosVar, puncDistanceVar);
    VariableNumMap verbDistanceVar = VariableNumMap.singleton(6, "verbDistance", CcgParser.verbDistanceVarType);
    ParametricFactor verbDistanceFactor = featureFactory.getDependencyWordDistanceFeatures(
        dependencyHeadVar, dependencySyntaxVar, dependencyArgNumVar, dependencyHeadPosVar, verbDistanceVar);

    // Create features over dependency structures.
    ParametricFactor dependencyParametricFactor = featureFactory.getDependencyFeatures(dependencyHeadVar,
        dependencySyntaxVar, dependencyArgNumVar, dependencyArgVar, dependencyHeadPosVar, dependencyArgPosVar);

    Set<String> puncTagSet = DEFAULT_PUNC_TAGS;
    Set<String> verbTagSet = DEFAULT_VERB_TAGS;

    // Create features over headed binary rules
    int maxVarNum = Ints.max(binaryRuleDistribution.getVars().getVariableNumsArray());
    VariableNumMap headedBinaryRulePredicateVar = VariableNumMap.singleton(maxVarNum + 1,
        "headedBinaryRulePredicate", semanticPredicateType);
    VariableNumMap headedBinaryRulePosVar = VariableNumMap.singleton(maxVarNum + 2,
        "headedBinaryRulePos", posType);
    ParametricFactor headedBinaryRuleFamily = featureFactory.getHeadedBinaryRuleFeatures(
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, headedBinaryRulePredicateVar, headedBinaryRulePosVar);

    // Create root syntax factor.
    DiscreteLogLinearFactor parametricRootDistribution = DiscreteLogLinearFactor
        .createIndicatorFactor(leftSyntaxVar);
    ParametricFactor parametricHeadedRootDistribution = featureFactory.getHeadedRootFeatures(
        leftSyntaxVar, headedBinaryRulePredicateVar, headedBinaryRulePosVar);

    return new ParametricCcgParser(lexiconFamily, dependencyHeadVar,
        dependencySyntaxVar, dependencyArgNumVar, dependencyArgVar, dependencyHeadPosVar, dependencyArgPosVar,
        dependencyParametricFactor, wordDistanceVar, wordDistanceFactor, puncDistanceVar,
        puncDistanceFactor, puncTagSet, verbDistanceVar,
        verbDistanceFactor, verbTagSet, leftSyntaxVar, rightSyntaxVar, parentSyntaxVar,
        parametricBinaryRuleDistribution, unaryRuleInputVar, unaryRuleVar,
        parametricUnaryRuleDistribution, headedBinaryRulePredicateVar, headedBinaryRulePosVar,
        headedBinaryRuleFamily, searchMoveVar, compiledSyntaxDistribution,
        leftSyntaxVar, headedBinaryRulePredicateVar, headedBinaryRulePosVar, parametricRootDistribution,
        parametricHeadedRootDistribution, allowWordSkipping, normalFormOnly);
  }

  /**
   * Adds the predicates in {@code subjects} to
   * {@code semanticPredicates}, while simultaneously counting the
   * argument numbers each predicate appears with and maintaining the
   * maximum number seen in {@code maxNumArgs}.
   * 
   * @param subjects
   * @param argNums
   * @param semanticPredicates
   * @param maxNumArgs
   */
  private static void addSubjectsToPredicateList(List<String> subjects, List<Integer> argNums,
      IndexedList<String> semanticPredicates, Map<Integer, Integer> maxNumArgs) {
    Preconditions.checkArgument(subjects.size() == argNums.size());
    for (int i = 0; i < subjects.size(); i++) {
      String subject = subjects.get(i);
      int argNum = argNums.get(i);
      semanticPredicates.add(subject);
      int predicateIndex = semanticPredicates.getIndex(subject);

      if (!maxNumArgs.containsKey(predicateIndex)) {
        maxNumArgs.put(predicateIndex, argNum);
      } else {
        maxNumArgs.put(predicateIndex, Math.max(maxNumArgs.get(predicateIndex), argNum));
      }
    }
  }

  public DiscreteVariable getPredicateVar() {
    return dependencyHeadVar.getDiscreteVariables().get(0);
  }

  /*
   * // This code is presumably used in the mention ccg parser. public
   * VariableNumMap getTerminalVar() { return terminalVar; }
   * 
   * public VariableNumMap getCcgCategoryVar() { return
   * ccgCategoryVar; }
   * 
   * public ParametricCcgParser replaceTerminalFamily(ParametricFactor
   * newTerminalFamily) { return new ParametricCcgParser(terminalVar,
   * ccgCategoryVar, newTerminalFamily, terminalPosVar,
   * terminalSyntaxVar, terminalPosFamily, terminalSyntaxFamily,
   * dependencyHeadVar, dependencySyntaxVar, dependencyArgNumVar,
   * dependencyArgVar, dependencyHeadPosVar, dependencyArgPosVar,
   * dependencyFamily, wordDistanceVar, wordDistanceFamily,
   * puncDistanceVar, puncDistanceFamily, puncTagSet, verbDistanceVar,
   * verbDistanceFamily, verbTagSet, leftSyntaxVar, rightSyntaxVar,
   * parentSyntaxVar, syntaxFamily, unaryRuleInputVar, unaryRuleVar,
   * unaryRuleFamily, headedBinaryRulePredicateVar,
   * headedBinaryRulePosVar, headedBinaryRuleFamily, searchMoveVar,
   * compiledSyntaxDistribution, rootSyntaxVar, rootPredicateVar,
   * rootPosVar, rootSyntaxFamily, headedRootSyntaxFamily,
   * allowWordSkipping, normalFormOnly); }
   */

  /**
   * Gets a new all-zero parameter vector.
   * 
   * @return
   */
  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics lexiconParameters = lexiconFamily.getNewSufficientStatistics();
    SufficientStatistics dependencyParameters = dependencyFamily.getNewSufficientStatistics();
    SufficientStatistics wordDistanceParameters = wordDistanceFamily.getNewSufficientStatistics();
    SufficientStatistics puncDistanceParameters = puncDistanceFamily.getNewSufficientStatistics();
    SufficientStatistics verbDistanceParameters = verbDistanceFamily.getNewSufficientStatistics();
    SufficientStatistics syntaxParameters = syntaxFamily.getNewSufficientStatistics();
    SufficientStatistics unaryRuleParameters = unaryRuleFamily.getNewSufficientStatistics();
    SufficientStatistics headedBinaryRuleParameters = headedBinaryRuleFamily.getNewSufficientStatistics();
    SufficientStatistics rootSyntaxParameters = rootSyntaxFamily.getNewSufficientStatistics();
    SufficientStatistics headedRootSyntaxParameters = headedRootSyntaxFamily.getNewSufficientStatistics();

    return new ListSufficientStatistics(STATISTIC_NAME_LIST,
        Arrays.asList(lexiconParameters, dependencyParameters, wordDistanceParameters,
            puncDistanceParameters, verbDistanceParameters, syntaxParameters, unaryRuleParameters,
            headedBinaryRuleParameters, rootSyntaxParameters, headedRootSyntaxParameters));
  }

  /**
   * Instantiates a {@code CcgParser} whose probability distributions
   * are derived from {@code parameters}.
   * 
   * @param parameters
   * @return
   */
  @Override
  public CcgParser getModelFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    CcgLexicon lexiconDistribution = lexiconFamily.getModelFromParameters(
        parameterList.getStatisticByName(LEXICON_PARAMETERS));

    DiscreteFactor dependencyDistribution = dependencyFamily.getModelFromParameters(
        parameterList.getStatisticByName(DEPENDENCY_PARAMETERS)).coerceToDiscrete();

    DiscreteFactor wordDistanceDistribution = wordDistanceFamily.getModelFromParameters(
        parameterList.getStatisticByName(WORD_DISTANCE_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor puncDistanceDistribution = puncDistanceFamily.getModelFromParameters(
        parameterList.getStatisticByName(PUNC_DISTANCE_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor verbDistanceDistribution = verbDistanceFamily.getModelFromParameters(
        parameterList.getStatisticByName(VERB_DISTANCE_PARAMETERS)).coerceToDiscrete();

    DiscreteFactor syntaxDistribution = syntaxFamily.getModelFromParameters(
        parameterList.getStatisticByName(SYNTAX_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor unaryRuleDistribution = unaryRuleFamily.getModelFromParameters(
        parameterList.getStatisticByName(UNARY_RULE_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor headedSyntaxDistribution = headedBinaryRuleFamily.getModelFromParameters(
        parameterList.getStatisticByName(HEADED_SYNTAX_PARAMETERS)).coerceToDiscrete();

    DiscreteFactor rootSyntaxDistribution = rootSyntaxFamily.getModelFromParameters(
        parameterList.getStatisticByName(ROOT_SYNTAX_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor headedRootSyntaxDistribution = headedRootSyntaxFamily.getModelFromParameters(
        parameterList.getStatisticByName(HEADED_ROOT_SYNTAX_PARAMETERS)).coerceToDiscrete();

    return new CcgParser(lexiconDistribution,
        dependencyHeadVar, dependencySyntaxVar, dependencyArgNumVar, dependencyArgVar,
        dependencyHeadPosVar, dependencyArgPosVar, dependencyDistribution,
        wordDistanceVar, wordDistanceDistribution, puncDistanceVar, puncDistanceDistribution,
        puncTagSet, verbDistanceVar, verbDistanceDistribution, verbTagSet,
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, syntaxDistribution,
        unaryRuleInputVar, unaryRuleVar, unaryRuleDistribution,
        headedBinaryRulePredicateVar, headedBinaryRulePosVar, headedSyntaxDistribution, searchMoveVar,
        compiledSyntaxDistribution, rootSyntaxVar, rootPredicateVar, rootPosVar, rootSyntaxDistribution,
        headedRootSyntaxDistribution, allowWordSkipping, normalFormOnly);
  }

  /**
   * Increments {@code gradient} by
   * {@code count * features(dependency)} for all dependency
   * structures in {@code dependencies}.
   * 
   * @param gradient
   * @param posTags
   * @param dependencies
   * @param count
   */
  public void incrementDependencySufficientStatistics(SufficientStatistics gradient,
      List<String> posTags, Collection<DependencyStructure> dependencies, double count) {

    int[] puncCounts = CcgParser.computeDistanceCounts(posTags, puncTagSet);
    int[] verbCounts = CcgParser.computeDistanceCounts(posTags, verbTagSet);

    SufficientStatistics dependencyGradient = gradient.coerceToList().getStatisticByName(DEPENDENCY_PARAMETERS);
    SufficientStatistics wordDistanceGradient = gradient.coerceToList().getStatisticByName(WORD_DISTANCE_PARAMETERS);
    SufficientStatistics puncDistanceGradient = gradient.coerceToList().getStatisticByName(PUNC_DISTANCE_PARAMETERS);
    SufficientStatistics verbDistanceGradient = gradient.coerceToList().getStatisticByName(VERB_DISTANCE_PARAMETERS);
    for (DependencyStructure dependency : dependencies) {
      int headWordIndex = dependency.getHeadWordIndex();
      int objectWordIndex = dependency.getObjectWordIndex();

      Assignment predicateAssignment = Assignment.unionAll(
          dependencyHeadVar.outcomeArrayToAssignment(dependency.getHead()),
          dependencySyntaxVar.outcomeArrayToAssignment(dependency.getHeadSyntacticCategory()),
          dependencyArgNumVar.outcomeArrayToAssignment(dependency.getArgIndex()),
          dependencyHeadPosVar.outcomeArrayToAssignment(posTags.get(headWordIndex)),
          dependencyArgPosVar.outcomeArrayToAssignment(posTags.get(objectWordIndex)));
      Assignment assignment = predicateAssignment.union(
          dependencyArgVar.outcomeArrayToAssignment(dependency.getObject()));

      dependencyFamily.incrementSufficientStatisticsFromAssignment(dependencyGradient, assignment, count);

      // Update distance parameters.
      int wordDistance = CcgParser.computeWordDistance(headWordIndex, objectWordIndex);
      int puncDistance = CcgParser.computeArrayDistance(puncCounts, headWordIndex, objectWordIndex);
      int verbDistance = CcgParser.computeArrayDistance(verbCounts, headWordIndex, objectWordIndex);

      Assignment wordDistanceAssignment = predicateAssignment.union(
          wordDistanceVar.outcomeArrayToAssignment(wordDistance));
      wordDistanceFamily.incrementSufficientStatisticsFromAssignment(wordDistanceGradient,
          wordDistanceAssignment, count);

      Assignment puncDistanceAssignment = predicateAssignment.union(
          puncDistanceVar.outcomeArrayToAssignment(puncDistance));
      puncDistanceFamily.incrementSufficientStatisticsFromAssignment(puncDistanceGradient,
          puncDistanceAssignment, count);

      Assignment verbDistanceAssignment = predicateAssignment.union(
          verbDistanceVar.outcomeArrayToAssignment(verbDistance));
      verbDistanceFamily.incrementSufficientStatisticsFromAssignment(verbDistanceGradient,
          verbDistanceAssignment, count);
    }
  }

  public void incrementSyntaxSufficientStatistics(SufficientStatistics gradient, CcgParse parse,
      List<String> posTags, double count) {
    SufficientStatistics syntaxGradient = gradient.coerceToList().getStatisticByName(SYNTAX_PARAMETERS);
    SufficientStatistics headedSyntaxGradient = gradient.coerceToList().getStatisticByName(HEADED_SYNTAX_PARAMETERS);
    if (!parse.isTerminal()) {
      CcgParse left = parse.getLeft();
      CcgParse right = parse.getRight();
      Combinator combinator = parse.getCombinator();
      // Increment the gradient for the rule applied at this node.
      Assignment assignment = Assignment.unionAll(
          leftSyntaxVar.outcomeArrayToAssignment(left.getHeadedSyntacticCategory()),
          rightSyntaxVar.outcomeArrayToAssignment(right.getHeadedSyntacticCategory()),
          parentSyntaxVar.outcomeArrayToAssignment(combinator));
      syntaxFamily.incrementSufficientStatisticsFromAssignment(syntaxGradient, assignment, count);

      // Increment the headed syntax gradient.
      for (IndexedPredicate semanticHead : parse.getSemanticHeads()) {
        String posTag = posTags.get(semanticHead.getHeadIndex());
        Assignment headedSyntaxAssignment = Assignment.unionAll(assignment,
            headedBinaryRulePredicateVar.outcomeArrayToAssignment(semanticHead.getHead()),
            headedBinaryRulePosVar.outcomeArrayToAssignment(posTag));
        headedBinaryRuleFamily.incrementSufficientStatisticsFromAssignment(headedSyntaxGradient,
            headedSyntaxAssignment, count);
      }

      // Recursively increment gradient for rules in subtrees.
      incrementSyntaxSufficientStatistics(gradient, left, posTags, count);
      incrementSyntaxSufficientStatistics(gradient, right, posTags, count);
    }

    // Increment unary rule parameters.
    if (parse.getUnaryRule() != null) {
      UnaryCombinator rule = parse.getUnaryRule();
      SufficientStatistics unaryRuleGradient = gradient.coerceToList().getStatisticByName(UNARY_RULE_PARAMETERS);
      Assignment unaryRuleAssignment = unaryRuleInputVar.outcomeArrayToAssignment(
          rule.getInputType()).union(unaryRuleVar.outcomeArrayToAssignment(rule));
      unaryRuleFamily.incrementSufficientStatisticsFromAssignment(unaryRuleGradient,
          unaryRuleAssignment, count);
    }
  }

  public void incrementRootSyntaxSufficientStatistics(SufficientStatistics parameters,
      HeadedSyntacticCategory category, Collection<IndexedPredicate> heads, List<String> posTags, double count) {
    SufficientStatistics rootParameters = parameters.coerceToList()
        .getStatisticByName(ROOT_SYNTAX_PARAMETERS);
    SufficientStatistics headedRootParameters = parameters.coerceToList()
        .getStatisticByName(HEADED_ROOT_SYNTAX_PARAMETERS);

    Assignment assignment = rootSyntaxVar.outcomeArrayToAssignment(category);
    rootSyntaxFamily.incrementSufficientStatisticsFromAssignment(rootParameters, assignment, count);

    for (IndexedPredicate head : heads) {
      String headPredicate = head.getHead();
      String posTag = posTags.get(head.getHeadIndex());

      Assignment headedAssignment = Assignment.unionAll(
          rootSyntaxVar.outcomeArrayToAssignment(category),
          rootPredicateVar.outcomeArrayToAssignment(headPredicate),
          rootPosVar.outcomeArrayToAssignment(posTag));
      headedRootSyntaxFamily.incrementSufficientStatisticsFromAssignment(headedRootParameters,
          headedAssignment, count);
    }
  }

  /**
   * Increments {@code gradient} by {@code count * features(parse)},
   * where {@code features} is a function from CCG parses to a feature
   * vectors.
   * 
   * @param gradient
   * @param parse
   * @param count
   */
  public void incrementSufficientStatistics(SufficientStatistics gradient, CcgParse parse,
      double count) {
    // Update the dependency structure parameters, including distance
    // parameters.
    incrementDependencySufficientStatistics(gradient, parse.getSpannedPosTags(),
        parse.getAllDependencies(), count);
    // Update syntactic combination parameters. (Both unary and binary
    // rules)
    incrementSyntaxSufficientStatistics(gradient, parse, parse.getSpannedPosTags(), count);
    incrementRootSyntaxSufficientStatistics(gradient, parse.getHeadedSyntacticCategory(),
        parse.getSemanticHeads(), parse.getSpannedPosTags(), count);
    // Update terminal distribution parameters.
    SufficientStatistics lexiconParameters = gradient.coerceToList().getStatisticByName(LEXICON_PARAMETERS);
    lexiconFamily.incrementLexiconSufficientStatistics(lexiconParameters, parse, count);
  }

  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  /**
   * Gets a human-readable description of the {@code numFeatures}
   * highest-weighted (in absolute value) features of
   * {@code parameters}.
   * 
   * @param parameters
   * @param numFeatures
   * @return
   */
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    ListSufficientStatistics parameterList = parameters.coerceToList();

    StringBuilder sb = new StringBuilder();
    sb.append(lexiconFamily.getParameterDescription(
        parameterList.getStatisticByName(LEXICON_PARAMETERS), numFeatures));

    sb.append(syntaxFamily.getParameterDescription(
        parameterList.getStatisticByName(SYNTAX_PARAMETERS), numFeatures));
    sb.append(unaryRuleFamily.getParameterDescription(
        parameterList.getStatisticByName(UNARY_RULE_PARAMETERS), numFeatures));
    sb.append(headedBinaryRuleFamily.getParameterDescription(
        parameterList.getStatisticByName(HEADED_SYNTAX_PARAMETERS), numFeatures));
    sb.append(rootSyntaxFamily.getParameterDescription(
        parameterList.getStatisticByName(ROOT_SYNTAX_PARAMETERS), numFeatures));
    sb.append(headedRootSyntaxFamily.getParameterDescription(
        parameterList.getStatisticByName(HEADED_ROOT_SYNTAX_PARAMETERS), numFeatures));

    sb.append(dependencyFamily.getParameterDescription(
        parameterList.getStatisticByName(DEPENDENCY_PARAMETERS), numFeatures));

    sb.append(wordDistanceFamily.getParameterDescription(
        parameterList.getStatisticByName(WORD_DISTANCE_PARAMETERS), numFeatures));
    sb.append(puncDistanceFamily.getParameterDescription(
        parameterList.getStatisticByName(PUNC_DISTANCE_PARAMETERS), numFeatures));
    sb.append(verbDistanceFamily.getParameterDescription(
        parameterList.getStatisticByName(VERB_DISTANCE_PARAMETERS), numFeatures));

    return sb.toString();
  }
}
