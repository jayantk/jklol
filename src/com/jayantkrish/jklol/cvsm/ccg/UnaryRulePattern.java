package com.jayantkrish.jklol.cvsm.ccg;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.util.CsvParser;

public class UnaryRulePattern {
  
  private final SyntacticCategory inputSyntax;
  private final SyntacticCategory outputSyntax;
  private final LambdaExpression logicalForm;

  public UnaryRulePattern(SyntacticCategory inputSyntax, SyntacticCategory outputSyntax,
      LambdaExpression logicalForm) {
    this.inputSyntax = Preconditions.checkNotNull(inputSyntax);
    this.outputSyntax = Preconditions.checkNotNull(outputSyntax);
    this.logicalForm = Preconditions.checkNotNull(logicalForm);
  }
  
  public static UnaryRulePattern parseFrom(String line) {
    String[] parts = CsvParser.noEscapeParser().parseLine(line);
    Preconditions.checkArgument(parts.length == 3);

    return new UnaryRulePattern(SyntacticCategory.parseFrom(parts[0]), SyntacticCategory.parseFrom(parts[1]),
        (LambdaExpression) ExpressionParser.lambdaCalculus().parseSingleExpression(parts[2]));
  }

  public boolean matches(CcgUnaryRule rule) {
    return inputSyntax.isUnifiableWith(rule.getInputSyntacticCategory().getSyntax()) 
        && outputSyntax.isUnifiableWith(rule.getResultSyntacticCategory().getSyntax());
  }

  public LambdaExpression getLogicalForm(CcgUnaryRule rule) {
    return logicalForm;
  }
}
