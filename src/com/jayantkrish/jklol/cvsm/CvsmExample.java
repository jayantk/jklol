package com.jayantkrish.jklol.cvsm;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.tensor.Tensor;

public class CvsmExample {

  private final Expression logicalForm;
  private final Tensor targets;

  // May be null.
  private final CcgParse parse;

  public CvsmExample(Expression logicalForm, Tensor targetDistribution, CcgParse parse) {
    this.logicalForm = Preconditions.checkNotNull(logicalForm);
    this.targets = Preconditions.checkNotNull(targetDistribution);
    this.parse = parse;
  }

  public Expression getLogicalForm() {
    return logicalForm;
  }

  public Tensor getTargets() {
    return targets;
  }

  public CcgParse getParse() {
    return parse;
  }
}
