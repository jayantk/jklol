package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;

import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.Scope;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.ScopeSet;

/**
 * Canonicalizes variable names in lambda expressions.
 * 
 * @author jayant
 *
 */
public class VariableCanonicalizationReplacementRule implements ExpressionReplacementRule {

  @Override
  public Expression2 getReplacement(Expression2 input, int index) {
    boolean changed = false;
    Expression2 result = input;

    if (index == 0) {
      ScopeSet scopes = StaticAnalysis.getScopes(input);
      for (int i = 0; i < input.size(); i++) {

        if (StaticAnalysis.isLambda(input, i)) {
          Scope scope = scopes.getScope(i);

          List<String> args = StaticAnalysis.getLambdaArguments(input, i);
          Expression2 body = StaticAnalysis.getLambdaBody(input, i);
          int[] indexes = input.getChildIndexes(i);
          int bodyIndex = indexes[indexes.length - 1];
          int startNum = scope.getNumBindings();
          
          for (int j = 0; j < args.size(); j++) {
            String newVarName = "$" + (startNum + j);

            if (newVarName.equals(args.get(j))) {
              // Variable is already named properly.
              continue;
            }

            // Substitute the new name for the occurrence in the lambda 
            // declaration
            changed = true;
            result = result.substitute(indexes[j + 1], newVarName);

            // Substitute the new name for free occurrences in the body of 
            // the lambda.
            int[] freeIndexes = StaticAnalysis.getIndexesOfFreeVariable(body, args.get(j));
            for (int k = 0; k < freeIndexes.length; k++) {
              result = result.substitute(bodyIndex + freeIndexes[k], newVarName);
            }
          }
        }
      }
    }

    if (changed) {
      return result;
    } else {
      return null;
    }
  }
}
