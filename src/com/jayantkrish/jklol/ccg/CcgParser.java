package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
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

  public CcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap dependencyHeadVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyArgVar, DiscreteFactor dependencyDistribution) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);

    this.dependencyHeadVar = Preconditions.checkNotNull(dependencyHeadVar);
    this.dependencyArgNumVar = Preconditions.checkNotNull(dependencyArgNumVar);
    this.dependencyArgVar = Preconditions.checkNotNull(dependencyArgVar);
    this.dependencyDistribution = Preconditions.checkNotNull(dependencyDistribution);
  }

  /**
   * Performs a beam search to find the best CCG parses of {@code terminals}.
   * Note that this is an approximate inference strategy, and the returned
   * parses may not be the best parses if at any point during the search
   * more than {@code beamSize} parse trees exist for a span of the sentence.
   * 
   * @param terminals
   * @param beamSize
   * @return {@code beamSize} best parses for {@code terminals}.
   */
  public List<CcgParse> beamSearch(List<String> terminals, int beamSize) {
    CcgChart chart = new CcgChart(terminals, beamSize);

    initializeChart(terminals, chart);

    // Construct a tree from the nonterminals.
    for (int spanSize = 1; spanSize < chart.size(); spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chart.size(); spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart);
      }
    }

    int numParses = Math.min(beamSize, chart.getNumChartEntriesForSpan(0, chart.size() - 1));

    List<CcgParse> parses = Lists.newArrayListWithCapacity(numParses);
    for (int i = numParses - 1; i >= 0; i--) {
      parses.add(chart.decodeParseFromSpan(0, chart.size() - 1, i));
    }
    return parses;
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

  private void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart) {
    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j is for forward compatibility for skipping terminal symbols.
      for (int j = i + 1; j < i + 2; j++) {
        ChartEntry[] leftTrees = chart.getChartEntriesForSpan(spanStart, spanStart + i);
        double[] leftProbs = chart.getChartEntryProbsForSpan(spanStart, spanStart + i);
        int numLeftTrees = chart.getNumChartEntriesForSpan(spanStart, spanStart + i);

        ChartEntry[] rightTrees = chart.getChartEntriesForSpan(spanStart + j, spanEnd);
        double[] rightProbs = chart.getChartEntryProbsForSpan(spanStart + j, spanEnd);
        int numRightTrees = chart.getNumChartEntriesForSpan(spanStart + j, spanEnd);

        for (int leftIndex = 0; leftIndex < numLeftTrees; leftIndex++) {
          ChartEntry leftRoot = leftTrees[leftIndex];
          double leftProb = leftProbs[leftIndex];
          for (int rightIndex = 0; rightIndex < numRightTrees; rightIndex++) {
            ChartEntry rightRoot = rightTrees[rightIndex];
            double rightProb = rightProbs[rightIndex];

            populateChartEntry(leftRoot, spanStart, spanStart + i, leftIndex,
                rightRoot, spanStart + j, spanEnd, rightIndex,
                chart, leftProb * rightProb, spanStart, spanEnd);
          }
        }
      }
    }
  }

  private void populateChartEntry(ChartEntry leftRoot, int leftSpanStart, int leftSpanEnd, int leftIndex,
      ChartEntry rightRoot, int rightSpanStart, int rightSpanEnd, int rightIndex,
      CcgChart chart, double leftRightProb, int rootSpanStart, int rootSpanEnd) {
    // Try the various rule combinations.
    List<ChartEntry> results = Lists.newArrayList();
    results.add(apply(leftRoot, rightRoot, Direction.RIGHT, leftSpanStart, leftSpanEnd, 
        leftIndex, rightSpanStart, rightSpanEnd, rightIndex));
    results.add(apply(rightRoot, leftRoot, Direction.LEFT, leftSpanStart, leftSpanEnd, 
        leftIndex, rightSpanStart, rightSpanEnd, rightIndex));
    results.add(compose(leftRoot, rightRoot, Direction.RIGHT));
    results.add(compose(rightRoot, leftRoot, Direction.LEFT));

    for (ChartEntry result : results) {
      if (result != null) {
        // Get the probabilities of the generated dependencies.
        double depProb = 1.0;
        for (DependencyStructure dep : result.getDependencies()) {
          Assignment assignment = dependencyHeadVar.outcomeArrayToAssignment(dep.getHead())
              .union(dependencyArgNumVar.outcomeArrayToAssignment(dep.getArgIndex()))
              .union(dependencyArgVar.outcomeArrayToAssignment(dep.getObject()));

          depProb *= dependencyDistribution.getUnnormalizedProbability(assignment);
        }

        chart.addChartEntryForSpan(result, leftRightProb * depProb, rootSpanStart, rootSpanEnd); 
        // System.out.println(rootSpanStart + "." + rootSpanEnd + " " + result.getCategory() + " " + result.getDependencies() + " " + (depProb * leftRightProb)); 
      }
    }
  }
  
  private ChartEntry apply(ChartEntry first, ChartEntry other, Direction direction,
      int leftSpanStart, int leftSpanEnd, int leftIndex, int rightSpanStart, int rightSpanEnd, int rightIndex) {
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
    Multimap<Integer, UnfilledDependency> unfilledDependencies = first.getUnfilledDependencies();
    Multimap<Integer, UnfilledDependency> newDeps = HashMultimap.create(unfilledDependencies);
    newDeps.removeAll(argNum);

    List<DependencyStructure> filledDeps = Lists.newArrayList();
    for (UnfilledDependency unfilled : unfilledDependencies.get(argNum)) {

      if (unfilled.getObjectIndex() == argNum) {
        Set<IndexedPredicate> objects = other.getHeads();
        if (unfilled.hasSubjects()) { 
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
          
          newDeps.remove(subjectIndex, unfilled);
          for (IndexedPredicate object : objects) {
            newDeps.put(subjectIndex, UnfilledDependency.createWithKnownObject(subjectIndex, argIndex, object.getHead(), object.getHeadIndex()));
          }
        }
      } else if (unfilled.getSubjectIndex() == argNum) {
        Set<IndexedPredicate> subjects = other.getHeads();
        if (unfilled.hasObjects()) {
          for (IndexedPredicate subject : subjects) {
            for (IndexedPredicate object : unfilled.getObjects()) {
              filledDeps.add(new DependencyStructure(subject.getHead(), subject.getHeadIndex(), 
                  object.getHead(), object.getHeadIndex(), unfilled.getArgumentIndex()));
            }
          }
        } else {
          // Part of the dependency remains unresolved. Fill what's possible, then propagate
          // the unfilled portions.
          int objectIndex = unfilled.getObjectIndex();
          newDeps.remove(objectIndex, unfilled);
          
          for (IndexedPredicate subject : subjects) {
            newDeps.put(objectIndex, UnfilledDependency.createWithKnownSubject(subject.getHead(),
                subject.getHeadIndex(), unfilled.getArgumentIndex(), objectIndex)); 
          }
        }
      }
    }
    
    if (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) {
      newDeps.putAll(other.getUnfilledDependencies());
    }
    
    // Handle any unfilled head arguments.
    if (newHeadArgumentNums.contains(argNum)) {
      newHeads.addAll(other.getHeads());
    }
    
    return new ChartEntry(syntax.getReturn(), newHeads, newHeadArgumentNums, newDeps, filledDeps, 
        leftSpanStart, leftSpanEnd, leftIndex, rightSpanStart, rightSpanEnd, rightIndex);
  }

  private ChartEntry compose(ChartEntry first, ChartEntry second, Direction direction) {
    return null;
  }
}