package com.jayantkrish.jklol.ccg;

import com.google.common.base.Preconditions;

/**
 * Information about the use of a lexicon entry within a CCG parse.
 * Includes which CCG lexicon the entry originated from, the reason
 * the entry was created, and the spans of text covered.  
 * 
 * @author jayantk
 *
 */
public class LexiconEntryInfo {

  // The CCG category of the entry.
  private final CcgCategory category;

  // The reason why this category was created in the parse.
  private final Object lexiconTrigger;
  // Which lexicon in the CCG parser that this lexicon entry came from.
  private final int lexiconIndex;
  
  // The text span covered by this lexicon entry.
  private final int spanStart;
  private final int spanEnd;
  
  // The text span that triggered adding this entry to the parse.
  // This is a subspan of the above span that is used to handle
  // word skipping.
  private final int triggerSpanStart;
  private final int triggerSpanEnd;
  
  public LexiconEntryInfo(CcgCategory category, Object lexiconTrigger, int lexiconIndex,
      int spanStart, int spanEnd, int triggerSpanStart, int triggerSpanEnd) {
    this.category = Preconditions.checkNotNull(category);
    this.lexiconTrigger = Preconditions.checkNotNull(lexiconTrigger);
    this.lexiconIndex = lexiconIndex;
    this.spanStart = spanStart;
    this.spanEnd = spanEnd;
    this.triggerSpanStart = triggerSpanStart;
    this.triggerSpanEnd = triggerSpanEnd;
  }

  public CcgCategory getCategory() {
    return category;
  }

  /**
   * Gets the trigger in the lexicon that caused this 
   * lexicon entry to be created and added to the parse. The
   * trigger is usually the words in the sentence (or some 
   * processed version of that), but could be something else.
   * 
   * @return
   */
  public Object getLexiconTrigger() {
    return lexiconTrigger;
  }

  public int getLexiconIndex() {
    return lexiconIndex;
  }

  public int getSpanStart() {
    return spanStart;
  }

  public int getSpanEnd() {
    return spanEnd;
  }

  public int getTriggerSpanStart() {
    return triggerSpanStart;
  }

  public int getTriggerSpanEnd() {
    return triggerSpanEnd;
  }
  
  /**
   * Returns a copy of {@code this} with the CCG category replaced
   * by {@code newCategory}.
   * 
   * @param newCategory
   * @return
   */
  public LexiconEntryInfo replaceCategory(CcgCategory newCategory) {
    return new LexiconEntryInfo(newCategory, lexiconTrigger, lexiconIndex, spanStart, spanEnd,
        triggerSpanStart, triggerSpanEnd);
  }
  
  @Override
  public String toString() {
    return triggerSpanStart + "," + triggerSpanEnd + ":" + lexiconTrigger.toString() + ":" + category.toCsvString();
  }
}
