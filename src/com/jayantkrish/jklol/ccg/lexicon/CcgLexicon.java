package com.jayantkrish.jklol.ccg.lexicon;

import java.io.Serializable;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * The lexicon of a CCG parser which determines the initial 
 * mapping from terminal symbols to nonterminals in the
 * parse chart. 
 *  
 * @author jayant
 *
 */
public interface CcgLexicon<T extends SupertaggedSentence> extends Serializable {
  public static final String UNKNOWN_WORD_PREFIX = "UNK-";

  void initializeChartTerminals(T input, CcgChart<T> chart, CcgParser<T> parser);

  VariableNumMap getTerminalVar();
  
  /**
   * Gets the possible lexicon entries for {@code wordSequence} that
   * can be used in this parser. The returned entries do not include
   * lexicon entries for unknown words, which may occur in the parse
   * if {@code wordSequence} is unrecognized.
   * 
   * @param wordSequence
   * @return
   */
  List<LexiconEntry> getLexiconEntries(List<String> wordSequence);
  
  List<LexiconEntry> getLexiconEntriesWithUnknown(String word, String posTag);
  
  List<LexiconEntry> getLexiconEntriesWithUnknown(List<String> originalWords, List<String> posTags);
  
  boolean isPossibleLexiconEntry(List<String> originalWords, List<String> posTags,
      HeadedSyntacticCategory category);
}