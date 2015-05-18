package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.VariableNumMap;

/**
 * Implementations of common {@code CcgLexicon} methods.
 * 
 * @author jayant
 *
 */
public abstract class AbstractCcgLexicon implements CcgLexicon {
  private static final long serialVersionUID = 3L;
  
  private final VariableNumMap terminalVar;

  public AbstractCcgLexicon(VariableNumMap terminalVar) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
  }

  @Override
  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }
}
