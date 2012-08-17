package com.jayantkrish.jklol.ccg;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory.CcgCombinationResult;
import com.jayantkrish.jklol.ccg.CcgCategory.DependencyStructure;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

public class CcgParser {

  private final int beamSize;

  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;
  
  private final VariableNumMap dependencyHeadVar;
  private final VariableNumMap dependencyArgNumVar;
  private final VariableNumMap dependencyArgVar;
  private final DiscreteFactor dependencyDistribution; 

  public CcgParser(int beamSize, VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap dependencyHeadVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyArgVar, DiscreteFactor dependencyDistribution) {
    this.beamSize = beamSize;

    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);
    
    this.dependencyHeadVar = Preconditions.checkNotNull(dependencyHeadVar);
    this.dependencyArgNumVar = Preconditions.checkNotNull(dependencyArgNumVar);
    this.dependencyArgVar = Preconditions.checkNotNull(dependencyArgVar);
    this.dependencyDistribution = Preconditions.checkNotNull(dependencyDistribution);
  }

  public List<CcgParse> beamSearch(List<? extends Object> terminals) {
    CcgChart chart = new CcgChart(terminals, beamSize);
    
    initializeChart(terminals, chart);

    // Construct a tree from the nonterminals.
    for (int spanSize = 1; spanSize < chart.size(); spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chart.size(); spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart);
      }
    }
      
    int numParses = Math.min(beamSize, chart.getNumParseTreesForSpan(0, chart.size() - 1));
    
    List<CcgParse> parses = Lists.newArrayListWithCapacity(numParses);
    for (int i = numParses - 1; i >= 0; i--) {
      parses.add(chart.decodeParseFromSpan(0, chart.size() - 1, i));
    }
    return parses;
  }
  
  public void initializeChart(List<? extends Object> terminals, CcgChart chart) {
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
            
            chart.addParseTreeForTerminalSpan(category, bestOutcome.getProbability(), i, j);
            System.out.println(i + "." + j + " : " + category + " " + bestOutcome.getProbability());
          }
        }
      }
    }
  }

  public void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart) {
    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j is for forward compatibility for skipping terminal symbols.
      for (int j = i + 1; j < i + 2; j++) {
        CcgCategory[] leftTrees = chart.getParseTreesForSpan(spanStart, spanStart + i);
        double[] leftProbs = chart.getParseTreeProbsForSpan(spanStart, spanStart + i);
        int numLeftTrees = chart.getNumParseTreesForSpan(spanStart, spanStart + i);

        CcgCategory[] rightTrees = chart.getParseTreesForSpan(spanStart + j, spanEnd);
        double[] rightProbs = chart.getParseTreeProbsForSpan(spanStart + j, spanEnd);
        int numRightTrees = chart.getNumParseTreesForSpan(spanStart + j, spanEnd);

        for (int leftIndex = 0; leftIndex < numLeftTrees; leftIndex++) {
          CcgCategory leftRoot = leftTrees[leftIndex];
          double leftProb = leftProbs[leftIndex];
          for (int rightIndex = 0; rightIndex < numRightTrees; rightIndex++) {
            CcgCategory rightRoot = rightTrees[rightIndex];
            double rightProb = rightProbs[rightIndex];

            populateChartEntry(leftRoot, spanStart, spanStart + i, leftIndex, 
                rightRoot, spanStart + j, spanEnd, rightIndex, 
                chart, leftProb * rightProb, spanStart, spanEnd);
          }
        }
      }
    }
  }

  private void populateChartEntry(CcgCategory leftRoot, int leftSpanStart, int leftSpanEnd, int leftIndex,
      CcgCategory rightRoot, int rightSpanStart, int rightSpanEnd, int rightIndex,
      CcgChart chart, double leftRightProb, int rootSpanStart, int rootSpanEnd) {
    // Try the various rule combinations.
    List<CcgCombinationResult> results = Lists.newArrayList();
    results.add(leftRoot.apply(rightRoot, Direction.RIGHT));
    results.add(rightRoot.apply(leftRoot, Direction.LEFT));
    results.add(leftRoot.compose(rightRoot, Direction.RIGHT));
    results.add(rightRoot.compose(leftRoot, Direction.LEFT));
    
    for (CcgCombinationResult result : results) {
      if (result != null) {
        // Get the probabilities of the generated dependencies.
        double depProb = 1.0;
        for (DependencyStructure dep : result.getDependencies()) {
          Assignment assignment = dependencyHeadVar.outcomeArrayToAssignment(dep.getHead())
              .union(dependencyArgNumVar.outcomeArrayToAssignment(dep.getArgIndex()))
              .union(dependencyArgVar.outcomeArrayToAssignment(dep.getObject()));
          
          depProb *= dependencyDistribution.getUnnormalizedProbability(assignment);
        }
        
        chart.addParseTreeForSpan(result.getCategory(), result.getDependencies(), leftRightProb * depProb, 
            leftSpanStart, leftSpanEnd, leftIndex, rightSpanStart, rightSpanEnd, rightIndex, 
            rootSpanStart, rootSpanEnd);
        System.out.println(rootSpanStart + "." + rootSpanEnd + " " + result.getCategory() + " " + result.getDependencies() + " " + (depProb * leftRightProb)); 
      }
    }
  }
}