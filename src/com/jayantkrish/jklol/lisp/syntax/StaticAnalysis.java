package com.jayantkrish.jklol.lisp.syntax;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.lisp.SExpression;

public class StaticAnalysis {

  private final SExpression expression;

  private StaticAnalysis() {
    
  }
  
  public static StaticAnalysis fromExpression(SExpression expression) {
    
  }

  public Set<String> getFreeVariables() {
    Set<String> freeVariables = Sets.newHashSet();
    
    for (int i = 0; i < expression.size(); i++) {
      SExpression sub = expression.getSubexpression(i);
      if (sub.isConstant()) {
        String constant = sub.getConstant();
        if (constant.equals("lambda")) {
          // Read off the variables and add them to a static scope.
          SExpression lambdaExpression = expression.getSubexpression(i - 1);
          List<SExpression> subexpressions = lambdaExpression.getSubexpressions();
          // First expression is "lambda", last is body.
          List<String> boundVarNames = Lists.newArrayList();
          for (int j = 1; j < subexpressions.size() - 1; j++) {
            Preconditions.checkState(subexpressions.get(j).isConstant(),
                "Illegal lambda expression %s", lambdaExpression);
            boundVarNames.add(subexpressions.get(j).getConstant());
          }
          
          // TODO: add to scope, track last index to pop off scope

        } else if (scope.isUnbound(constant)) {
          freeVariables.add(sub.getConstant());
        }
      }
    }
    return freeVariables;
  }

  // warning: need to freshen rest / body to avoid capturing parts of val
  // ((lambda ?x ?rest+ ?body) ?val ?valrest+) -> 
  // ((lambda ?rest sub(?body, ?x, ?val)) ?valrest)
  //
  // ((lambda ?x ?body) ?val) ->
  // sub(?body, ?x, ?val)
  //
  // List valued variables (* and +) are inlined automatically
  // (and<t,t> ?first* (and<t,t> ?inner*) ?last*)
  // (and<t,t> ?first ?inner ?last)
  //
  // (and ?first* (exists ?vars* ?body) ?last*)
  // (exists ?vars (and ?first ?body ?last))
  //
  // (exists ?vars* (exists ?vars2* ?body))
  // (exists ?vars* ?vars2* body)
  
}
