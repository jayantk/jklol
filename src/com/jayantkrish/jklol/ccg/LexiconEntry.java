package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * A mapping from a list of words to a {@link CcgCategory}. Each lexicon entry
 * represents a possible syntactic and semantic category for the given list of
 * words. A lexicon is a set of lexicon entries; together, this set defines the
 * structure of a CCG grammar.
 * <p>
 * {@code LexiconEntry} is immutable.
 * 
 * @author jayantk
 */
public class LexiconEntry {
  private final List<String> words;
  private final CcgCategory category;

  // Character that delimits the components of lexicon entries
  // during lexicon entry parsing. 
  private static final char ENTRY_DELIMITER=',';

  public LexiconEntry(List<String> words, CcgCategory category) {
    this.words = ImmutableList.copyOf(words);
    this.category = Preconditions.checkNotNull(category);
  }

  /**
   * Parses a line of a CCG lexicon into a lexicon entry. The expected format is
   * a comma separated tuple:
   * <code>
   * (space delimited word list),(semantic head),(syntactic type),(# delimited semantic dependencies)
   * </code>
   * The last three components of the list represent the CCG category. For example:
   * <code>
   * baseball player,baseball_player,N
   * in,concept:locatedIn,(N\>N)/N,concept:locatedIn 1 ?1#concept:locatedIn 2 ?2 
   * </code>
   * 
   * @param lexiconLine
   * @return
   */
  public static LexiconEntry parseLexiconEntry(String lexiconLine) {
    try {
      String[] parts = new CSVParser(ENTRY_DELIMITER, CSVParser.DEFAULT_QUOTE_CHARACTER, 
          CSVParser.NULL_CHARACTER).parseLine(lexiconLine);

      // Add the lexicon word sequence to the lexicon.
      String wordPart = parts[0];
      if (parts.length >= 4) {
        return new LexiconEntry(Arrays.asList(wordPart.split(" ")),
            CcgCategory.parseFrom(parts[1], parts[2], parts[3]));
      } else {
        return new LexiconEntry(Arrays.asList(wordPart.split(" ")),
            CcgCategory.parseFrom(parts[1], parts[2], ""));
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid lexicon line: " + lexiconLine, e);
    }
  }

  /**
   * Gets the words which can invoke this lexicon entry.
   * 
   * @return
   */
  public List<String> getWords() {
    return words;
  }

  /**
   * Gets the syntactic/semantic category which the given words may parse to in
   * a CCG parse.
   * 
   * @return
   */
  public CcgCategory getCategory() {
    return category;
  }

  @Override
  public String toString() {
    return words + ":" + category;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((category == null) ? 0 : category.hashCode());
    result = prime * result + ((words == null) ? 0 : words.hashCode());
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
    LexiconEntry other = (LexiconEntry) obj;
    if (category == null) {
      if (other.category != null)
        return false;
    } else if (!category.equals(other.category))
      return false;
    if (words == null) {
      if (other.words != null)
        return false;
    } else if (!words.equals(other.words))
      return false;
    return true;
  }
}
