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
 * Lexicon for handling unknown words.
 * 
 * @author jayant
 *
 */
public class UnknownWordLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap posVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor posCategoryDistribution;

  public UnknownWordLexicon(VariableNumMap terminalVar, VariableNumMap posVar,
      VariableNumMap ccgCategoryVar, DiscreteFactor posCategoryDistribution) {
    super(terminalVar);
    this.posVar = Preconditions.checkNotNull(posVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.posCategoryDistribution = Preconditions.checkNotNull(posCategoryDistribution);
  }

  @Override
  public void getLexiconEntries(int spanStart, int spanEnd, AnnotatedSentence sentence,
      ChartEntry[] alreadyGenerated, int numAlreadyGenerated,  List<Object> triggerAccumulator,
      List<CcgCategory> accumulator, List<Double> probAccumulator) {

    if (numAlreadyGenerated == 0 && spanEnd == spanStart) {
      String pos = sentence.getPosTags().get(spanStart);
      Assignment assignment = posVar.outcomeArrayToAssignment(pos);

      Iterator<Outcome> iterator = posCategoryDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory ccgCategory = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());

        triggerAccumulator.add(pos);
        accumulator.add(ccgCategory);
        probAccumulator.add(bestOutcome.getProbability());
      }
    }
  }
}
