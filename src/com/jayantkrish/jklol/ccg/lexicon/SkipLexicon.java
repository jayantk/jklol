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
 * Lexicon for performing word skipping in the CCG parser.
 * This lexicon wraps another lexicon and extends every entry
 * in that lexicon such that the parser is able to skip words
 * in the sentence. The implementation guarantees that the
 * skipped words do not create additional ambiguity in the 
 * parser, i.e., for every CCG derivation for every subset of 
 * words in the sentence, exactly one corresponding CCG
 * derivation is permitted.
 * 
 * @author jayantk
 *
 */
public class SkipLexicon implements CcgLexicon {
  private static final long serialVersionUID = 1L;

  private final VariableNumMap terminalVar;
  private final CcgLexicon lexicon;
  // private final TableFactor skipProbs;

  public SkipLexicon(CcgLexicon lexicon) {
    this.terminalVar = lexicon.getTerminalVar();
    this.lexicon = Preconditions.checkNotNull(lexicon);
  }

  @Override
  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }

  @Override
  public void getLexiconEntries(int spanStart, int spanEnd, AnnotatedSentence sentence,
      ChartEntry[] alreadyGenerated, int numAlreadyGenerated,
      List<Object> triggerAccumulator, List<CcgCategory> accumulator, List<Double> probAccumulator) {
    // TODO: this method doesn't totally do the right thing.

    for (int i = spanStart; i <= spanEnd; i++) {
      for (int j = i; j <= spanEnd; j++) {
        lexicon.getLexiconEntries(i, j, sentence, alreadyGenerated, numAlreadyGenerated,
            triggerAccumulator, accumulator, probAccumulator);
      }
    }
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
        lexicon.getLexiconEntries(i, j, sentence, previousEntries, numAlreadyGenerated,
            triggerAccumulator, accumulator, probAccumulator);
        Preconditions.checkState(accumulator.size() == probAccumulator.size());
        Preconditions.checkState(accumulator.size() == triggerAccumulator.size());
        
        for (int n = 0; n < accumulator.size(); n++) {
          Object trigger = new SkipTrigger(triggerAccumulator.get(n), i, j);
          CcgCategory entry = accumulator.get(n);
          double prob = probAccumulator.get(n);
          parser.addLexiconEntryToChart(chart, trigger, entry, prob, i, j, sentence, lexiconNum);

          for (int k = j + 1; k < sentence.size(); k++) {
            // Skip any number of words to the right.
            parser.addLexiconEntryToChart(chart, trigger, entry, prob, i, k, sentence, lexiconNum);
          }
          
          if (i != 0) {
            for (int k = j; k < sentence.size(); k++) {
              // Skip all words to the left AND any number of words to
              // the right.
              parser.addLexiconEntryToChart(chart, trigger, entry, prob, 0, k, sentence, lexiconNum);
            }
          }
        }
      }
    }

    for (int i = 0; i < sentence.size(); i++) {
      for (int j = i; j < sentence.size(); j++) {
        chart.doneAddingChartEntriesForSpan(i, j);
      }
    }
  }
  
  public static class SkipTrigger {
    private final Object trigger;
    private final int triggerSpanStart;
    private final int triggerSpanEnd;

    public SkipTrigger(Object trigger, int triggerSpanStart, int triggerSpanEnd) {
      this.trigger = trigger;
      this.triggerSpanStart = triggerSpanStart;
      this.triggerSpanEnd = triggerSpanEnd;
    }

    public Object getTrigger() {
      return trigger;
    }

    public int getTriggerSpanStart() {
      return triggerSpanStart;
    }

    public int getTriggerSpanEnd() {
      return triggerSpanEnd;
    }
  }
}
