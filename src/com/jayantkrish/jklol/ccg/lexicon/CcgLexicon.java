package com.jayantkrish.jklol.ccg.lexicon;

import java.io.Serializable;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * The lexicon of a CCG parser which determines the initial 
 * mapping from terminal symbols to nonterminals in the
 * parse chart. 
 *  
 * @author jayant
 *
 */
public interface CcgLexicon extends Serializable {
  public static final String UNKNOWN_WORD_PREFIX = "UNK-";

  void initializeChartTerminals(SupertaggedSentence input, CcgChart chart, CcgParser parser);

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
  
  /**
   * TODO: the lexicon interface needs to be refactored to clean up this method
   * and some of this other nonsense
   * 
   * @param originalWords
   * @param preprocessedWords
   * @param pos
   * @param ccgWordList
   * @param featureVectors
   * @param spanStart
   * @param spanEnd
   * @param terminals
   * @param category
   * @return
   */
  double getCategoryWeight(List<String> originalWords, List<String> preprocessedWords, 
      List<String> pos, List<WordAndPos> ccgWordList, List<Tensor> featureVectors,
      int spanStart, int spanEnd, List<String> terminals, CcgCategory category);
}