package com.jayantkrish.jklol.experiments.wikitables;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

public class WikiTableMentionAnnotation {
    
  public static final String HEADING = "heading";
  public static final String VALUE = "value";
  
  public static final String NAME = "mentionAnnotation";
  
  private final List<String> mentions;
  private final List<String> mentionTypes;
  private final List<Integer> tokenStarts;
  private final List<Integer> tokenEnds;
  
  public WikiTableMentionAnnotation(List<String> mentions, List<String> mentionTypes,
      List<Integer> tokenStarts, List<Integer> tokenEnds) {
    this.mentions = ImmutableList.copyOf(mentions);
    this.mentionTypes = ImmutableList.copyOf(mentionTypes);
    this.tokenStarts = ImmutableList.copyOf(tokenStarts);
    this.tokenEnds = ImmutableList.copyOf(tokenEnds);
  }
  
  public Set<String> getMentionsWithType(String type) {
    Set<String> things = Sets.newHashSet();
    for (int i = 0; i < mentionTypes.size(); i++) {
      if (mentionTypes.get(i).equals(type)) {
        things.add(mentions.get(i));
      }
    }
    return things;
  }

  public List<String> getMentions() {
    return mentions;
  }

  public List<String> getMentionTypes() {
    return mentionTypes;
  }

  public List<Integer> getTokenStarts() {
    return tokenStarts;
  }

  public List<Integer> getTokenEnds() {
    return tokenEnds;
  }
}
