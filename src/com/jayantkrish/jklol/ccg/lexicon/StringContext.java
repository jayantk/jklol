package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

/**
 * A string span within a larger sentence. This class is used
 * for generating feature vectors.
 * 
 * @author jayant
 *
 */
public class StringContext {
  private final int spanStart;
  private final int spanEnd;
  
  private final AnnotatedSentence sentence;

  public StringContext(int spanStart, int spanEnd, AnnotatedSentence sentence) {
    this.spanStart = spanStart;
    this.spanEnd = spanEnd;

    this.sentence = Preconditions.checkNotNull(sentence);
  }

  /**
   * Generates and returns a list of every StringContext for every
   * sentence in {@code examples}.
   *  
   * @param examples
   * @return
   */
  public static List<StringContext> getContextsFromExamples(List<CcgExample> examples) {
    List<StringContext> contexts = Lists.newArrayList();
    for (CcgExample example : examples) {
      AnnotatedSentence sentence = example.getSentence();
      int numTerminals = sentence.size();
      for (int i = 0; i < numTerminals; i++) {
        for (int j = i; j < numTerminals; j++) {
          contexts.add(new StringContext(i, j, sentence));
        }
      }
    }
    return contexts;
  }

  public int getSpanStart() {
    return spanStart;
  }

  public int getSpanEnd() {
    return spanEnd;
  }
  
  public AnnotatedSentence getSentence() {
    return sentence;
  }

  public List<String> getWords() {
    return sentence.getWords();
  }

  public List<String> getPos() {
    return sentence.getPosTags();
  }
}