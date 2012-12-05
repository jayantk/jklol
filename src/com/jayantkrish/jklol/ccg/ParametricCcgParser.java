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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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

  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
  private final ParametricFactor dependencyFamily;

  private final List<CcgBinaryRule> binaryRules;
  private final List<CcgUnaryRule> unaryRules;
  
  private final boolean allowComposition;

  /**
   * Name of the parameter vector governing lexicon entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";

  /**
   * Name of the parameter vector governing dependency structures.
   */
  public static final String DEPENDENCY_PARAMETERS = "dependencies";
  
  public static final String INPUT_DEPENDENCY_PARAMETERS = "inputFeatures";
  public static final String INDICATOR_DEPENDENCY_PARAMETERS = "indicatorFeatures";

  public ParametricCcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      ParametricFactor terminalFamily, VariableNumMap dependencyHeadVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      ParametricFactor dependencyFamily, List<CcgBinaryRule> binaryRules, 
      List<CcgUnaryRule> unaryRules, boolean allowComposition) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalFamily = Preconditions.checkNotNull(terminalFamily);
    this.dependencyHeadVar = Preconditions.checkNotNull(dependencyHeadVar);
    this.dependencyArgNumVar = Preconditions.checkNotNull(dependencyArgNumVar);
    this.dependencyArgVar = Preconditions.checkNotNull(dependencyArgVar);
    this.dependencyFamily = Preconditions.checkNotNull(dependencyFamily);
    this.binaryRules = ImmutableList.copyOf(binaryRules);
    this.unaryRules = ImmutableList.copyOf(unaryRules);
    
    this.allowComposition = allowComposition;
  }

  /**
   * Produces a parametric CCG parser from a lexicon, represented a
   * series of phrase/CCG category mappings. Each mapping is given as
   * a comma separated string, whose first value is the phrase and
   * whose second value is the CCG category string. Lines beginning
   * with # are interpreted as comments and skipped over.
   * 
   * @param lexiconLines
   * @return
   */
  public static ParametricCcgParser parseFromLexicon(Iterable<String> unfilteredLexiconLines,
      Iterable<String> unfilteredRuleLines, Iterable<String> dependencyFeatures,
      boolean allowComposition) {
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
    for (String lexiconLine : lexiconLines) {
      // Create the CCG category.
      LexiconEntry lexiconEntry = LexiconEntry.parseLexiconEntry(lexiconLine);
      words.add(lexiconEntry.getWords());
      categories.add(lexiconEntry.getCategory());

      // Store the values of any assignments as semantic predicates.
      semanticPredicates.addAll(Iterables.concat(lexiconEntry.getCategory().getAssignment()));
      // Store any predicates from the subjects of the dependency
      // structures.
      addSubjectsToPredicateList(lexiconEntry.getCategory().getSubjects(),
          lexiconEntry.getCategory().getArgumentNumbers(), semanticPredicates, maxNumArgs);
    }
    
    // Add any predicates from binary and unary rules.
    for (CcgBinaryRule rule : binaryRules) {
      addSubjectsToPredicateList(rule.getSubjects(), rule.getArgumentNumbers(), semanticPredicates,
          maxNumArgs);
    }
    for (CcgUnaryRule rule : unaryRules) {
      addSubjectsToPredicateList(rule.getSubjects(), rule.getArgumentNumbers(), semanticPredicates,
          maxNumArgs);
    }

    // Build the terminal distribution. This maps word sequences to
    // CCG categories, with one possible mapping per entry in the
    // lexicon.
    DiscreteVariable ccgCategoryType = new DiscreteVariable("ccgCategory", categories.items());
    DiscreteVariable wordType = new DiscreteVariable("words", words.items());

    VariableNumMap terminalVar = VariableNumMap.singleton(0, "words", wordType);
    VariableNumMap ccgCategoryVar = VariableNumMap.singleton(1, "ccgCategory", ccgCategoryType);
    VariableNumMap vars = terminalVar.union(ccgCategoryVar);
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    for (String lexiconLine : lexiconLines) {
      LexiconEntry lexiconEntry = LexiconEntry.parseLexiconEntry(lexiconLine);
      terminalBuilder.setWeight(vars.outcomeArrayToAssignment(lexiconEntry.getWords(),
          lexiconEntry.getCategory()), 1.0);
    }
    ParametricFactor terminalParametricFactor = new IndicatorLogLinearFactor(vars,
        terminalBuilder.build());

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
    vars = VariableNumMap.unionAll(semanticHeadVar, semanticArgNumVar, semanticArgVar);
    
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

    return new ParametricCcgParser(terminalVar, ccgCategoryVar, terminalParametricFactor,
        semanticHeadVar, semanticArgNumVar, semanticArgVar, dependencyParametricFactor,
        binaryRules, unaryRules, allowComposition);
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
    SufficientStatistics dependencyParameters = dependencyFamily.getNewSufficientStatistics();

    return new ListSufficientStatistics(Arrays.asList(TERMINAL_PARAMETERS, DEPENDENCY_PARAMETERS),
        Arrays.asList(terminalParameters, dependencyParameters));
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
    DiscreteFactor dependencyDistribution = dependencyFamily.getModelFromParameters(
        parameterList.getStatisticByName(DEPENDENCY_PARAMETERS)).coerceToDiscrete();

    return new CcgParser(terminalVar, ccgCategoryVar, terminalDistribution,
        dependencyHeadVar, dependencyArgNumVar, dependencyArgVar, dependencyDistribution,
        binaryRules, unaryRules, allowComposition);
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
      Collection<DependencyStructure> dependencies, double count) {
    SufficientStatistics dependencyGradient = gradient.coerceToList().getStatisticByName(DEPENDENCY_PARAMETERS);
    for (DependencyStructure dependency : dependencies) {
      Assignment assignment = Assignment.unionAll(
          dependencyHeadVar.outcomeArrayToAssignment(dependency.getHead()),
          dependencyArgNumVar.outcomeArrayToAssignment(dependency.getArgIndex()),
          dependencyArgVar.outcomeArrayToAssignment(dependency.getObject()));

      dependencyFamily.incrementSufficientStatisticsFromAssignment(dependencyGradient, assignment, count);
    }
  }

  /**
   * Increments {@code gradient} by
   * {@code count * features(lexiconEntry)} for all lexicon entries in
   * {@code lexiconEntries}.
   * 
   * @param gradient
   * @param dependencies
   * @param count
   */
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      Collection<LexiconEntry> lexiconEntries, double count) {
    SufficientStatistics terminalGradient = gradient.coerceToList().getStatisticByName(TERMINAL_PARAMETERS);
    for (LexiconEntry lexiconEntry : lexiconEntries) {
      Assignment assignment = Assignment.unionAll(
          terminalVar.outcomeArrayToAssignment(lexiconEntry.getWords()),
          ccgCategoryVar.outcomeArrayToAssignment(lexiconEntry.getCategory()));
      terminalFamily.incrementSufficientStatisticsFromAssignment(terminalGradient, assignment, count);
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
    // Update the dependency structure parameters.
    incrementDependencySufficientStatistics(gradient, parse.getAllDependencies(), count);
    // Update terminal distribution parameters.
    incrementLexiconSufficientStatistics(gradient, parse.getSpannedLexiconEntries(), count);
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
    sb.append(dependencyFamily.getParameterDescription(
        parameterList.getStatisticByName(DEPENDENCY_PARAMETERS), numFeatures));
    return sb.toString();
  }

  /**
   * Returns true if {@code example} can be used to train this model
   * family.
   * 
   * @param example
   * @return
   */
  public boolean isValidExample(CcgExample example) {
    Set<DependencyStructure> dependencies = example.getDependencies();
    for (DependencyStructure dependency : dependencies) {
      if (!isValidDependency(dependency)) {
        System.out.println(dependency);
        return false;
      }
    }

    if (example.hasLexiconEntries()) {
      for (LexiconEntry lexiconEntry : example.getLexiconEntries()) {
        if (!isValidLexiconEntry(lexiconEntry)) {
          System.out.println(lexiconEntry);
          return false;
        }
      }
    }
    return true;
  }

  public boolean isValidDependency(DependencyStructure dependency) {
    return dependencyHeadVar.isValidOutcomeArray(dependency.getHead()) &&
        dependencyArgNumVar.isValidOutcomeArray(dependency.getArgIndex()) &&
        dependencyArgVar.isValidOutcomeArray(dependency.getObject());
  }

  public boolean isValidLexiconEntry(LexiconEntry lexiconEntry) {
    return terminalVar.isValidOutcomeArray(lexiconEntry.getWords()) &&
        ccgCategoryVar.isValidOutcomeArray(lexiconEntry.getCategory());
  }
}
