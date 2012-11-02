package com.jayantkrish.jklol.ccg;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;

public class CcgBinaryRule {
  private final SyntacticCategory leftSyntax;
  private final SyntacticCategory rightSyntax;
  private final SyntacticCategory returnSyntax;

  private final int[] unfilledHeads;
  private final long[] unfilledDeps;
  
  private final boolean inheritLeft;
  private final boolean inheritRight;
  
  public CcgBinaryRule(SyntacticCategory leftSyntax, SyntacticCategory rightSyntax, 
      SyntacticCategory returnSyntax, int[] unfilledHeads, long[] unfilledDeps, 
      boolean inheritLeft, boolean inheritRight) {
    this.leftSyntax = leftSyntax;
    this.rightSyntax = rightSyntax;
    this.returnSyntax = returnSyntax;
    
    this.unfilledHeads = unfilledHeads;
    this.unfilledDeps = unfilledDeps;
    
    this.inheritLeft = inheritLeft;
    this.inheritRight = inheritRight;
  }
  
  public static CcgBinaryRule parseFrom(String line) {
    String[] chunks = line.split("###");
    Preconditions.checkArgument(chunks.length >= 3);
    
    String[] syntacticParts = chunks[0].split(" ");
    Preconditions.checkArgument(syntacticParts.length == 3);
    SyntacticCategory leftSyntax = SyntacticCategory.parseFrom(syntacticParts[0]);
    SyntacticCategory rightSyntax = SyntacticCategory.parseFrom(syntacticParts[1]);
    SyntacticCategory returnSyntax = SyntacticCategory.parseFrom(syntacticParts[2]);
    
    boolean inheritLeft = chunks[1].equals("T");
    boolean inheritRight = chunks[2].equals("T");
    
    int[] unfilledHeads = new int[0];
    if (chunks.length >= 4) {
      String[] newHeads = chunks[3].split(",");
      unfilledHeads = new int[newHeads.length];
      for (int i = 0; i < newHeads.length; i++) {
        unfilledHeads[i] = Integer.parseInt(newHeads[i].substring(1));
      }
    }
    
    long[] unfilledDeps = new long[0];
    if (chunks.length >= 5) {
      String[] newDeps = chunks[4].split(" ");
      Preconditions.checkArgument(newDeps.length == 3);
      long subjectNum = Long.parseLong(newDeps[0].substring(1));
      long argNum = Long.parseLong(newDeps[1]);
      long objectNum = Long.parseLong(newDeps[2].substring(1));
      unfilledDeps = new long[1];
      
      unfilledDeps[0] = CcgParser.marshalUnfilledDependency(objectNum, argNum, subjectNum, 0, 0);
    }
    
    return new CcgBinaryRule(leftSyntax, rightSyntax, returnSyntax, unfilledHeads, unfilledDeps,
        inheritLeft, inheritRight);
  }

  /**
   * Gets the expected syntactic type that should occur on the left side 
   * in order to instantiate this rule.
   * 
   * @return
   */
  public SyntacticCategory getLeftSyntacticType() {
    return leftSyntax;
  }

  /**
   * Gets the expected syntactic type that should occur on the right side 
   * in order to instantiate this rule.
   * 
   * @return
   */
  public SyntacticCategory getRightSyntacticType() {
    return rightSyntax;
  }

  public ChartEntry apply(ChartEntry left, ChartEntry right, int leftSpanStart,
      int leftSpanEnd, int leftIndex, int rightSpanStart, int rightSpanEnd,
      int rightIndex) {

    int[] newHeadNums = new int[0];
    int[] newHeadIndexes = new int[0];
    int[] newUnfilledHeads = Arrays.copyOf(unfilledHeads, unfilledHeads.length);
    long[] newUnfilledDeps = Arrays.copyOf(unfilledDeps, unfilledDeps.length);
    long[] newFilledDeps = new long[0];
    if (inheritLeft) {
      newHeadNums = Ints.concat(newHeadNums, left.getHeadWordNums());
      newHeadIndexes = Ints.concat(newHeadIndexes, left.getHeadIndexes());
      newUnfilledHeads = Ints.concat(newUnfilledHeads, left.getUnfilledHeads());
      newUnfilledDeps = Longs.concat(newUnfilledDeps, left.getUnfilledDependencies());
    }
    if (inheritRight) {
      newHeadNums = Ints.concat(newHeadNums, right.getHeadWordNums());
      newHeadIndexes = Ints.concat(newHeadIndexes, right.getHeadIndexes());
      newUnfilledHeads = Ints.concat(newUnfilledHeads, right.getUnfilledHeads());
      newUnfilledDeps = Longs.concat(newUnfilledDeps, right.getUnfilledDependencies());
    }
    
    return new ChartEntry(returnSyntax, newHeadNums, newHeadIndexes, newUnfilledHeads, 
        newUnfilledDeps, newFilledDeps, leftSpanStart, leftSpanEnd, leftIndex, rightSpanStart,
        rightSpanEnd, rightIndex);
  }
}
