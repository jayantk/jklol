package com.jayantkrish.jklol.experiments.geoquery;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public class LexiconEntryTemplate {
  private final HeadedSyntacticCategory syntax;

  private final List<Type> typeSignature;
  private final Expression2 lfTemplate;

  public LexiconEntryTemplate(HeadedSyntacticCategory syntax, List<Type> typeSignature,
      Expression2 lfTemplate) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.typeSignature = ImmutableList.copyOf(typeSignature);
    this.lfTemplate = Preconditions.checkNotNull(lfTemplate);
  }
  
  public HeadedSyntacticCategory getSyntax() {
    return syntax;
  }

  public List<Type> getTypeSignature() {
    return typeSignature;
  }

  public Expression2 getLfTemplate() {
    return lfTemplate;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((lfTemplate == null) ? 0 : lfTemplate.hashCode());
    result = prime * result + ((syntax == null) ? 0 : syntax.hashCode());
    result = prime * result + ((typeSignature == null) ? 0 : typeSignature.hashCode());
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
    LexiconEntryTemplate other = (LexiconEntryTemplate) obj;
    if (lfTemplate == null) {
      if (other.lfTemplate != null)
        return false;
    } else if (!lfTemplate.equals(other.lfTemplate))
      return false;
    if (syntax == null) {
      if (other.syntax != null)
        return false;
    } else if (!syntax.equals(other.syntax))
      return false;
    if (typeSignature == null) {
      if (other.typeSignature != null)
        return false;
    } else if (!typeSignature.equals(other.typeSignature))
      return false;
    return true;
  }
}