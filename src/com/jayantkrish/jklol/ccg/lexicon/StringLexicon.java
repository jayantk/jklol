package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Tensor;

public class StringLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final List<CcgCategory> categories;

  public StringLexicon(VariableNumMap terminalVar, List<CcgCategory> categories) {
    super(terminalVar, null);
    this.categories = ImmutableList.copyOf(categories);
  }

  @Override
  public List<LexiconEntry> getLexiconEntries(List<String> wordSequence) {
    List<LexiconEntry> entries = Lists.newArrayList();
    for (CcgCategory category : categories) {
      Expression2 wordSequenceExpression = Expression2.constant("\"" + Joiner.on(" ").join(wordSequence) + "\"");
      Expression2 newLf = Expression2.nested(category.getLogicalForm(), wordSequenceExpression);
      CcgCategory newCategory = category.replaceLogicalForm(newLf);
      entries.add(new LexiconEntry(wordSequence, newCategory));
    }
    return entries;
  }

  @Override
  public double getCategoryWeight(List<String> originalWords, List<String> preprocessedWords,
      List<String> pos, List<WordAndPos> ccgWordList, List<Tensor> featureVectors, int spanStart,
      int spanEnd, List<String> terminals, CcgCategory category) {
    return 1;
  }
}
