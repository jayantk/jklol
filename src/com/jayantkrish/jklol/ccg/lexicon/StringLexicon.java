package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * CCG Lexicon that allows strings from the text to be 
 * used as lexicon entries. 
 * 
 * @author jayant
 *
 */
public class StringLexicon extends AbstractCcgLexicon {
  private static final long serialVersionUID = 1L;

  private final List<CcgCategory> categories;

  public StringLexicon(VariableNumMap terminalVar, List<CcgCategory> categories) {
    super(terminalVar);
    this.categories = ImmutableList.copyOf(categories);

    /*
    this.featureGenerator = Preconditions.checkNotNull(featureGenerator);
    this.featureWeights = Preconditions.checkNotNull(featureWeights);
    Preconditions.checkArgument(featureWeights.getDimensionSizes().length == 1);
    Preconditions.checkArgument(featureGenerator.getNumberOfFeatures() == featureWeights.getDimensionSizes()[0]);
    */
  }

  @Override
  public List<LexiconEntry> getLexiconEntries(List<String> wordSequence, List<String> posTags, 
      List<LexiconEntry> alreadyGenerated) {
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
  public double getCategoryWeight(List<String> wordSequence, List<String> posTags, CcgCategory category) {
    return 1.0;
  }

  /**
   * The context within which a string appears in a sentence. 
   * This class is used to generate features for the string lexicon.
   * 
   * @author jayant
   */
  /*
  public static class StringContext {
    private final int spanStart;
    private final int spanEnd;
    
    private final List<String> originalWords;
    private final List<String> preprocessedWords;
    private final List<String> pos;
    
    private final CcgCategory category;

    public StringContext(int spanStart, int spanEnd, List<String> originalWords,
        List<String> preprocessedWords, List<String> pos, CcgCategory category) {
      this.spanStart = spanStart;
      this.spanEnd = spanEnd;
      this.originalWords = originalWords;
      this.preprocessedWords = preprocessedWords;
      this.pos = pos;
      this.category = category;
    }

    public int getSpanStart() {
      return spanStart;
    }

    public int getSpanEnd() {
      return spanEnd;
    }

    public List<String> getOriginalWords() {
      return originalWords;
    }

    public List<String> getPreprocessedWords() {
      return preprocessedWords;
    }

    public List<String> getPos() {
      return pos;
    }

    public CcgCategory getCategory() {
      return category;
    }
  }
  */
}
