package com.jayantkrish.jklol.ccg;

import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class CcgSyntaxTree {
  // The syntactic category at the root of this tree, 
  // possibly after the application of a unary rule.
  private final SyntacticCategory syntax;
  // Pre-unary rule syntactic category. May be equal to
  // syntax.
  private final SyntacticCategory originalSyntax;
  // Portion of the sentence spanned by this tree.
  private final int spanStart;
  private final int spanEnd;
  
  // Non-null if this is a nonterminal.
  private final CcgSyntaxTree left;
  private final CcgSyntaxTree right;
  
  // Non-null only if this is a terminal.
  private final List<String> words;
  private final List<String> posTags;
  // If this is a terminal, the true head-passing relationship
  // for the syntactic category. If non-null, must be the same
  // base syntactic category as originalSyntax.
  private final HeadedSyntacticCategory headedSyntax;

  private CcgSyntaxTree(SyntacticCategory syntax, SyntacticCategory originalSyntax, 
      int spanStart, int spanEnd, CcgSyntaxTree left, CcgSyntaxTree right, List<String> words,
      List<String> posTags, HeadedSyntacticCategory headedSyntax) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.originalSyntax = Preconditions.checkNotNull(originalSyntax);
    this.spanStart = spanStart;
    this.spanEnd = spanEnd;
    Preconditions.checkArgument(spanEnd >= spanStart);
    
    this.left = left;
    this.right = right;
            
    // Either both left and right are null, or only one is.
    Preconditions.checkArgument(!(left == null ^ right == null));
    Preconditions.checkArgument(left == null ^ words == null);
    
    this.words = words;
    this.posTags = posTags;
    this.headedSyntax = headedSyntax;
    Preconditions.checkArgument(headedSyntax == null || headedSyntax.isCanonicalForm(),
        "Illegal headed syntactic category %s", headedSyntax);
  }

  /**
   * Create a terminal CCG parse tree, with an optional unary rule application.
   * The tree has {@code syntax} as its root syntactic category, and spans the 
   * given set of {@code words} and {@code posTags}. {@code spanStart} and 
   * {@code spanEnd} are the word indexes of the tree's span in the sentence.
   *  
   * @param syntax
   * @param originalSyntax
   * @param spanStart
   * @param spanEnd
   * @param words
   * @param posTags
   * @param headedSyntax
   * @return
   */
  public static CcgSyntaxTree createTerminal(SyntacticCategory syntax,
      SyntacticCategory originalSyntax, int spanStart, int spanEnd, List<String> words,
      List<String> posTags, HeadedSyntacticCategory headedSyntax) {
    Preconditions.checkNotNull(words);
    Preconditions.checkNotNull(posTags);
    return new CcgSyntaxTree(syntax, originalSyntax, spanStart, spanEnd, null, null, words,
        posTags, headedSyntax);
  }
  
  public static CcgSyntaxTree createNonterminal(SyntacticCategory syntax,
      SyntacticCategory originalSyntax, CcgSyntaxTree left, CcgSyntaxTree right) {
    int spanStart = left.getSpanStart();
    int spanEnd = right.getSpanEnd();
    return new CcgSyntaxTree(syntax, originalSyntax, spanStart, spanEnd, left, right, null, null,
        null);
  }

  public SyntacticCategory getRootSyntax() {
    return syntax;
  }

  public CcgSyntaxTree replaceRootSyntax(SyntacticCategory newRoot) {
    return new CcgSyntaxTree(newRoot, getPreUnaryRuleSyntax(), getSpanStart(),
        getSpanEnd(), getLeft(), getRight(), getWords(),
        getPosTags(), headedSyntax);
  }

  public SyntacticCategory getPreUnaryRuleSyntax() {
    return originalSyntax;
  }
  
  public boolean hasUnaryRule() {
    return !syntax.equals(originalSyntax);
  }
  
  public boolean isTerminal() {
    return left == null;
  }
  
  public List<String> getWords() {
    return words;
  }
  
  public List<String> getPosTags() {
    return posTags;
  }

  public HeadedSyntacticCategory getHeadedSyntacticCategory() {
    return headedSyntax;
  }

  public List<String> getAllSpannedWords() {
    List<String> spannedWords = Lists.newArrayList();
    getAllSpannedWordsHelper(spannedWords);
    return spannedWords;
  }

  private void getAllSpannedWordsHelper(List<String> wordAccumulator) {
    if (!isTerminal()) {
      left.getAllSpannedWordsHelper(wordAccumulator);
      right.getAllSpannedWordsHelper(wordAccumulator);
    } else {
      wordAccumulator.addAll(words);
    }
  }
  
  public List<String> getAllSpannedPosTags() {
    List<String> spannedPosTags = Lists.newArrayList();
    getAllSpannedPosTagsHelper(spannedPosTags);
    return spannedPosTags;    
  }

  private void getAllSpannedPosTagsHelper(List<String> tagAccumulator) {
    if (!isTerminal()) {
      left.getAllSpannedPosTagsHelper(tagAccumulator);
      right.getAllSpannedPosTagsHelper(tagAccumulator);
    } else {
      tagAccumulator.addAll(posTags);
    }
  }
  
  public List<HeadedSyntacticCategory> getAllSpannedHeadedSyntacticCategories() {
    List<HeadedSyntacticCategory> categories = Lists.newArrayList();
    getAllSpannedHeadedSyntacticCategoriesHelper(categories);
    return categories;    
  }

  private void getAllSpannedHeadedSyntacticCategoriesHelper(List<HeadedSyntacticCategory> categories) {
    if (!isTerminal()) {
      left.getAllSpannedHeadedSyntacticCategoriesHelper(categories);
      right.getAllSpannedHeadedSyntacticCategoriesHelper(categories);
    } else {
      categories.add(headedSyntax);
    }
  }

  /**
   * Gets the syntactic categories assigned to the words in
   * this parse.
   * 
   * @return
   */
  public List<SyntacticCategory> getAllSpannedLexiconEntries() {
    List<SyntacticCategory> categories = Lists.newArrayList();
    getAllSpannedLexiconEntriesHelper(categories);
    return categories;
  }

  private void getAllSpannedLexiconEntriesHelper(List<SyntacticCategory> accumulator) {
    if (!isTerminal()) {
      left.getAllSpannedLexiconEntriesHelper(accumulator);
      right.getAllSpannedLexiconEntriesHelper(accumulator);
    } else {
      accumulator.add(originalSyntax);
    }
  }
  
  public SyntacticCategory getLexiconEntryForWordIndex(int wordIndex) {
    Preconditions.checkArgument(spanStart <= wordIndex && wordIndex <= spanEnd);
    
    if (isTerminal()) {
      return originalSyntax;
    } else {
      if (wordIndex <= left.getSpanEnd()) {
        return left.getLexiconEntryForWordIndex(wordIndex);
      } else {
        return right.getLexiconEntryForWordIndex(wordIndex);
      }
    }
  }

  public CcgSyntaxTree getLeft() {
    return left;
  }
  
  public CcgSyntaxTree getRight() {
    return right;
  }
  
  public int getSpanStart() {
    return spanStart;
  }
  
  public int getSpanEnd() {
    return spanEnd;
  }
  
  public Set<CcgRuleSchema> getObservedBinaryRules() {
    Set<CcgRuleSchema> rules = Sets.newHashSet();
    getObservedBinaryRules(rules);
    return rules;
  }
  
  private void getObservedBinaryRules(Set<CcgRuleSchema> rules) {
    if (!isTerminal()) {
      rules.add(new CcgRuleSchema(left.getRootSyntax(), right.getRootSyntax(), getPreUnaryRuleSyntax()));
      left.getObservedBinaryRules(rules);
      right.getObservedBinaryRules(rules);
    }
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("<");
    sb.append(syntax);
    sb.append("_");
    sb.append(originalSyntax);
    sb.append(spanStart);
    sb.append(".");
    sb.append(spanEnd);
    if (!isTerminal()) {
      sb.append(" ");
      sb.append(left.toString());
      sb.append(" ");
      sb.append(right.toString());
    } else {
      sb.append(" ");
      sb.append(Joiner.on(" ").join(words));
    }
    sb.append(">");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((left == null) ? 0 : left.hashCode());
    result = prime * result + ((originalSyntax == null) ? 0 : originalSyntax.hashCode());
    result = prime * result + ((posTags == null) ? 0 : posTags.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
    result = prime * result + spanEnd;
    result = prime * result + spanStart;
    result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
    result = prime * result + ((words == null) ? 0 : words.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CcgSyntaxTree other = (CcgSyntaxTree) obj;
    if (left == null) {
      if (other.left != null)
        return false;
    } else if (!left.equals(other.left))
      return false;
    if (originalSyntax == null) {
      if (other.originalSyntax != null)
        return false;
    } else if (!originalSyntax.equals(other.originalSyntax))
      return false;
    if (posTags == null) {
      if (other.posTags != null)
        return false;
    } else if (!posTags.equals(other.posTags))
      return false;
    if (right == null) {
      if (other.right != null)
        return false;
    } else if (!right.equals(other.right))
      return false;
    if (spanEnd != other.spanEnd)
      return false;
    if (spanStart != other.spanStart)
      return false;
    if (syntax == null) {
      if (other.syntax != null)
        return false;
    } else if (!syntax.equals(other.syntax))
      return false;
    if (words == null) {
      if (other.words != null)
        return false;
    } else if (!words.equals(other.words))
      return false;
    return true;
  }
}
