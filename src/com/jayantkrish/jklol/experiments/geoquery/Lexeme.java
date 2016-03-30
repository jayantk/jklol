package com.jayantkrish.jklol.experiments.geoquery;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

public class Lexeme {
  private final List<String> predicates;
  
  public Lexeme(List<String> predicates) {
    this.predicates = ImmutableList.copyOf(predicates);
  }
  
  public List<String> getPredicates() {
    return predicates;
  }
  
  public List<Type> getTypeSignature(TypeDeclaration typeDeclaration) {
    List<Type> typeSig = Lists.newArrayList();
    for (String predicate : predicates) {
      typeSig.add(StaticAnalysis.inferType(Expression2.constant(predicate), typeDeclaration));
    }
    return typeSig;
  }
  
  @Override
  public String toString() {
    return predicates.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((predicates == null) ? 0 : predicates.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Lexeme other = (Lexeme) obj;
    if (predicates == null) {
      if (other.predicates != null)
        return false;
    } else if (!predicates.equals(other.predicates))
      return false;
    return true;
  }
}