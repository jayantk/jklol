package com.jayantkrish.jklol.ccg.augment;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgUnaryRule;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.util.CsvParser;

public class UnaryRulePattern {
  
  private final SyntacticCategory inputSyntax;
  private final SyntacticCategory outputSyntax;
  private final Expression2 logicalForm;

  public UnaryRulePattern(SyntacticCategory inputSyntax, SyntacticCategory outputSyntax,
      Expression2 logicalForm) {
    this.inputSyntax = Preconditions.checkNotNull(inputSyntax);
    this.outputSyntax = Preconditions.checkNotNull(outputSyntax);
    this.logicalForm = Preconditions.checkNotNull(logicalForm);
  }
  
  public static UnaryRulePattern parseFrom(String line) {
    String[] parts = new CsvParser(',', '"', CsvParser.DEFAULT_ESCAPE).parseLine(line);
    Preconditions.checkArgument(parts.length == 3);

    return new UnaryRulePattern(SyntacticCategory.parseFrom(parts[0]), SyntacticCategory.parseFrom(parts[1]),
        ExpressionParser.expression2().parse(parts[2]));
  }

  public boolean matches(CcgUnaryRule rule) {
    return inputSyntax.isUnifiableWith(rule.getInputSyntacticCategory().getSyntax()) 
        && outputSyntax.isUnifiableWith(rule.getResultSyntacticCategory().getSyntax());
  }

  public Expression2 getLogicalForm(CcgUnaryRule rule) {
    return logicalForm;
  }
}
