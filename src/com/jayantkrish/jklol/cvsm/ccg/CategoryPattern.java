package com.jayantkrish.jklol.cvsm.ccg;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.Expression;

public interface CategoryPattern extends Serializable {
  
  boolean matches(List<String> words, SyntacticCategory category,
      Collection<DependencyStructure> deps);

  /**
   * Returns {@code null} if this pattern does not match the input. 
   */
  Expression getLogicalForm(List<String> words, SyntacticCategory category,
      Collection<DependencyStructure> deps);
}
