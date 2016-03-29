package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;

import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.LexiconEntry;

public interface Genlex {

  /**
   * 
   * TODO: does this need to take additional arguments?
   * 
   * @param example
   * @return
   */
  public Collection<LexiconEntry> genlex(CcgExample example);
  
}
