package com.jayantkrish.jklol.ccg;


/**
 * A semantic predicate paired with the index of the word that
 * instantiated it. This class disambiguates between multiple
 * distinct instantiations of a predicate in a sentence. Such
 * instantiations occur, for example, when a single word occurs
 * multiple times in the sentence.
 * 
 * @author jayant
 */
public class IndexedPredicate {
  // The name of the predicate.
  private final String predicate;

  // The word index that created predicate.
  private final int wordIndex;

  public IndexedPredicate(String head, int wordIndex) {
    this.predicate = head;
    this.wordIndex = wordIndex;
  }

  public String getHead() {
    return predicate;
  }

  public int getHeadIndex() {
    return wordIndex;
  }

  @Override
  public String toString() {
    return predicate + ":" + wordIndex;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((predicate == null) ? 0 : predicate.hashCode());
    result = prime * result + wordIndex;
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
    IndexedPredicate other = (IndexedPredicate) obj;
    if (predicate == null) {
      if (other.predicate != null)
        return false;
    } else if (!predicate.equals(other.predicate))
      return false;
    if (wordIndex != other.wordIndex)
      return false;
    return true;
  }
}
