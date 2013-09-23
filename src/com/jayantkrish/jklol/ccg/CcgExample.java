package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Expression;

/**
 * A training example for {@code CcgLoglikelihoodOracle}. Stores an
 * input word sequence and its expected set of dependencies. May
 * optionally contain the expected syntactic structure.
 * 
 * @author jayant
 */
public class CcgExample {

  private final List<String> words;
  private final List<String> posTags;

  // Optional field containing candidate supertags for the sentence.
  // Null means all syntactic categories are acceptable for each word.
  private final List<List<SyntacticCategory>> supertags;

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
  public CcgExample(List<String> words, List<String> posTags, List<List<SyntacticCategory>> supertags,
      Set<DependencyStructure> dependencies, CcgSyntaxTree syntacticParse, Expression logicalForm) {
    this.words = Preconditions.checkNotNull(words);
    this.posTags = Preconditions.checkNotNull(posTags);
    this.supertags = supertags;
    Preconditions.checkArgument(words.size() == posTags.size());
    Preconditions.checkArgument(supertags == null || supertags.size() == words.size());
    this.dependencies = dependencies;
    this.syntacticParse = syntacticParse;
    this.logicalForm = logicalForm;

    if (syntacticParse != null) {
      List<String> syntaxWords = syntacticParse.getAllSpannedWords();
      Preconditions.checkArgument(syntaxWords.equals(words),
          "CCG syntax tree and example must agree on words: \"%s\" vs \"%s\" %s", syntaxWords,
          words, syntacticParse);
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

  public List<String> getWords() {
    return words;
  }

  public List<String> getPosTags() {
    return posTags;
  }
  
  public List<List<SyntacticCategory>> getSupertags() {
    return supertags;
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
    return words + " " + dependencies + " " + syntacticParse;
  }
}
