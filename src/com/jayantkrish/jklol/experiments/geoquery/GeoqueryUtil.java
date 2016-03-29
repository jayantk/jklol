package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;

import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;

public class GeoqueryUtil {
  public static final String FEATURE_ANNOTATION_NAME = "features";
  
  public static ExpressionSimplifier getExpressionSimplifier() {
    return new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
  }
}
