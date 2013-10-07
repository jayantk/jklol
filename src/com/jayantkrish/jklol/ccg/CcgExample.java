package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;

/**
 * A training example for training a CCG parser. The input
 * portion of the training example is a part-of-speech tagged 
 * (and tokenized) sentence, and optionally a collection of supertags to use.
 * The output portion optionally contains the correct syntactic parse of
 * the sentence, its predicate-argument dependencies, and its logical form.
 * If any output field is unspecified, it is presumed to be unobserved.
 * 
 * @author jayant
 */
public class CcgExample {

  // The sentence to parse, along with part-of-speech tags for each word
  // and optional supertags (syntactic categories to consider for each word).
  private final SupertaggedSentence sentence;

  // May be null, in which case the true dependencies are
  // unobserved.
  private final Set<DependencyStructure> dependencies;
  // May be null, in which case the true syntactic structure is
  // unobserved.
  private final CcgSyntaxTree syntacticParse;
  // Expected logical form for the parse. May be null,
  // in which case the true logical form is unobserved.
  private final Expression logicalForm;

  /**
   * Create a new training example for a CCG parser.
   * 
   * @param words The input language to CCG parse.
   * @param posTags Part-of-speech tags for the input language. May be
   * {@code null}.
   * @param supertags List of candidate syntactic categories for each 
   * word. May be {@code null}.
   * @param dependencies The dependencies in the correct CCG parse of
   * {@code words}. May be {@code null}, in which case the
   * dependencies are unobserved.
   * @param syntacticParse The syntactic structure of the correct CCG
   * parse of {@code words}. May be {@code null}, in which case the
   * correct parse is treated as unobserved.
   * @param logicalForm The logical form of the correct CCG parse of
   * {@code words}. May be {@code null}, in which case the correct
   * value is treated as unobserved.
   */
  public CcgExample(SupertaggedSentence sentence, Set<DependencyStructure> dependencies,
      CcgSyntaxTree syntacticParse, Expression logicalForm) {
    this.sentence = Preconditions.checkNotNull(sentence);
    this.dependencies = dependencies;
    this.syntacticParse = syntacticParse;
    this.logicalForm = logicalForm;

    if (syntacticParse != null) {
      List<String> syntaxWords = syntacticParse.getAllSpannedWords();
      Preconditions.checkArgument(syntaxWords.equals(sentence.getWords()),
          "CCG syntax tree and example must agree on words: \"%s\" vs \"%s\" %s", syntaxWords,
          sentence.getWords(), syntacticParse);
    }
  }

  /**
   * Gets the complete set of POS tags used for any word in
   * {@code examples}.
   * 
   * @param examples
   * @return
   */
  public static Set<String> getPosTagVocabulary(Iterable<? extends CcgExample> examples) {
    Set<String> posTagVocabulary = Sets.newHashSet();
    for (CcgExample example : examples) {
      posTagVocabulary.addAll(example.getPosTags());
    }
    return posTagVocabulary;
  }

  public SupertaggedSentence getSentence() {
    return sentence;
  }
  
  public List<String> getWords() {
    return sentence.getWords();
  }

  public List<String> getPosTags() {
    return sentence.getPosTags();
  }

  public List<List<HeadedSyntacticCategory>> getSupertags() {
    return sentence.getSupertags();
  }

  /**
   * Returns {@code true} if the syntactic structure of the correct
   * parse is observed.
   * 
   * @return
   */
  public boolean hasSyntacticParse() {
    return syntacticParse != null;
  }

  public CcgSyntaxTree getSyntacticParse() {
    return syntacticParse;
  }

  public boolean hasDependencies() {
    return dependencies != null;
  }

  public Set<DependencyStructure> getDependencies() {
    return dependencies;
  }

  public boolean hasLogicalForm() {
    return logicalForm != null;
  }

  public Expression getLogicalForm() {
    return logicalForm;
  }

  @Override
  public String toString() {
    return sentence + " " + dependencies + " " + syntacticParse;
  }
}
