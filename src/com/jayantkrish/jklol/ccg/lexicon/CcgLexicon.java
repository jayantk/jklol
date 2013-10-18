package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * The lexicon of a CCG parser which determines the initial 
 * mapping from terminal symbols to nonterminals in the
 * parse chart. 
 *  
 * @author jayant
 *
 */
public interface CcgLexicon {
  public static final String UNKNOWN_WORD_PREFIX = "UNK-";

  void initializeChartTerminals(List<String> terminals, List<String> posTags, CcgChart chart,
      CcgParser parser);

  VariableNumMap getTerminalVar();
  
  VariableNumMap getTerminalPosVar();
  
  List<LexiconEntry> getLexiconEntries(List<String> wordSequence);
  
  List<LexiconEntry> getLexiconEntriesWithUnknown(String word, String posTag);
  
  List<LexiconEntry> getLexiconEntriesWithUnknown(List<String> originalWords, List<String> posTags);
  
  boolean isPossibleLexiconEntry(List<String> originalWords, List<String> posTags,
      HeadedSyntacticCategory category);
}