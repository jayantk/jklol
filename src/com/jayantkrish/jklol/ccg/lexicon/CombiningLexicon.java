package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Tensor;

/**
 * Lexicon that combines two other lexicons.
 * 
 * @author jayant
 *
 */
public class CombiningLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final List<CcgLexicon> lexicons;
  
  public CombiningLexicon(VariableNumMap terminalVar, List<CcgLexicon> lexicons) {
    super(terminalVar, null);
    this.lexicons = ImmutableList.copyOf(lexicons);
    for (CcgLexicon lexicon : lexicons) {
      Preconditions.checkArgument(terminalVar.equals(lexicon.getTerminalVar()));
    }
  }

  @Override
  public List<LexiconEntry> getLexiconEntries(List<String> wordSequence) {
    Set<LexiconEntry> entries = Sets.newHashSet();
    for (CcgLexicon lexicon : lexicons) {
      entries.addAll(lexicon.getLexiconEntries(wordSequence));
    }
    return Lists.newArrayList(entries);
  }

  @Override
  public double getCategoryWeight(List<String> originalWords, List<String> preprocessedWords,
      List<String> pos, List<WordAndPos> ccgWordList, List<Tensor> featureVectors, int spanStart,
      int spanEnd, List<String> terminals, CcgCategory category) {
    double prob = 1.0;
    for (CcgLexicon lexicon : lexicons) {
      prob *= lexicon.getCategoryWeight(originalWords, preprocessedWords, pos, ccgWordList,
          featureVectors, spanStart, spanEnd, terminals, category);
    }
    return prob;
  }
}
