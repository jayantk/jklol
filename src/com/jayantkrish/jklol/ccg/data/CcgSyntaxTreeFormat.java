package com.jayantkrish.jklol.ccg.data;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.data.LineDataFormat;
import com.jayantkrish.jklol.util.ArrayUtils;

public class CcgSyntaxTreeFormat extends LineDataFormat<CcgSyntaxTree> {
  
  public CcgSyntaxTree parseFrom(String treeString) {
    return parseFromString(treeString, 0);
  }
  
  private static CcgSyntaxTree parseFromString(String treeString, int numWordsOnLeft) {
    int curDepth = 0;
    List<Integer> treeStartIndexes = Lists.newArrayList();
    List<Integer> treeEndIndexes = Lists.newArrayList();
    for (int i = 0; i < treeString.length(); i++) {
      if (treeString.charAt(i) == '<') {
        if (curDepth == 0) {
          treeStartIndexes.add(i);
        }
        curDepth++;
      } else if (treeString.charAt(i) == '>') {
        curDepth--;
        if (curDepth == 0) {
          treeEndIndexes.add(i);
        }
      }
    }
    
    Preconditions.checkState(treeStartIndexes.size() == treeEndIndexes.size());
    int size = treeStartIndexes.size();
    Preconditions.checkState(size >= 0 || size <= 2);
    if (size == 0) {
      String[] parts = treeString.trim().split(" ");

      String syntaxPart = parts[0];
      SyntacticCategory rootCat = null;
      SyntacticCategory preUnaryCat = null;
      if (syntaxPart.trim().contains("_")) {
        String[] syntaxPartParts = syntaxPart.split("_");
        rootCat = SyntacticCategory.parseFrom(syntaxPartParts[0]);
        preUnaryCat = SyntacticCategory.parseFrom(syntaxPartParts[1]);
      } else {
        rootCat = SyntacticCategory.parseFrom(syntaxPart);
        preUnaryCat = rootCat;
      }
      
      int numWords = (parts.length - 1)/ 2;
      List<String> posTags = Arrays.asList(ArrayUtils.copyOfRange(parts, 1, 1 + numWords));
      List<String> words = Arrays.asList(ArrayUtils.copyOfRange(parts, 1 + numWords, 1 + (numWords*2)));
      Preconditions.checkState(posTags.size() == words.size());

      return CcgSyntaxTree.createTerminal(rootCat, preUnaryCat, numWordsOnLeft, 
          numWordsOnLeft + words.size() - 1, words, posTags, null);
    } if (size == 1) {
      return parseFromString(treeString.substring(treeStartIndexes.get(0) + 1, treeEndIndexes.get(0)),
          numWordsOnLeft);
    } else {
      CcgSyntaxTree leftTree = parseFromString(treeString.substring(treeStartIndexes.get(0) + 1,
          treeEndIndexes.get(0)), numWordsOnLeft);
      CcgSyntaxTree rightTree = parseFromString(treeString.substring(treeStartIndexes.get(1) + 1,
          treeEndIndexes.get(1)), leftTree.getSpanEnd() + 1);
      
      String syntaxPart = treeString.substring(0, treeStartIndexes.get(0));
      SyntacticCategory rootCat = null;
      SyntacticCategory preUnaryCat = null;
      if (syntaxPart.trim().contains("_")) {
        String[] syntaxPartParts = syntaxPart.split("_");
        rootCat = SyntacticCategory.parseFrom(syntaxPartParts[0]);
        preUnaryCat = SyntacticCategory.parseFrom(syntaxPartParts[1]);
      } else {
        rootCat = SyntacticCategory.parseFrom(syntaxPart);
        preUnaryCat = rootCat;
      }

      return CcgSyntaxTree.createNonterminal(rootCat, preUnaryCat, leftTree, rightTree);
    }
  }
}
