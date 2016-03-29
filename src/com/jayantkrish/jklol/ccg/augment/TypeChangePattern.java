package com.jayantkrish.jklol.ccg.augment;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.util.CsvParser;

public class TypeChangePattern {
  
  private final SyntacticCategory inputPattern;
  private final SyntacticCategory outputPattern;
  
  private final Expression2 expression;
  
  public TypeChangePattern(SyntacticCategory inputPattern,
      SyntacticCategory outputPattern, Expression2 expression) {
    this.inputPattern = Preconditions.checkNotNull(inputPattern);
    this.outputPattern = Preconditions.checkNotNull(outputPattern);
    this.expression = Preconditions.checkNotNull(expression);
  }
  
  public static TypeChangePattern parseFrom(String string) {
    String[] parts = CsvParser.noEscapeParser().parseLine(string);
    Preconditions.checkArgument(parts.length == 4);
    
    SyntacticCategory input = SyntacticCategory.parseFrom(parts[1]);
    SyntacticCategory output= SyntacticCategory.parseFrom(parts[2]);
    Expression2 expression = ExpressionParser.expression2().parse(parts[3]);
    
    return new TypeChangePattern(input, output, expression);
  }

  public boolean matches(SyntacticCategory input, SyntacticCategory output) {
    return inputPattern.isUnifiableWith(input) && outputPattern.isUnifiableWith(output);
  }
  
  public Expression2 getLogicalForm() {
    return expression;
  }
}
