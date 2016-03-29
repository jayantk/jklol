package com.jayantkrish.jklol.experiments.wikitables;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.CsvParser;
import com.jayantkrish.jklol.util.IoUtils;

public class WikiTable {

  private String id;
  private String[] headings;
  private String[][] rows;
  
  public WikiTable(String id, String[] headings, String[][] rows) {
    this.id = Preconditions.checkNotNull(id);
    this.headings = Preconditions.checkNotNull(headings);
    this.rows = Preconditions.checkNotNull(rows);
  }
  
  public static WikiTable fromCsvFile(String id, String filename) {
    List<String> lines = IoUtils.readLines(filename);
    CsvParser parser = CsvParser.defaultParser();
    
    String[] headings = parser.parseLine(lines.get(0));
    String[][] rows = new String[lines.size() - 1][headings.length];

    for (int i = 1; i < lines.size(); i++) {
      String[] parts = parser.parseLine(lines.get(i));
      for (int j = 0; j < parts.length; j++) {
        rows[i - 1][j] = parts[j];
      }
    }
    return new WikiTable(id, headings, rows);
  }
  
  public String getId() {
    return id;
  }
  
  public String[] getHeadings() {
    return headings;
  }

  public int getColumnByHeading(String heading) {
    for (int i = 0; i < headings.length; i++) {
      if (headings[i].equals(heading)) {
        return i;
      }
    }
    return -1;
  }

  public String[][] getRows() {
    return rows;
  }
  
  public int getNumRows() {
    return rows.length;
  }
  
  public int getNumColumns() {
    return headings.length;
  }

  public String getValue(int row, int col) {
    return rows[row][col];
  }
  
  public String toString() {
    return "[WikiTable " + getId() + "]";
  }
  
  public String toTsv() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < headings.length; i++) {
      sb.append(headings[i]);
      sb.append("\t");
    }
    sb.append("\n");
    
    for (int i = 0; i < rows.length; i++) {
      for (int j = 0; j < rows[i].length; j++) {
        sb.append(rows[i][j]);
        sb.append("\t");
      }
      if (i != rows.length - 1) {
        sb.append("\n");
      }
    }
    return sb.toString();
  }
}
