package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmExample {

  private final Expression2 logicalForm;

  // May be null
  private final Tensor targets;
  // May be null.
  private final CcgParse parse;

  public CvsmExample(Expression2 logicalForm, Tensor targetDistribution, CcgParse parse) {
    this.logicalForm = Preconditions.checkNotNull(logicalForm);
    this.targets = targetDistribution;
    this.parse = parse;
  }

  public Expression2 getLogicalForm() {
    return logicalForm;
  }

  public Tensor getTargets() {
    return targets;
  }

  public CcgParse getParse() {
    return parse;
  }
}
