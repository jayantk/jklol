package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Lexicon containing a table of word sequences to CCG
 * category mappings.
 * 
 * @author jayant
 *
 */
public class TableLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 3L;
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;

  public TableLexicon(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution) {
    super(terminalVar);
    
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);
    VariableNumMap expectedTerminalVars = terminalVar.union(ccgCategoryVar);
    Preconditions.checkArgument(expectedTerminalVars.equals(terminalDistribution.getVars()));
  }

  @Override
  public void getLexiconEntries(int spanStart, int spanEnd, AnnotatedSentence sentence,
      ChartEntry[] alreadyGenerated, int numAlreadyGenerated, List<Object> triggerAccumulator,
      List<CcgCategory> accumulator, List<Double> probAccumulator) {
    List<String> wordSequence = sentence.getWordsLowercase().subList(spanStart, spanEnd + 1);
    if (terminalVar.isValidOutcomeArray(wordSequence)) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(wordSequence);

      Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory ccgCategory = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());

        triggerAccumulator.add(wordSequence);
        accumulator.add(ccgCategory);
        probAccumulator.add(bestOutcome.getProbability());
      }
    }
  }
}
