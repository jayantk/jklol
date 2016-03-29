package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

/**
 * A lexeme pairs a token sequence with a set of predicates.
 * Lexemes are part of factored lexicons, and can be used to populate
 * a syntactic category / logical form template pair. 
 * 
 * @author jayantk
 *
 */
public class Lexeme {
  
  private List<String> tokens;
  private Set<String> predicates;
  
  public Lexeme(List<String> tokens, Set<String> predicates) {
    this.tokens = ImmutableList.copyOf(tokens);
    this.predicates = Sets.newHashSet(predicates);
  }
  
  public static Set<List<String>> lexemesToTokens(Collection<Lexeme> lexemes) {
    Set<List<String>> tokens = Sets.newHashSet();
    for (Lexeme lexeme : lexemes) {
      tokens.add(lexeme.getTokens());
    }
    return tokens;
  }

  public List<String> getTokens() {
    return tokens;
  }
  
  public Set<String> getPredicates() {
    return predicates;
  }
  
  @Override
  public String toString() {
    return tokens + "/" + predicates;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((predicates == null) ? 0 : predicates.hashCode());
    result = prime * result + ((tokens == null) ? 0 : tokens.hashCode());
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
    if (tokens == null) {
      if (other.tokens != null)
        return false;
    } else if (!tokens.equals(other.tokens))
      return false;
    return true;
  }
}
