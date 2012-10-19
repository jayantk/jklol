package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
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
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;

  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
  private final DiscreteFactor dependencyDistribution;
  
  // Pull out the weights and variable types from the dependency 
  // structure distribution for efficiency.
  private final DiscreteVariable dependencyHeadType;
  private final DiscreteVariable dependencyArgNumType;
  private final DiscreteVariable dependencyArgType;
  private final Tensor dependencyTensor;

  public CcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap dependencyHeadVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyArgVar, DiscreteFactor dependencyDistribution) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);

    Preconditions.checkArgument(dependencyDistribution.getVars().equals(
        VariableNumMap.unionAll(dependencyHeadVar, dependencyArgNumVar, dependencyArgVar)));
    Preconditions.checkArgument(dependencyHeadVar.getOnlyVariableNum() < dependencyArgNumVar.getOnlyVariableNum());
    Preconditions.checkArgument(dependencyArgNumVar.getOnlyVariableNum() < dependencyArgVar.getOnlyVariableNum());
    this.dependencyHeadVar = Preconditions.checkNotNull(dependencyHeadVar);
    this.dependencyArgNumVar = Preconditions.checkNotNull(dependencyArgNumVar);
    this.dependencyArgVar = Preconditions.checkNotNull(dependencyArgVar);
    this.dependencyDistribution = Preconditions.checkNotNull(dependencyDistribution);
    
    this.dependencyHeadType = dependencyHeadVar.getDiscreteVariables().get(0);
    this.dependencyArgNumType = dependencyArgNumVar.getDiscreteVariables().get(0);
    this.dependencyArgType = dependencyArgVar.getDiscreteVariables().get(0);
    this.dependencyTensor = dependencyDistribution.getWeights();
  }

  public List<CcgParse> beamSearch(List<String> terminals, int beamSize) {
    return beamSearch(terminals, beamSize, new NullLogFunction());
  }

  /**
   * Performs a beam search to find the best CCG parses of {@code terminals}.
   * Note that this is an approximate inference strategy, and the returned
   * parses may not be the best parses if at any point during the search
   * more than {@code beamSize} parse trees exist for a span of the sentence.
   * 
   * @param terminals
   * @param beamSize
   * @param log
   * @return {@code beamSize} best parses for {@code terminals}.
   */
  public List<CcgParse> beamSearch(List<String> terminals, int beamSize, LogFunction log) {
    CcgChart chart = new CcgChart(terminals, beamSize);

    log.startTimer("ccg_parse/initialize_chart");
    initializeChart(terminals, chart);
    log.stopTimer("ccg_parse/initialize_chart");

    // Construct a tree from the nonterminals.
    log.startTimer("ccg_parse/calculate_inside_beam");
    int chartSize = chart.size();
    for (int spanSize = 1; spanSize < chartSize; spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chartSize; spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart, log);
      }
    }
    log.stopTimer("ccg_parse/calculate_inside_beam");

    int numParses = Math.min(beamSize, chart.getNumChartEntriesForSpan(0, chart.size() - 1));

    return chart.decodeBestParsesForSpan(0, chart.size() - 1, numParses);
  }
  
  /**
   * Initializes the parse chart with entries from the CCG lexicon for {@code terminals}.
   * 
   * @param terminals
   * @param chart
   */
  private void initializeChart(List<String> terminals, CcgChart chart) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());

    int ccgCategoryVarNum = ccgCategoryVar.getOnlyVariableNum();
    for (int i = 0; i < terminals.size(); i++) {
      for (int j = i; j < terminals.size(); j++) {
        if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
          Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, j + 1));
          Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
          while (iterator.hasNext()) {
            Outcome bestOutcome = iterator.next();
            CcgCategory category = (CcgCategory) bestOutcome.getAssignment().getValue(ccgCategoryVarNum);

            chart.addChartEntryForTerminalSpan(category, bestOutcome.getProbability(), i, j);
            // System.out.println(i + "." + j + " : " + category + " " + bestOutcome.getProbability());
          }
        }
      }
    }
  }

  private void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart, LogFunction log) {
    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j is for forward compatibility for skipping terminal symbols.
      for (int j = i + 1; j < i + 2; j++) {
        ChartEntry[] leftTrees = chart.getChartEntriesForSpan(spanStart, spanStart + i);
        double[] leftProbs = chart.getChartEntryProbsForSpan(spanStart, spanStart + i);
        int numLeftTrees = chart.getNumChartEntriesForSpan(spanStart, spanStart + i);
        Multimap<SyntacticCategory, Integer> leftTypes = aggregateBySyntacticType(leftTrees, numLeftTrees);
        Multimap<SyntacticCategory, Integer> leftArguments = aggregateByArgumentType(leftTrees, numLeftTrees, Direction.RIGHT);

        ChartEntry[] rightTrees = chart.getChartEntriesForSpan(spanStart + j, spanEnd);
        double[] rightProbs = chart.getChartEntryProbsForSpan(spanStart + j, spanEnd);
        int numRightTrees = chart.getNumChartEntriesForSpan(spanStart + j, spanEnd);
        Multimap<SyntacticCategory, Integer> rightTypes = aggregateBySyntacticType(rightTrees, numRightTrees);
        Multimap<SyntacticCategory, Integer> rightArguments = aggregateByArgumentType(rightTrees, numRightTrees, Direction.LEFT);

        // Do CCG right application. (The category on the left is a function.)
        for (SyntacticCategory leftArgument : leftArguments.keySet()) {
          for (SyntacticCategory rightType : rightTypes.keySet()) {
            if (leftArgument.isUnifiableWith(rightType)) {
              for (Integer leftIndex : leftArguments.get(leftArgument)) {
                ChartEntry leftRoot = leftTrees[leftIndex];
                double leftProb = leftProbs[leftIndex];
                for (Integer rightIndex : rightTypes.get(rightType)) {
                  ChartEntry rightRoot = rightTrees[rightIndex];
                  double rightProb = rightProbs[rightIndex];

                  ChartEntry result = apply(leftRoot, rightRoot, Direction.RIGHT, spanStart, 
                      spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex);
                  if (result != null) {
                    addChartEntry(result, chart, leftProb * rightProb, spanStart, spanEnd);
                  }
                }
              }
            }
          }
        }

        // Do CCG left application. (The category on the right is a function.)
        for (SyntacticCategory rightArgument : rightArguments.keySet()) {
          for (SyntacticCategory leftType : leftTypes.keySet()) {
            if (rightArgument.isUnifiableWith(leftType)) {
              for (Integer leftIndex : leftTypes.get(leftType)) {
                ChartEntry leftRoot = leftTrees[leftIndex];
                double leftProb = leftProbs[leftIndex];
                for (Integer rightIndex : rightArguments.get(rightArgument)) {
                  ChartEntry rightRoot = rightTrees[rightIndex];
                  double rightProb = rightProbs[rightIndex];

                  ChartEntry result = apply(rightRoot, leftRoot, Direction.LEFT, spanStart,
                      spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex);
                  if (result != null) {
                    addChartEntry(result, chart, leftProb * rightProb, spanStart, spanEnd);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private Multimap<SyntacticCategory, Integer> aggregateBySyntacticType(
      ChartEntry[] entries, int numEntries) {
    Multimap<SyntacticCategory, Integer> map = HashMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      map.put(entries[i].getSyntax(), i);
    }
    return map;
  }

  /**
   * Identifies all elements of {@code entries} that accept an argument on
   * {@code direction}, and returns a map from the argument type to the 
   * indexes of chart entries that accept that type. 
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

  /**
   * Computes the {@code keyNum} containing the weight for {@code dep} 
   * in {@code dependencyTensor}.
   * 
   * @param dep
   * @return
   */
  private long dependencyToLong(DependencyStructure dep) {
    int headNum = dependencyHeadType.getValueIndex(dep.getHead());
    int argNumNum = dependencyArgNumType.getValueIndex(dep.getArgIndex());
    int objectNum = dependencyArgType.getValueIndex(dep.getObject());
    return dependencyTensor.dimKeyToKeyNum(new int[] {headNum, argNumNum, objectNum});
  }

  /**
   * Calculates the probability of any new dependencies in {@code result}, then inserts it
   * into {@code chart}.
   * @param result
   * @param chart
   * @param leftRightProb
   * @param spanStart
   * @param spanEnd
   */
  private void addChartEntry(ChartEntry result, CcgChart chart, double leftRightProb, 
      int spanStart, int spanEnd) {
    // Get the probabilities of the generated dependencies.
    double depProb = 1.0;
    for (DependencyStructure dep : result.getDependencies()) {
      long depNum = dependencyToLong(dep);
      depProb *= dependencyTensor.get(depNum);
    }

    chart.addChartEntryForSpan(result, leftRightProb * depProb, spanStart, spanEnd); 
    // System.out.println(rootSpanStart + "." + rootSpanEnd + " " + result.getCategory() + " " + result.getDependencies() + " " + (depProb * leftRightProb)); 
  }
  
  private ChartEntry apply(ChartEntry first, ChartEntry other, Direction direction,
      int leftSpanStart, int leftSpanEnd, int leftIndex, int rightSpanStart, int rightSpanEnd,
      int rightIndex) {
    SyntacticCategory syntax = first.getSyntax();
    if (syntax.isAtomic() || !syntax.acceptsArgumentOn(direction) || 
        !syntax.getArgument().isUnifiableWith(other.getSyntax())) {
      return null;
    }

    Set<IndexedPredicate> newHeads = (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) 
        ? other.getHeads() : first.getHeads();
    Set<Integer> newHeadArgumentNums = (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) 
        ? other.getUnfilledHeads() : first.getUnfilledHeads(); 

    // Resolve semantic dependencies. Fill all dependency slots which require this argument.
    // Return any fully-filled dependencies, while saving partially-filled dependencies for later.
    int argNum = syntax.getArgumentList().size(); 
    UnfilledDependency[] unfilledDependencies = first.getUnfilledDependencies();
    UnfilledDependency[] otherUnfilledDeps = other.getUnfilledDependencies();

    List<DependencyStructure> filledDeps = Lists.newArrayList();
    List<UnfilledDependency> newUnfilledDependencies = Lists.newArrayList();
    for (UnfilledDependency unfilled : unfilledDependencies) {
      // Check if the argument currently being filled matches the argument
      // expected by this dependency.
      if (unfilled.getObjectIndex() == argNum) {
        Set<IndexedPredicate> objects = other.getHeads();
        if (unfilled.hasSubjects()) {
          // After substituting the objects, this dependency is completely filled.
          for (IndexedPredicate head : unfilled.getSubjects()) {
            for (IndexedPredicate object : objects) {
              filledDeps.add(new DependencyStructure(head.getHead(), head.getHeadIndex(), 
                  object.getHead(), object.getHeadIndex(), unfilled.getArgumentIndex()));
            }
          }
        } else {
          // The subject of this dependency has not been filled.
          int subjectIndex = unfilled.getSubjectIndex();
          int argIndex = unfilled.getArgumentIndex();

          for (IndexedPredicate object : objects) {
            newUnfilledDependencies.add(UnfilledDependency.createWithKnownObject(subjectIndex, 
                argIndex, object.getHead(), object.getHeadIndex()));
          }
        }
      } else if (unfilled.getSubjectIndex() == argNum) {
        int otherArgNum = unfilled.getArgumentIndex();
        
        if (unfilled.hasObjects()) {
          for (IndexedPredicate object : unfilled.getObjects()) {
            for (UnfilledDependency otherDep : otherUnfilledDeps) {
              if (otherDep.getObjectIndex() == otherArgNum || otherDep.getSubjectIndex() == otherArgNum) {
                substituteDependencyVariable(otherArgNum, otherDep, object, filledDeps);
              }
            }
          }
        } else {
          // Part of the dependency remains unresolved. Fill what's possible, then propagate
          // the unfilled portions.
          int replacementIndex = unfilled.getObjectIndex();

          for (UnfilledDependency otherDep : otherUnfilledDeps) {
            if (otherDep.getObjectIndex() == otherArgNum || otherDep.getSubjectIndex() == otherArgNum) {
              substituteDependencyVariable(otherArgNum, otherDep, replacementIndex, newUnfilledDependencies);
            }
          }
        }
      } else {
        newUnfilledDependencies.add(unfilled);
      }
    }
    
    if (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) {
      newUnfilledDependencies.addAll(Arrays.asList(other.getUnfilledDependencies()));
    }
    
    // Handle any unfilled head arguments.
    if (newHeadArgumentNums.contains(argNum)) {
      newHeads.addAll(other.getHeads());
    }
    
    DependencyStructure[] filledDepArray = filledDeps.toArray(
        new DependencyStructure[filledDeps.size()]);
    UnfilledDependency[] unfilledDepArray = newUnfilledDependencies.toArray(
        new UnfilledDependency[newUnfilledDependencies.size()]);
    
    return new ChartEntry(syntax.getReturn(), newHeads, newHeadArgumentNums, 
        unfilledDepArray, filledDepArray, leftSpanStart, leftSpanEnd, leftIndex, rightSpanStart,
        rightSpanEnd, rightIndex);
  }

  /*
  private int countFilledDeps(UnfilledDependency[] unfilledDependencies, 
      UnfilledDependency[] otherUnfilledDeps, int argNum) {
    int count = 0;
    for (UnfilledDependency unfilled : unfilledDependencies) {
      // Check if the argument currently being filled matches the argument
      // expected by this dependency.
      if (unfilled.getObjectIndex() == argNum && unfilled.hasSubjects()) {
        count += unfilled.getSubjects().size() * other.getHeads().size();
      } else if (unfilled.getSubjectIndex() == argNum && unfilled.hasObjects()) {
        int otherArgNum = unfilled.getArgumentIndex();
        
        if (unfilled.hasObjects()) {
          for (IndexedPredicate object : unfilled.getObjects()) {
            for (UnfilledDependency otherDep : otherUnfilledDeps) {
              if (otherDep.getObjectIndex() == otherArgNum || otherDep.getSubjectIndex() == otherArgNum) {
                substituteDependencyVariable(otherArgNum, otherDep, object, filledDeps);
              }
            }
          }
        } else {
          // Part of the dependency remains unresolved. Fill what's possible, then propagate
          // the unfilled portions.
          int replacementIndex = unfilled.getObjectIndex();

          for (UnfilledDependency otherDep : otherUnfilledDeps) {
            if (otherDep.getObjectIndex() == otherArgNum || otherDep.getSubjectIndex() == otherArgNum) {
              substituteDependencyVariable(otherArgNum, otherDep, replacementIndex, newUnfilledDependencies);
            }
          }
        }
      } else {
        newUnfilledDependencies.add(unfilled);
      }
    }
  }
  */

  /**
   * Replaces all instances of {@code dependencyVariableNum} in {@code dep} 
   * with the variable given by {@code replacementVariableNum}. 
   * 
   * @param dependencyVariableNum
   * @param dep
   * @param replacementVariableNum
   * @param unfilledDepsAccumulator
   */
  private void substituteDependencyVariable(int dependencyVariableNum, UnfilledDependency dep,
      int replacementVariableNum, List<UnfilledDependency> unfilledDepsAccumulator) {
    UnfilledDependency newDep = dep;
    if (dep.getSubjectIndex() == dependencyVariableNum) {
      newDep = newDep.replaceSubject(replacementVariableNum);
    } else {
      Preconditions.checkState(dep.hasSubjects());
    }
    
    if (dep.getObjectIndex() == dependencyVariableNum) {
      newDep = newDep.replaceObject(replacementVariableNum);
    } else {
      Preconditions.checkState(dep.hasObjects());
    }
    
    unfilledDepsAccumulator.add(newDep);
  }
  
  /**
   * Replaces all instances of {@code dependencyVariableNum} in {@code dep} 
   * with {@code value}, which is a defined predicate. 
   *  
   * @param dependencyVariableNum
   * @param dep
   * @param value
   * @param filledDepsAccumulator
   */
  private void substituteDependencyVariable(int dependencyVariableNum, UnfilledDependency dep,
      IndexedPredicate value, List<DependencyStructure> filledDepsAccumulator) {
    UnfilledDependency newDep = dep;
    Set<IndexedPredicate> values = Sets.newHashSet(value);
    if (dep.getSubjectIndex() == dependencyVariableNum) {
      newDep = newDep.replaceSubject(values);
    }
    
    if (dep.getObjectIndex() == dependencyVariableNum) {
      newDep = newDep.replaceObject(values);
    }
    
    Preconditions.checkState(newDep.hasObjects() && newDep.hasSubjects());
    for (IndexedPredicate head : newDep.getSubjects()) {
      for (IndexedPredicate object : newDep.getObjects()) {
        filledDepsAccumulator.add(new DependencyStructure(head.getHead(), head.getHeadIndex(), 
            object.getHead(), object.getHeadIndex(), newDep.getArgumentIndex()));
      }
    }
  }

  private ChartEntry compose(ChartEntry first, ChartEntry second, Direction direction) {
    return null;
  }
}