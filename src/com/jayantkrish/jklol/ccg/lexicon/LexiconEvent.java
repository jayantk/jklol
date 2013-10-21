package com.jayantkrish.jklol.ccg.lexicon;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.sequence.LocalContext;

/**
 * The event which a featurized lexicon can observe during
 * feature generation.
 * 
 * @author jayant
 *
 */
public class LexiconEvent {
  private final CcgCategory category;
  private final List<String> terminals;
  private final LocalContext<WordAndPos> context;

  public LexiconEvent(CcgCategory category, List<String> terminals,
      LocalContext<WordAndPos> context) {
    this.category = Preconditions.checkNotNull(category);
    this.terminals = Preconditions.checkNotNull(terminals);
    this.context = Preconditions.checkNotNull(context);
  }

  public CcgCategory getCategory() {
    return category;
  }
  
  public List<String> getTerminals() {
    return terminals;
  }

  /**
   * Gets the surrounding context for these terminals in the
   * sentence. The context is centered around the rightmost word
   * in terminals.
   * 
   * @return
   */
  public LocalContext<WordAndPos> getContext() {
    return context;
  }
}