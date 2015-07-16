package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
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
      List<String> preprocessedTerminals, CcgParser parser, int lexiconNum) {
    List<String> posTags = sentence.getPosTags();

    for (int i = 0; i < preprocessedTerminals.size(); i++) {
      for (int j = i; j < preprocessedTerminals.size(); j++) {
        List<String> terminalValue = preprocessedTerminals.subList(i, j + 1);
        List<String> posTagValue = posTags.subList(i, j + 1);

        ChartEntry[] previousEntries = chart.getChartEntriesForSpan(i, j);
        int numAlreadyGenerated = chart.getNumChartEntriesForSpan(i, j);

        List<LexiconEntry> accumulator = Lists.newArrayList();
        List<Double> probAccumulator = Lists.newArrayList();
        getLexiconEntries(terminalValue, posTagValue, previousEntries,
            numAlreadyGenerated, i, j, sentence, accumulator, probAccumulator);
        Preconditions.checkState(accumulator.size() == probAccumulator.size());

        for (int n = 0; n < accumulator.size(); n++) {
          LexiconEntry entry = accumulator.get(n);
          double prob = probAccumulator.get(n);
          parser.addLexiconEntryToChart(chart, entry, prob, i, j, lexiconNum, sentence,
              terminalValue, posTagValue);
        }
        chart.doneAddingChartEntriesForSpan(i, j);
      }
    }
  }
}
