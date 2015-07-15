package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
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
  public void getLexiconEntries(List<String> wordSequence, List<String> posTags,
      List<LexiconEntry> alreadyGenerated, int spanStart, int spanEnd, AnnotatedSentence sentence,
      List<LexiconEntry> accumulator, List<Double> probAccumulator) {
    TableLexicon.getLexiconEntriesFromFactor(wordSequence, terminalDistribution,
        terminalVar, ccgCategoryVar, accumulator, probAccumulator);
  }

  /**
   * Gets the possible lexicon entries for {@code wordSequence} from
   * {@code terminalDistribution}, a distribution over CCG categories
   * given word sequences.
   * 
   * @param wordSequence
   * @param terminalDistribution
   * @param terminalVar
   * @param ccgCategoryVar
   * @param accumulator list that generated entries are added to.
   * @param probAccumulator probabilities of the generated entries.
   * @return
   */
  private static void getLexiconEntriesFromFactor(List<String> wordSequence,
      DiscreteFactor terminalDistribution, VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      List<LexiconEntry> accumulator, List<Double> probAccumulator) {
    if (terminalVar.isValidOutcomeArray(wordSequence)) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(wordSequence);

      Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory ccgCategory = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());

        accumulator.add(new LexiconEntry(wordSequence, ccgCategory));
        probAccumulator.add(bestOutcome.getProbability());
      }
    }
  }
}
