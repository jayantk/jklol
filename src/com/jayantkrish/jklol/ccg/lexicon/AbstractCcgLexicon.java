package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

/**
 * Implementations of common {@code CcgLexicon} methods.
 * 
 * @author jayant
 *
 */
public abstract class AbstractCcgLexicon implements CcgLexicon {
  private static final long serialVersionUID = 3L;
  
  private final VariableNumMap terminalVar;

  public AbstractCcgLexicon(VariableNumMap terminalVar) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
  }

  @Override
  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }
  
  @Override
  public void initializeChart(CcgChart chart, AnnotatedSentence sentence,
      CcgParser parser, int lexiconNum) {
    for (int i = 0; i < sentence.size(); i++) {
      for (int j = i; j < sentence.size(); j++) {
        ChartEntry[] previousEntries = chart.getChartEntriesForSpan(i, j);
        int numAlreadyGenerated = chart.getNumChartEntriesForSpan(i, j);

        List<Object> triggerAccumulator = Lists.newArrayList();
        List<CcgCategory> accumulator = Lists.newArrayList();
        List<Double> probAccumulator = Lists.newArrayList();
        getLexiconEntries(i, j, sentence, previousEntries, numAlreadyGenerated,
            triggerAccumulator, accumulator, probAccumulator);
        Preconditions.checkState(accumulator.size() == probAccumulator.size());
        Preconditions.checkState(accumulator.size() == triggerAccumulator.size());

        for (int n = 0; n < accumulator.size(); n++) {
          Object trigger = triggerAccumulator.get(n);
          CcgCategory entry = accumulator.get(n);
          double prob = probAccumulator.get(n);
          parser.addLexiconEntryToChart(chart, trigger, entry, prob, i, j, sentence, lexiconNum);
        }
        chart.doneAddingChartEntriesForSpan(i, j);
      }
    }
  }
}
