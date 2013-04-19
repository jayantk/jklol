package com.jayantkrish.jklol.cvsm.ccg;

import java.io.Serializable;
import java.util.List;

import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.Expression;

public interface CategoryPattern extends Serializable {
  
  boolean matches(List<String> words, HeadedSyntacticCategory category);

  /**
   * Returns {@code null} if this pattern does not match the input. 
   */
  Expression getLogicalForm(List<String> words, HeadedSyntacticCategory category);
}
