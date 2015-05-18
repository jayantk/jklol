package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Lexicon for handling unknown words.
 * 
 * @author jayant
 *
 */
public class UnknownWordLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap terminalPosSequenceVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;

  public UnknownWordLexicon(VariableNumMap terminalVar, VariableNumMap terminalPosSequenceVar,
      VariableNumMap ccgCategoryVar, DiscreteFactor terminalDistribution) {
    super(terminalVar);
    this.terminalPosSequenceVar = Preconditions.checkNotNull(terminalPosSequenceVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);
  }

  @Override
  public List<LexiconEntry> getLexiconEntries(List<String> wordSequence, List<String> posSequence,
      List<LexiconEntry> alreadyGenerated) {

    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    if (alreadyGenerated.size() == 0) {
      Assignment assignment = terminalPosSequenceVar.outcomeArrayToAssignment(posSequence);

      Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory ccgCategory = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());

        lexiconEntries.add(new LexiconEntry(wordSequence, ccgCategory));
      }
    }
    return lexiconEntries;
  }

  @Override
  public double getCategoryWeight(List<String> wordSequence, List<String> posSequence,
      CcgCategory category) {
    Assignment terminalAssignment = terminalPosSequenceVar.outcomeArrayToAssignment(posSequence);
    Assignment categoryAssignment = ccgCategoryVar.outcomeArrayToAssignment(category);
    Assignment a = terminalAssignment.union(categoryAssignment);
    return terminalDistribution.getUnnormalizedProbability(a);
  }
}
