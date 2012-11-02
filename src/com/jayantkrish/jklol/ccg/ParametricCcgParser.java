package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.CcgCategory.Argument;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Parameterized CCG grammar. This class instantiates CCG parsers given
 * parameter vectors. This class parameterizes CCGs with probabilities for:
 * <ul>
 * <li>Lexicon entries.
 * <li>Dependency structures (i.e., semantic dependencies).
 * </ul>
 * 
 * @author jayant
 */
public class ParametricCcgParser {

  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final ParametricFactor terminalFamily;

  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
  private final ParametricFactor dependencyFamily;
  
  private final List<CcgBinaryRule> binaryRules;

  private final String TERMINAL_PARAMETERS = "terminals";
  private final String DEPENDENCY_PARAMETERS = "dependencies";

  public ParametricCcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      ParametricFactor terminalFamily, VariableNumMap dependencyHeadVar,
      VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      ParametricFactor dependencyFamily, List<CcgBinaryRule> binaryRules) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalFamily = Preconditions.checkNotNull(terminalFamily);
    this.dependencyHeadVar = Preconditions.checkNotNull(dependencyHeadVar);
    this.dependencyArgNumVar = Preconditions.checkNotNull(dependencyArgNumVar);
    this.dependencyArgVar = Preconditions.checkNotNull(dependencyArgVar);
    this.dependencyFamily = Preconditions.checkNotNull(dependencyFamily);
    this.binaryRules = ImmutableList.copyOf(binaryRules);
  }

  /**
   * Produces a parametric CCG parser from a lexicon, represented a series of
   * phrase/CCG category mappings. Each mapping is given as a comma separated
   * string, whose first value is the phrase and whose second value is the CCG
   * category string.
   * 
   * @param lexiconLines
   * @return
   */
  public static ParametricCcgParser parseFromLexicon(Iterable<String> unfilteredLexiconLines,
      Iterable<String> unfilteredRuleLines) {    
    List<CcgBinaryRule> binaryRules = Lists.newArrayList();
    for (String line : unfilteredRuleLines) {
      if (!line.startsWith("#")) {
        binaryRules.add(CcgBinaryRule.parseFrom(line));
      }
    }

    // Remove comments, which are lines that begin with "#".
    List<String> lexiconLines = Lists.newArrayList();
    for (String line : unfilteredLexiconLines) {
      if (!line.startsWith("#")) {
        lexiconLines.add(line);
      }
    }
    
    // Parse out all of the categories, words, and semanticPredicates from the
    // lexicon.
    IndexedList<CcgCategory> categories = IndexedList.create();
    IndexedList<List<String>> words = IndexedList.create();
    IndexedList<String> semanticPredicates = IndexedList.create();
    Map<Integer, Integer> maxNumArgs = Maps.newHashMap();
    for (String lexiconLine : lexiconLines) {
      // Create the CCG category.
      LexiconEntry lexiconEntry = LexiconEntry.parseLexiconEntry(lexiconLine);
      words.add(lexiconEntry.getWords());
      categories.add(lexiconEntry.getCategory());

      // Store the heads of the dependencies as semantic predicates.
      addArgumentsToPredicateList(lexiconEntry.getCategory().getHeads(), semanticPredicates);
      // Store any predicates from the subjects of the dependency structures.
      addSubjectsToPredicateList(lexiconEntry.getCategory().getSubjects(),
          lexiconEntry.getCategory().getArgumentNumbers(), semanticPredicates, maxNumArgs);
      // Store any predicates from the objects of the dependency structures.
      addArgumentsToPredicateList(lexiconEntry.getCategory().getObjects(), semanticPredicates);
    }

    // Build the terminal distribution. This maps word sequences to
    // CCG categories, with one possible mapping per entry in the lexicon.
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
    ParametricFactor terminalParametricFactor = new IndicatorLogLinearFactor(vars, terminalBuilder.build());

    // Build the dependency distribution.
    DiscreteVariable semanticPredicateType = new DiscreteVariable("semanticPredicates", semanticPredicates.items());
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

    ParametricFactor dependencyParametricFactor = new DenseIndicatorLogLinearFactor(vars);

    return new ParametricCcgParser(terminalVar, ccgCategoryVar, terminalParametricFactor,
        semanticHeadVar, semanticArgNumVar, semanticArgVar, dependencyParametricFactor, 
        binaryRules);
  }

  private static void addArgumentsToPredicateList(Collection<Argument> arguments,
      IndexedList<String> semanticPredicates) {
    for (Argument arg : arguments) {
      if (arg.hasPredicate()) {
        semanticPredicates.add(arg.getPredicate());
      }
    }
  }

  /**
   * Adds the predicates in {@code subjects} to {@code semanticPredicates},
   * while simultaneously counting the argument numbers each predicate appears
   * with and maintaining the maximum number seen in {@code maxNumArgs}.
   * 
   * @param subjects
   * @param argNums
   * @param semanticPredicates
   * @param maxNumArgs
   */
  private static void addSubjectsToPredicateList(List<Argument> subjects, List<Integer> argNums,
      IndexedList<String> semanticPredicates, Map<Integer, Integer> maxNumArgs) {
    Preconditions.checkArgument(subjects.size() == argNums.size());
    for (int i = 0; i < subjects.size(); i++) {
      Argument subject = subjects.get(i);
      int argNum = argNums.get(i);
      if (subject.hasPredicate()) {
        semanticPredicates.add(subject.getPredicate());
        int predicateIndex = semanticPredicates.getIndex(subject.getPredicate());

        if (!maxNumArgs.containsKey(predicateIndex)) {
          maxNumArgs.put(predicateIndex, argNum);
        } else {
          maxNumArgs.put(predicateIndex, Math.max(maxNumArgs.get(predicateIndex), argNum));
        }
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
   * Instantiates a {@code CcgParser} whose probability distributions are
   * derived from {@code parameters}.
   * 
   * @param parameters
   * @return
   */
  public CcgParser getParserFromParameters(SufficientStatistics parameters) {
    ListSufficientStatistics parameterList = parameters.coerceToList();
    DiscreteFactor terminalDistribution = terminalFamily.getFactorFromParameters(
        parameterList.getStatisticByName(TERMINAL_PARAMETERS)).coerceToDiscrete();
    DiscreteFactor dependencyDistribution = dependencyFamily.getFactorFromParameters(
        parameterList.getStatisticByName(DEPENDENCY_PARAMETERS)).coerceToDiscrete();

    return new CcgParser(terminalVar, ccgCategoryVar, terminalDistribution,
        dependencyHeadVar, dependencyArgNumVar, dependencyArgVar, dependencyDistribution,
        binaryRules);
  }

  /**
   * Increments {@code gradient} by {@code count * features(dependency)} for all
   * dependency structures in {@code dependencies}.
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
   * Increments {@code gradient} by {@code count * features(lexiconEntry)} for
   * all lexicon entries in {@code lexiconEntries}.
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
   * Increments {@code gradient} by {@code count * features(parse)}, where
   * {@code features} is a function from CCG parses to a feature vectors.
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
   * highest-weighted (in absolute value) features of {@code parameters}.
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
   * Returns true if {@code example} can be used to train this model family.
   * 
   * @param example
   * @return
   */
  public boolean isValidExample(CcgExample example) {
    Set<DependencyStructure> dependencies = example.getDependencies();
    for (DependencyStructure dependency : dependencies) {
      if (!isValidDependency(dependency)) {
        return false;
      }
    }
    
    if (example.hasLexiconEntries()) {
      for (LexiconEntry lexiconEntry : example.getLexiconEntries()) {
        if (!isValidLexiconEntry(lexiconEntry)) {
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
