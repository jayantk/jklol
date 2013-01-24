package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.CombiningParametricFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.StringUtils;

/**
 * Parameterized CCG grammar. This class instantiates CCG parsers
 * given parameter vectors. This class parameterizes CCGs with
 * probabilities for:
 * <ul>
 * <li>Lexicon entries.
 * <li>Dependency structures (i.e., semantic dependencies).
 * </ul>
 * 
 * @author jayant
 */
public class ParametricCcgParser implements ParametricFamily<CcgParser> {
  
  private static final long serialVersionUID = 1L;
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final ParametricFactor terminalFamily;
  
  private final VariableNumMap terminalPosVar;
  private final VariableNumMap terminalSyntaxVar;
  private final ParametricFactor terminalPosFamily;

  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
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

  private final VariableNumMap rootSyntaxVar;
  private final ParametricFactor rootSyntaxFamily;
  
  /**
   * Name of the parameter vector governing lexicon entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";
  
  public static final String TERMINAL_POS_PARAMETERS = "terminalPos";

  /**
   * Name of the parameter vector governing dependency structures.
   */
  public static final String DEPENDENCY_PARAMETERS = "dependencies";
  
  public static final String WORD_DISTANCE_PARAMETERS = "wordDistance";
  public static final String PUNC_DISTANCE_PARAMETERS = "puncDistance";
  public static final String VERB_DISTANCE_PARAMETERS = "verbDistance";
  
  /**
   * Name of the parameter vector governing combinations of 
   * syntactic categories.
   */
  public static final String SYNTAX_PARAMETERS = "syntax";
  public static final String UNARY_RULE_PARAMETERS = "unaryRules";
  public static final String ROOT_SYNTAX_PARAMETERS = "rootSyntax";
  
  public static final String INPUT_DEPENDENCY_PARAMETERS = "inputFeatures";
  public static final String INDICATOR_DEPENDENCY_PARAMETERS = "indicatorFeatures";
  
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

