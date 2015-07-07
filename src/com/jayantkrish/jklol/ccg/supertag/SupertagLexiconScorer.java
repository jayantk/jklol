package com.jayantkrish.jklol.ccg.supertag;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.lexicon.LexiconScorer;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

/**
 * Lexicon scorer for incorporating supertags into CCG parsing. This
 * class assigns a score of 0 to lexicon entries whose syntactic
 * category is not in an annotated list of syntactic categories for
 * the span.
 *  
 * @author jayantk
 */
public class SupertagLexiconScorer implements LexiconScorer {
  private static final long serialVersionUID = 1L;

  private final String supertagAnnotationName;

  public SupertagLexiconScorer(String supertagAnnotationName) {
    this.supertagAnnotationName = Preconditions.checkNotNull(supertagAnnotationName);
  }

  @Override
  public double getCategoryWeight(int spanStart, int spanEnd, AnnotatedSentence sentence,
      List<String> terminalValue, List<String> posTags, CcgCategory category) {

    SupertagAnnotation annotation = (SupertagAnnotation) sentence
      .getAnnotation(supertagAnnotationName);
    if (annotation == null) {
      // If no supertags are annotated on the sentence, do not restrict the
      // set of possible lexicon entries.
      return 1.0;
    }
    
    List<List<HeadedSyntacticCategory>> supertags = annotation.getSupertags();
    
    // This filter only applies to single word terminal entries where
    // the example has a specified set of valid supertags.
    if (spanStart != spanEnd || supertags.get(spanStart).size() == 0) {
      return 1.0;
    } 

    HeadedSyntacticCategory entrySyntax = category.getSyntax();
    for (HeadedSyntacticCategory supertag : supertags.get(spanStart)) {
      if (entrySyntax.equals(supertag)) {
        return 1.0;
      }
    }
    return 0.0;
  }
}
