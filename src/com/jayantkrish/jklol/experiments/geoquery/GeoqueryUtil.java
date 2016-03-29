package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
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
  
  public static TypeDeclaration getTypeDeclaration() { 
    Map<String, String> typeReplacements = Maps.newHashMap();
    typeReplacements.put("lo", "e");
    typeReplacements.put("c", "e");
    typeReplacements.put("co", "e");
    typeReplacements.put("s", "e");
    typeReplacements.put("r", "e");
    typeReplacements.put("l", "e");
    typeReplacements.put("m", "e");
    typeReplacements.put("p", "e");
    return new ExplicitTypeDeclaration(typeReplacements);
  }

}
