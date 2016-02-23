package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.util.Assignment;

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
      CcgParser parser, int lexiconNum, VariableNumMap wordSkipWordVar,
      DiscreteFactor wordSkipWeights) {
    if (wordSkipWeights == null) {
      initializeChartNoWordSkip(chart, sentence, parser, lexiconNum);
    } else {
      initializeChartWordSkip(chart, sentence, parser, lexiconNum,
          wordSkipWordVar, wordSkipWeights);
    }
  }

  private void initializeChartNoWordSkip(CcgChart chart, AnnotatedSentence sentence,
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
          parser.addLexiconEntryToChart(chart, trigger, entry, prob, i, j, i, j, sentence, lexiconNum);
        }
        chart.doneAddingChartEntriesForSpan(i, j);
      }
    }
  }

  private void initializeChartWordSkip(CcgChart chart, AnnotatedSentence sentence,
      CcgParser parser, int lexiconNum, VariableNumMap terminalVar, DiscreteFactor skipWeights) {
    List<String> lcWords = sentence.getWordsLowercase();
    double[] skipProbs = new double[sentence.size()];
    for (int i = 0; i < lcWords.size(); i++) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(lcWords.subList(i, i + 1));
      if (terminalVar.isValidAssignment(assignment)) {
        skipProbs[i] = skipWeights.getUnnormalizedProbability(assignment);
      } else {
        skipProbs[i] = 1.0;
      }
    }

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
          Object trigger = new SkipTrigger(triggerAccumulator.get(n), i, j);
          CcgCategory entry = accumulator.get(n);
          double prob = probAccumulator.get(n);
          parser.addLexiconEntryToChart(chart, trigger, entry, prob, i, j, i, j, sentence, lexiconNum);

          double rightProb = prob;
          for (int k = j + 1; k < sentence.size(); k++) {
            // Skip any number of words to the right.
            rightProb *= skipProbs[k];
            parser.addLexiconEntryToChart(chart, trigger, entry, rightProb, i, k, i, j, sentence, lexiconNum);
          }

          if (i != 0) {
            double rightLeftProb = prob;
            for (int k = 0; k < i; k++) {
              rightLeftProb *= skipProbs[k];
            }

            for (int k = j; k < sentence.size(); k++) {
              // Skip all words to the left AND any number of words to
              // the right.
              if (k != j) {
                rightLeftProb *= skipProbs[k];
              }
              parser.addLexiconEntryToChart(chart, trigger, entry, rightLeftProb, 0, k, i, j, sentence, lexiconNum);
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

    @Override
    public String toString() {
      return trigger.toString();
    }
  }
}