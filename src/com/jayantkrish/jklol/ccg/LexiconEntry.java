package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.util.ArrayUtils;
import com.jayantkrish.jklol.util.CsvParser;

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
   * (space delimited word list),(syntactic type),(list of variable assignments and unfilled semantic dependencies)
   * </code>
   * The final two components of the list represent the CCG category. Examples:
   * <code>
   * baseball player,N{0},0 baseball_player
   * in,((N{1}\N{1}){0}/N{2}){0},0 concept:locatedIn,concept:locatedIn 1 1,concept:locatedIn 2 2 
   * </code>
   * 
   * @param lexiconLine
   * @return
   */
  public static LexiconEntry parseLexiconEntry(String lexiconLine) {
    String[] parts = getCsvParser().parseLine(lexiconLine);

    // Add the lexicon word sequence to the lexicon.
    String wordPart = parts[0];
    List<String> words = Lists.newArrayList();
    String[] wordArray = wordPart.split(" ");
    for (int i = 0; i < wordArray.length; i++) {
      words.add(wordArray[i].intern());
    }

    CcgCategory category = CcgCategory.parseFrom(ArrayUtils.copyOfRange(parts, 1, parts.length));
    return new LexiconEntry(words, category);
  }

  public static CsvParser getCsvParser() {
    return new CsvParser(ENTRY_DELIMITER, CsvParser.DEFAULT_QUOTE, '~');
  }

  public static LexiconEntry parseFromJson(String line) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(line);
      return LexiconEntry.parseFromJson(root);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static LexiconEntry parseFromJson(JsonNode root) {
    JsonNode wordsNode = root.get("words");
    Preconditions.checkState(wordsNode.isArray());
    List<String> words = Lists.newArrayList();
    for (JsonNode word : wordsNode) {
      words.add(word.asText().intern());
    }

    JsonNode ccgCategoryNode = root.get("ccgCategory");
    CcgCategory category = CcgCategory.parseFromJson(ccgCategoryNode);

    return new LexiconEntry(words, category);
  }
  
  public static List<LexiconEntry> parseLexiconEntries(Iterable<String> unfilteredLexiconLines) {
    // Remove comments, which are lines that begin with "#".
    List<String> lexiconLines = Lists.newArrayList();
    for (String line : unfilteredLexiconLines) {
      if (!line.startsWith("#")) {
        lexiconLines.add(line);
      }
    }

    List<LexiconEntry> lexiconEntries = Lists.newArrayList(); 
    for (String lexiconLine : lexiconLines) {
      // Create the CCG category.
      lexiconEntries.add(LexiconEntry.parseLexiconEntry(lexiconLine));
    }
    return lexiconEntries;
  }

  public static List<LexiconEntry> parseLexiconEntriesJson(Iterable<String> lexiconLines) {
    // Remove comments, which are lines that begin with "#".
    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    for (String line : lexiconLines) {
      if (!line.startsWith("#")) {
        lexiconEntries.add(LexiconEntry.parseFromJson(line));
      }
    }
    return lexiconEntries;
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

  public String toCsvString() {
    return "\"" + getCsvParser().escape(Joiner.on(" ").join(words))
        + "\"," + category.toCsvString();  
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
