package com.jayantkrish.jklol.ccg.augment;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public interface CategoryPattern extends Serializable {
  
  boolean matches(List<String> words, SyntacticCategory category,
      Collection<DependencyStructure> deps);

  /**
   * Returns {@code null} if this pattern does not match the input. 
   */
  Expression2 getLogicalForm(List<String> words, SyntacticCategory category,
      Collection<DependencyStructure> deps);
}
