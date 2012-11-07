package com.jayantkrish.jklol.ccg;


public class CcgUnaryRule {

  private final SyntacticCategory inputSyntax;
  private final SyntacticCategory returnSyntax;
  
  public CcgUnaryRule(SyntacticCategory inputSyntax, SyntacticCategory returnSyntax) {
    this.inputSyntax = inputSyntax;
    this.returnSyntax = returnSyntax;
  }  

  /*
  public static CcgUnaryRule parseFrom(String line) {
    try {
      String[] chunks = new CSVParser(CSVParser.DEFAULT_SEPARATOR, 
          CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.NULL_CHARACTER).parseLine(line);
      Preconditions.checkArgument(chunks.length >= 2);

      System.out.println(Arrays.toString(chunks));

      String[] syntacticParts = chunks[0].split(" ");
      Preconditions.checkArgument(syntacticParts.length == 2);
      SyntacticCategory inputSyntax = SyntacticCategory.parseFrom(syntacticParts[0]);
      SyntacticCategory returnSyntax = SyntacticCategory.parseFrom(syntacticParts[1]);

      boolean inheritSemantics = chunks[1].equals("T");

      int[] unfilledHeads = new int[0];
      if (chunks.length >= 3) {
        String[] newHeads = chunks[3].split(",");
        unfilledHeads = new int[newHeads.length];
        for (int i = 0; i < newHeads.length; i++) {
          unfilledHeads[i] = Integer.parseInt(newHeads[i].substring(1));
        }
      }

      long[] unfilledDeps = new long[0];
      if (chunks.length >= 4) {
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
    } catch (IOException e) {
      throw new IllegalArgumentException("Illegal binary rule string: " + line, e);
    }
  }
  */
}
