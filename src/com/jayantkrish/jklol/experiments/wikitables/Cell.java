package com.jayantkrish.jklol.experiments.wikitables;

public class Cell {
  private final int rowId;
  private final int colId;

  public Cell(int rowId, int colId) {
    this.rowId = rowId;
    this.colId = colId;
  }

  public int getRowId() {
    return rowId;
  }

  public int getColId() {
    return colId;
  }
  
  public String toString() {
    return "(" + rowId + "," + colId + ")";
  }
}
