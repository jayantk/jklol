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
  public void getLexiconEntries(List<String> wordSequence, List<String> posSequence,
      ChartEntry[] alreadyGenerated, int numAlreadyGenerated,
      int spanStart, int spanEnd, AnnotatedSentence sentence,
      List<LexiconEntry> accumulator, List<Double> probAccumulator) {
    // TODO: this method doesn't totally do the right thing.

    for (int i = 0; i < wordSequence.size(); i++) {
      for (int j = i; j < wordSequence.size(); j++) {
        List<String> subwords = wordSequence.subList(i, j + 1);
        List<String> subpos = posSequence.subList(i, j + 1);
        lexicon.getLexiconEntries(subwords, subpos, alreadyGenerated, numAlreadyGenerated,
            spanStart + i, spanStart + j, sentence, accumulator, probAccumulator);
      }
    }
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
        lexicon.getLexiconEntries(terminalValue, posTagValue, previousEntries,
            numAlreadyGenerated, i, j, sentence, accumulator, probAccumulator);
        Preconditions.checkState(accumulator.size() == probAccumulator.size());

        for (int n = 0; n < accumulator.size(); n++) {
          LexiconEntry entry = accumulator.get(n);
          double prob = probAccumulator.get(n);
          parser.addLexiconEntryToChart(chart, entry, prob, i, j, lexiconNum, sentence,
              terminalValue, posTagValue);

          for (int k = j + 1; k < preprocessedTerminals.size(); k++) {
            // Skip any number of words to the right.
            parser.addLexiconEntryToChart(chart, entry, prob, i, k, lexiconNum, sentence,
              terminalValue, posTagValue);
          }
          
          if (i != 0) {
            for (int k = j; k < preprocessedTerminals.size(); k++) {
              // Skip all words to the left AND any number of words to
              // the right.
              parser.addLexiconEntryToChart(chart, entry, prob, 0, k, lexiconNum, sentence,
                  terminalValue, posTagValue);
            }
          }
        }
      }
    }

    for (int i = 0; i < preprocessedTerminals.size(); i++) {
      for (int j = i; j < preprocessedTerminals.size(); j++) {
        chart.doneAddingChartEntriesForSpan(i, j);
      }
    }
  }
}
