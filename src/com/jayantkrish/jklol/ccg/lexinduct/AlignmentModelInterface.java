package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.List;

public interface AlignmentModelInterface {

  public AlignedExpressionTree getBestAlignment(AlignmentExample example);
  
  /**
   * Gets the {@code num} (approximate) best alignments for {@code example}.
   * 
   * @param example
   * @param num
   * @return
   */
  public List<AlignedExpressionTree> getBestAlignments(AlignmentExample example, int num);
  
}
