package com.jayantkrish.jklol.ccg.augment;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgBinaryRule;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.util.CsvParser;

public class BinaryRulePattern {

  private final SyntacticCategory leftSyntax;
  private final SyntacticCategory rightSyntax;
  private final SyntacticCategory outputSyntax;
  private final LambdaExpression logicalForm;

  public BinaryRulePattern(SyntacticCategory leftSyntax, SyntacticCategory rightSyntax,
      SyntacticCategory outputSyntax, LambdaExpression logicalForm) {
    this.leftSyntax = Preconditions.checkNotNull(leftSyntax);
    this.rightSyntax = Preconditions.checkNotNull(rightSyntax);
    this.outputSyntax = Preconditions.checkNotNull(outputSyntax);
    this.logicalForm = Preconditions.checkNotNull(logicalForm);
  }

  public static BinaryRulePattern parseFrom(String line) {
    String[] parts = new CsvParser(',', '"', CsvParser.DEFAULT_ESCAPE).parseLine(line);
    Preconditions.checkArgument(parts.length == 4);

    return new BinaryRulePattern(SyntacticCategory.parseFrom(parts[0]),
        SyntacticCategory.parseFrom(parts[1]), SyntacticCategory.parseFrom(parts[2]),
        (LambdaExpression) ExpressionParser.lambdaCalculus().parseSingleExpression(parts[3]));
  }

  public boolean matches(CcgBinaryRule rule) {
    return leftSyntax.isUnifiableWith(rule.getLeftSyntacticType().getSyntax()) 
        && rightSyntax.isUnifiableWith(rule.getRightSyntacticType().getSyntax())
        && outputSyntax.isUnifiableWith(rule.getParentSyntacticType().getSyntax());
  }

  public LambdaExpression getLogicalForm(CcgBinaryRule rule) {
    return logicalForm;
  }
}