  public ParametricCcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      ParametricFactor terminalFamily, VariableNumMap terminalPosVar,
      VariableNumMap terminalSyntaxVar, ParametricFactor terminalPosFamily, VariableNumMap dependencyHeadVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      ParametricFactor dependencyFamily, VariableNumMap wordDistanceVar,
      ParametricFactor wordDistanceFamily, VariableNumMap puncDistanceVar,
      ParametricFactor puncDistanceFamily, Set<String> puncTagSet, VariableNumMap verbDistanceVar,
      ParametricFactor verbDistanceFamily, Set<String> verbTagSet, VariableNumMap leftSyntaxVar, 
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar, 
      ParametricFactor syntaxFamily, VariableNumMap unaryRuleInputVar,
      VariableNumMap unaryRuleVar, ParametricFactor unaryRuleFamily, 
      VariableNumMap rootSyntaxVar, ParametricFactor rootSyntaxFamily) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalFamily = Preconditions.checkNotNull(terminalFamily);
    
    this.terminalPosVar = Preconditions.checkNotNull(terminalPosVar);
    this.terminalSyntaxVar = Preconditions.checkNotNull(terminalSyntaxVar);
    this.terminalPosFamily = Preconditions.checkNotNull(terminalPosFamily);

    this.dependencyHeadVar = Preconditions.checkNotNull(dependencyHeadVar);
    this.dependencyArgNumVar = Preconditions.checkNotNull(dependencyArgNumVar);
    this.dependencyArgVar = Preconditions.checkNotNull(dependencyArgVar);
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
    
    this.rootSyntaxVar = Preconditions.checkNotNull(rootSyntaxVar);
    this.rootSyntaxFamily = Preconditions.checkNotNull(rootSyntaxFamily);
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
   * @param dependencyFeatures if not null, a list of files containing 
   * features of dependency structures.
   * @param posTagSet set of POS tags in the data. If null, 
   * ParametricCcgParser.DEFAULT_POS_TAG is the only POS tag.
   * @param allowComposition allow function composition in addition to 
   * other CCG rules.
   * @return
   */
  public static ParametricCcgParser parseFromLexicon(Iterable<String> unfilteredLexiconLines,
      Iterable<String> unfilteredRuleLines, Iterable<String> dependencyFeatures,
      Set<String> posTagSet, boolean allowComposition) {
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
    DiscreteFactor syntacticDistribution = CcgParser.buildSyntacticDistribution(syntacticCategories, binaryRules, allowComposition);
    VariableNumMap leftSyntaxVar = syntacticDistribution.getVars().getVariablesByName(CcgParser.LEFT_SYNTAX_VAR_NAME);
    VariableNumMap rightSyntaxVar = syntacticDistribution.getVars().getVariablesByName(CcgParser.RIGHT_SYNTAX_VAR_NAME);
    VariableNumMap parentSyntaxVar = syntacticDistribution.getVars().getVariablesByName(CcgParser.PARENT_SYNTAX_VAR_NAME);
    IndicatorLogLinearFactor parametricSyntacticDistribution = new IndicatorLogLinearFactor(
        syntacticDistribution.getVars(), syntacticDistribution);

    // Build the terminal distribution. This maps word sequences to
    // CCG categories, with one possible mapping per entry in the
    // lexicon.
    DiscreteVariable wordType = new DiscreteVariable("words", words.items());
    DiscreteVariable ccgCategoryType = new DiscreteVariable("ccgCategory", categories.items());

    VariableNumMap terminalVar = VariableNumMap.singleton(0, "words", wordType);
    VariableNumMap ccgCategoryVar = VariableNumMap.singleton(1, "ccgCategory", ccgCategoryType);
    VariableNumMap terminalWordVars = VariableNumMap.unionAll(terminalVar, ccgCategoryVar);

    TableFactorBuilder terminalBuilder = new TableFactorBuilder(terminalWordVars, SparseTensorBuilder.getFactory());
    for (String lexiconLine : lexiconLines) {
      LexiconEntry lexiconEntry = LexiconEntry.parseLexiconEntry(lexiconLine);
      terminalBuilder.setWeight(terminalWordVars.outcomeArrayToAssignment(lexiconEntry.getWords(),
          lexiconEntry.getCategory()), 1.0);
    }
    ParametricFactor terminalParametricFactor = new IndicatorLogLinearFactor(terminalWordVars,
        terminalBuilder.build());

    // Build a distribution over parts of speech and syntactic categories, 
    // for smoothing the terminal distribution. 
    posTagSet = posTagSet != null ? posTagSet : Sets.newHashSet(DEFAULT_POS_TAG);
    DiscreteVariable posType = new DiscreteVariable("pos", posTagSet);
    DiscreteVariable ccgSyntaxType = leftSyntaxVar.getDiscreteVariables().get(0);
    VariableNumMap posVar = VariableNumMap.singleton(0, "pos", posType);
    VariableNumMap terminalSyntaxVar = VariableNumMap.singleton(1, "terminalSyntax", ccgSyntaxType);
    VariableNumMap terminalPosVars = VariableNumMap.unionAll(posVar, terminalSyntaxVar);
    ParametricFactor terminalPosParametricFactor = new DenseIndicatorLogLinearFactor(terminalPosVars);

    // Create variables for representing the CCG parser's dependency structures.  
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

    VariableNumMap semanticHeadVar = VariableNumMap.singleton(0, "semanticHead", semanticPredicateType);
    VariableNumMap semanticArgNumVar = VariableNumMap.singleton(1, "semanticArgNum", argumentNums);
    VariableNumMap semanticArgVar = VariableNumMap.singleton(2, "semanticArg", semanticPredicateType);
    VariableNumMap vars = VariableNumMap.unionAll(semanticHeadVar, semanticArgNumVar, semanticArgVar);
    
    // Create features over dependency structures.
    ParametricFactor dependencyIndicatorFactor = new DenseIndicatorLogLinearFactor(vars);
    ParametricFactor dependencyParametricFactor;
    if (dependencyFeatures != null) {
      DiscreteVariable dependencyFeatureVarType = new DiscreteVariable("dependencyFeatures", 
          StringUtils.readColumnFromDelimitedLines(dependencyFeatures, 3, ","));
      System.out.println(dependencyFeatureVarType.getValues());
      VariableNumMap dependencyFeatureVar = VariableNumMap.singleton(3, 
          "dependencyFeatures", dependencyFeatureVarType);
      VariableNumMap featureFactorVars = vars.union(dependencyFeatureVar);
      
      List<Function<String, ?>> converters = Lists.newArrayList();
      converters.add(Functions.<String>identity());
      converters.add(new Function<String, Integer>() {
        public Integer apply(String input) {
          return Integer.parseInt(input);
        }
      });
      converters.add(Functions.<String>identity());
      converters.add(Functions.<String>identity());
      TableFactor dependencyFeatureTable = TableFactor.fromDelimitedFile(featureFactorVars, 
          converters, dependencyFeatures, ",", true);
      DiscreteLogLinearFactor dependencyFeatureFactor = new DiscreteLogLinearFactor(vars,
          dependencyFeatureVar, dependencyFeatureTable);
      
      dependencyParametricFactor = new CombiningParametricFactor(vars, 
          Arrays.asList(INPUT_DEPENDENCY_PARAMETERS, INDICATOR_DEPENDENCY_PARAMETERS), 
          Arrays.asList(dependencyFeatureFactor, dependencyIndicatorFactor));
    } else {
      dependencyParametricFactor = dependencyIndicatorFactor; 
    }
    
    // Create features over argument distances for each predicate.
    VariableNumMap distancePredicateVars = semanticHeadVar.union(semanticArgNumVar);
    VariableNumMap wordDistanceVar = VariableNumMap.singleton(2, "wordDistance", CcgParser.wordDistanceVarType);
    VariableNumMap puncDistanceVar = VariableNumMap.singleton(2, "puncDistance", CcgParser.puncDistanceVarType);
    VariableNumMap verbDistanceVar = VariableNumMap.singleton(2, "verbDistance", CcgParser.verbDistanceVarType);
    ParametricFactor wordDistanceFactor = new DenseIndicatorLogLinearFactor(distancePredicateVars.union(wordDistanceVar));
    ParametricFactor puncDistanceFactor = new DenseIndicatorLogLinearFactor(distancePredicateVars.union(puncDistanceVar));
    ParametricFactor verbDistanceFactor = new DenseIndicatorLogLinearFactor(distancePredicateVars.union(verbDistanceVar));
    Set<String> puncTagSet = DEFAULT_PUNC_TAGS;
    Set<String> verbTagSet = DEFAULT_VERB_TAGS;
    
    // Create root syntax factor.
    DiscreteLogLinearFactor parametricRootDistribution = DiscreteLogLinearFactor
        .createIndicatorFactor(leftSyntaxVar);

    // Create features over unary rules.
    DiscreteFactor unaryRuleDistribution = CcgParser.buildUnaryRuleDistribution(unaryRules, 
        leftSyntaxVar.getDiscreteVariables().get(0));
    VariableNumMap unaryRuleInputVar = unaryRuleDistribution.getVars().getVariablesByName(CcgParser.UNARY_RULE_INPUT_VAR_NAME);
    VariableNumMap unaryRuleVar = unaryRuleDistribution.getVars().getVariablesByName(CcgParser.UNARY_RULE_VAR_NAME);
    IndicatorLogLinearFactor parametricUnaryRuleDistribution = new IndicatorLogLinearFactor(
        unaryRuleInputVar.union(unaryRuleVar), unaryRuleDistribution);

    return new ParametricCcgParser(terminalVar, ccgCategoryVar, terminalParametricFactor,
        posVar, terminalSyntaxVar, terminalPosParametricFactor, semanticHeadVar, semanticArgNumVar,
        semanticArgVar, dependencyParametricFactor, wordDistanceVar, wordDistanceFactor, 
        puncDistanceVar, puncDistanceFactor, puncTagSet, verbDistanceVar,
        verbDistanceFactor, verbTagSet, leftSyntaxVar, rightSyntaxVar, parentSyntaxVar,
        parametricSyntacticDistribution, unaryRuleInputVar, unaryRuleVar, 
        parametricUnaryRuleDistribution, leftSyntaxVar, parametricRootDistribution);
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

  /**
   * Gets a new all-zero parameter vector.
   * 
   * @return
   */
  public SufficientStatistics getNewSufficientStatistics() {
    SufficientStatistics terminalParameters = terminalFamily.getNewSufficientStatistics();
    SufficientStatistics terminalPosParameters = terminalPosFamily.getNewSufficientStatistics();
    SufficientStatistics dependencyParameters = dependencyFamily.getNewSufficientStatistics();
    SufficientStatistics wordDistanceParameters = wordDistanceFamily.getNewSufficientStatistics();
    SufficientStatistics puncDistanceParameters = puncDistanceFamily.getNewSufficientStatistics();
    SufficientStatistics verbDistanceParameters = verbDistanceFamily.getNewSufficientStatistics();
    SufficientStatistics syntaxParameters = syntaxFamily.getNewSufficientStatistics();
    SufficientStatistics unaryRuleParameters = unaryRuleFamily.getNewSufficientStatistics();
    SufficientStatistics rootSyntaxParameters = rootSyntaxFamily.getNewSufficientStatistics();

    return new ListSufficientStatistics(
        Arrays.asList(TERMINAL_PARAMETERS, TERMINAL_POS_PARAMETERS, 
            DEPENDENCY_PARAMETERS, WORD_DISTANCE_PARAMETERS, PUNC_DISTANCE_PARAMETERS, VERB_DISTANCE_PARAMETERS, 
            SYNTAX_PARAMETERS, UNARY_RULE_PARAMETERS, ROOT_SYNTAX_PARAMETERS),
        Arrays.asList(terminalParameters, terminalPosParameters, dependencyParameters, wordDistanceParameters,
            puncDistanceParameters, verbDistanceParameters, syntaxParameters, unaryRuleParameters,
            rootSyntaxParameters));
  }

  /**
   * Instantiates a {@code CcgParser} whose probability distributions
   * are derived from {@code parameters}.
   * 
   * @param parameters
   * @return
   */
  public CcgParser getModelFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    DiscreteFactor terminalDistribution = terminalFamily.getModelFromParameters(
        parameterList.getStatisticByName(TERMINAL_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor terminalPosDistribution = terminalPosFamily.getModelFromParameters(
        parameterList.getStatisticByName(TERMINAL_POS_PARAMETERS)).coerceToDiscrete();
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
    DiscreteFactor rootSyntaxDistribution = rootSyntaxFamily.getModelFromParameters(
        parameterList.getStatisticByName(ROOT_SYNTAX_PARAMETERS)).coerceToDiscrete();

    return new CcgParser(terminalVar, ccgCategoryVar, terminalDistribution,
        terminalPosVar, terminalSyntaxVar, terminalPosDistribution, 
        dependencyHeadVar, dependencyArgNumVar, dependencyArgVar, dependencyDistribution,
        wordDistanceVar, wordDistanceDistribution, puncDistanceVar, puncDistanceDistribution,
        puncTagSet, verbDistanceVar, verbDistanceDistribution, verbTagSet,
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, syntaxDistribution, 
        unaryRuleInputVar, unaryRuleVar, unaryRuleDistribution, rootSyntaxVar, 
        rootSyntaxDistribution);
  }

  /**
   * Increments {@code gradient} by
   * {@code count * features(dependency)} for all dependency
   * structures in {@code dependencies}.
   * 
   * @param gradient
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
      Assignment predicateAssignment = Assignment.unionAll(
          dependencyHeadVar.outcomeArrayToAssignment(dependency.getHead()),
          dependencyArgNumVar.outcomeArrayToAssignment(dependency.getArgIndex()));
      Assignment assignment = predicateAssignment.union(
          dependencyArgVar.outcomeArrayToAssignment(dependency.getObject()));

      dependencyFamily.incrementSufficientStatisticsFromAssignment(dependencyGradient, assignment, count);
      
      // Update distance parameters.
      int headWordIndex = dependency.getHeadWordIndex();
      int objectWordIndex = dependency.getObjectWordIndex();
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
      double count) {
    SufficientStatistics syntaxGradient = gradient.coerceToList().getStatisticByName(SYNTAX_PARAMETERS);
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
      
      // Recursively increment gradient for rules in subtrees.
      incrementSyntaxSufficientStatistics(gradient, left, count);
      incrementSyntaxSufficientStatistics(gradient, right, count);
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
      HeadedSyntacticCategory category, double count) {
    SufficientStatistics rootParameters = parameters.coerceToList()
        .getStatisticByName(ROOT_SYNTAX_PARAMETERS);
    
    Assignment assignment = rootSyntaxVar.outcomeArrayToAssignment(category);
    rootSyntaxFamily.incrementSufficientStatisticsFromAssignment(rootParameters, assignment, count);
  }

  /**
   * Increments {@code gradient} by
   * {@code count * features(lexiconEntry)} for all lexicon entries in
   * {@code lexiconEntries}.
   * 
   * @param gradient
   * @param lexiconEntries
   * @param posTags
   * @param count
   */
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      List<LexiconEntry> lexiconEntries, List<String> posTags, double count) {
    Preconditions.checkArgument(lexiconEntries.size() == posTags.size());
    SufficientStatistics terminalGradient = gradient.coerceToList().getStatisticByName(TERMINAL_PARAMETERS);
    SufficientStatistics terminalPosGradient = gradient.coerceToList().getStatisticByName(TERMINAL_POS_PARAMETERS);
    int numEntries = lexiconEntries.size();
    for (int i = 0; i < numEntries; i++) {
      LexiconEntry lexiconEntry = lexiconEntries.get(i);
      Assignment assignment = Assignment.unionAll(
          terminalVar.outcomeArrayToAssignment(lexiconEntry.getWords()),
          ccgCategoryVar.outcomeArrayToAssignment(lexiconEntry.getCategory()));
      terminalFamily.incrementSufficientStatisticsFromAssignment(terminalGradient, assignment, count);
      
      Assignment posAssignment = terminalPosVar.outcomeArrayToAssignment(posTags.get(i)).union(
          terminalSyntaxVar.outcomeArrayToAssignment(lexiconEntry.getCategory().getSyntax()));
      terminalPosFamily.incrementSufficientStatisticsFromAssignment(terminalPosGradient,
          posAssignment, count);
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
    // Update the dependency structure parameters, including distance parameters.
    incrementDependencySufficientStatistics(gradient, parse.getSpannedPosTags(),
        parse.getAllDependencies(), count);
    // Update syntactic combination parameters. (Both unary and binary rules)
    incrementSyntaxSufficientStatistics(gradient, parse, count);
    incrementRootSyntaxSufficientStatistics(gradient, parse.getHeadedSyntacticCategory(), count);
    // Update terminal distribution parameters.
    incrementLexiconSufficientStatistics(gradient, parse.getSpannedLexiconEntries(),
        parse.getSpannedPosTagsByLexiconEntry(), count);
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
    sb.append(terminalFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_PARAMETERS), numFeatures));
    sb.append(terminalPosFamily.getParameterDescription(
        parameterList.getStatisticByName(TERMINAL_POS_PARAMETERS), numFeatures));
    sb.append(syntaxFamily.getParameterDescription(
        parameterList.getStatisticByName(SYNTAX_PARAMETERS), numFeatures));
    sb.append(unaryRuleFamily.getParameterDescription(
        parameterList.getStatisticByName(UNARY_RULE_PARAMETERS), numFeatures));
    sb.append(dependencyFamily.getParameterDescription(
        parameterList.getStatisticByName(DEPENDENCY_PARAMETERS), numFeatures));
    
    sb.append(wordDistanceFamily.getParameterDescription(
        parameterList.getStatisticByName(WORD_DISTANCE_PARAMETERS), numFeatures));
    sb.append(puncDistanceFamily.getParameterDescription(
        parameterList.getStatisticByName(PUNC_DISTANCE_PARAMETERS), numFeatures));
    sb.append(verbDistanceFamily.getParameterDescription(
        parameterList.getStatisticByName(VERB_DISTANCE_PARAMETERS), numFeatures));
    
    sb.append(rootSyntaxFamily.getParameterDescription(
        parameterList.getStatisticByName(ROOT_SYNTAX_PARAMETERS), numFeatures));

    return sb.toString();
  }
}
