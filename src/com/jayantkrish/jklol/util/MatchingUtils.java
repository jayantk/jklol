package com.jayantkrish.jklol.util;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

/**
 * Implementation of maximum-weighted matching on bipartite graphs.
 * 
 * @author jayantk
 *
 */
public class MatchingUtils {
  
  public static int[] maxMatching(double[][] costs, double discretization) {
    int numRows = costs.length;
    int numCols = costs[0].length;
    int[][] intCosts = new int[numRows][numCols];
    for (int i = 0; i < numRows; i++) {
      for (int j = 0; j < numCols; j++) {
        intCosts[i][j] = (int) (costs[i][j] / discretization);
      }
    }
    return maxMatching(intCosts);
  }

  public static int[] maxMatching(int[][] costs) {
    if (costs.length > costs[0].length) {
      // More rows than columns. Transpose the matrix
      int numRows = costs.length;
      int numCols = costs[0].length;
      int[][] transposed = new int[numCols][numRows];
      for (int i = 0; i < numRows; i++) {
        for (int j = 0; j < numCols; j++) {
          transposed[j][i] = costs[i][j];
        }
      }

      HungarianAlgorithm alg = new HungarianAlgorithm(transposed);
      alg.solve();
      return alg.getColMatching();
    } else {
      HungarianAlgorithm alg = new HungarianAlgorithm(costs);
      alg.solve();
      return alg.getRowMatching();
    }
  }

  private MatchingUtils() {
    // Prevent instantiation.
  }
  
  /**
   * Data structure storing state for the Hungarian algorithm
   * for max weighted matching. Implementation based on the 
   * notes here <a href="http://www.cse.ust.hk/~golin/COMP572/Notes/Matching.pdf">
   * http://www.cse.ust.hk/~golin/COMP572/Notes/Matching.pdf</a> 
   * 
   * @author jayantk
   *
   */
  private static class HungarianAlgorithm {
    // The cost matrix represents the edge weights of the
    // bipartite graph.
    private final int numRows;
    private final int numCols;
    private final int[][] costs;
    
    // Row and column potential functions. Potentials have the
    // property that rowPotential[i] + colPotential[j] >= costs[i][j].
    private final int[] rowPotentials;
    private final int[] colPotentials;
    
    // Mapping of rows to their matched column, -1 if unmatched.
    private final int[] rowMatching;
    // Mapping of columns to their matched row, -1 if unmatched.
    private final int[] colMatching;

    public HungarianAlgorithm(int[][] costs) {
      this.numRows = costs.length;
      this.numCols = costs[0].length;
      Preconditions.checkArgument(numCols >= numRows, "Cost matrix must have numCols >= numRows.");

      // Copy cost matrix
      this.costs = new int[numRows][numCols];
      this.rowPotentials = new int[numRows];
      this.colPotentials = new int[numCols];
      Arrays.fill(colPotentials, 0);
      for (int i = 0; i < numRows; i++) {
        // double minVal = Doubles.min(costs[i]);
        for (int j = 0; j < numCols; j++) {
          this.costs[i][j] = costs[i][j];
        }
        this.rowPotentials[i] = Ints.max(costs[i]);
      }
      
      this.rowMatching = new int[numRows];
      Arrays.fill(rowMatching, -1);
      this.colMatching = new int[numCols];
      Arrays.fill(colMatching, -1);
      // Greedily initialize the matching
      for (int i = 0; i < numRows; i++) {
        for (int j = 0; j < numCols; j++) {
          if (this.costs[i][j] == this.rowPotentials[i] && colMatching[j] == -1) {
            updateMatching(i, j);
          }
        }
      }
    }
    
    public void solve() {
      int[] rowsInTree = new int[numRows];
      int[] colsInTree = new int[numCols];
      int[] rowBackpointers = new int[numRows];
      int[] colBackpointers = new int[numCols];
      int[] neighbors = new int[numCols];
      int[] neighborBackpointers = new int[numCols];
      
      // The outer loop searches for augmenting paths in the graph.
      int freeRowIndex = getFreeRowIndex();
      while (freeRowIndex != -1) {
        // System.out.println("row matching: " + Arrays.toString(rowMatching));
        // System.out.println("col matching: " + Arrays.toString(colMatching));
        // System.out.println("free row: " + freeRowIndex);
        
        Arrays.fill(rowBackpointers, -1);
        Arrays.fill(colBackpointers, -1);
        
        rowsInTree[0] = freeRowIndex;
        int numRowsInTree = 1;
        int numColsInTree = 0;
        
        // Build an alternating tree starting from freeRowIndex. This loop
        // terminates when we find an augmenting path in the alternating tree.
        boolean extendingTree = true;
        while (extendingTree) {
          int numNeighbors = findNeighbors(rowsInTree, numRowsInTree, neighbors, neighborBackpointers);

          // Check if the neighbor set is equal to the cols in the tree.
          // If so, update the potentials so that the neighbor set is no
          // longer equal to the cols.
          Arrays.sort(neighbors, 0, numNeighbors);
          Arrays.sort(colsInTree, 0, numColsInTree);
          // System.out.println("neighbors: " + Arrays.toString(Arrays.copyOf(neighbors, numNeighbors)));
          // System.out.println("colsInTree: " + Arrays.toString(Arrays.copyOf(colsInTree, numColsInTree)));
          if (numNeighbors == numColsInTree && subarrayEquals(neighbors, colsInTree, 0, numNeighbors)) {
            updatePotentials(rowsInTree, numRowsInTree, colsInTree, numColsInTree);

            numNeighbors = findNeighbors(rowsInTree, numRowsInTree, neighbors, neighborBackpointers);
            Arrays.sort(neighbors, 0, numNeighbors);
            // System.out.println("updated neighbors: " + Arrays.toString(Arrays.copyOf(neighbors, numNeighbors)));
            Preconditions.checkState(!(numNeighbors == numColsInTree
                && subarrayEquals(neighbors, colsInTree, 0, numNeighbors)));
          }

          // Find a column that is a neighbor of the current set of vertices
          // but is not already in the tree.
          int chosenCol = -1;
          int chosenBackpointer = -1;
          for (int i = 0; i < numNeighbors; i++) {
            if (i >= numColsInTree || neighbors[i] != colsInTree[i]) {
              chosenCol = neighbors[i];
              chosenBackpointer = neighborBackpointers[chosenCol];
              break;
            }
          }
          Preconditions.checkState(chosenCol != -1);
          // System.out.println("chosen col: " + chosenCol);

          if (colMatching[chosenCol] == -1) {
            // If this column is unmatched, then there is an augmenting path in the tree.
            // Figure out what the augmenting path was by following the tree backpointers,
            // then use it to update our matching.  
            int col = chosenCol;
            int row = chosenBackpointer;
            // System.out.println("matching: " + row + " -> " + col);
            updateMatching(row, col);
            while (rowBackpointers[row] != -1) {
              col = rowBackpointers[row];
              row = colBackpointers[col];
              // System.out.println("matching: " + row + " -> " + col);
              updateMatching(row, col);
            }

            freeRowIndex = getFreeRowIndex();
            extendingTree = false;

          } else {
            // Column is already matched to a row, so grow the tree by adding
            // these edges.
            // System.out.println("row backpointer " + colMatching[chosenCol] + " -> " + chosenCol);
            rowBackpointers[colMatching[chosenCol]] = chosenCol;
            // System.out.println("col backpointer " + chosenCol + " -> " + chosenBackpointer);
            colBackpointers[chosenCol] = chosenBackpointer;

            // System.out.println("r: " + Arrays.toString(Arrays.copyOf(rowsInTree, numRowsInTree)) + " + " + colMatching[chosenCol]);
            rowsInTree[numRowsInTree] = colMatching[chosenCol];
            numRowsInTree++;
            // System.out.println("c: " + Arrays.toString(Arrays.copyOf(colsInTree, numColsInTree)) + " + " + chosenCol);
            colsInTree[numColsInTree] = chosenCol;
            numColsInTree++;
          }
        }
      }
    }
    
    public int[] getRowMatching() {
      return Arrays.copyOf(rowMatching, rowMatching.length);
    }
    
    public int[] getColMatching() {
      return Arrays.copyOf(colMatching, colMatching.length);
    }
    
    private void updateMatching(int row, int col) {
      if (rowMatching[row] != -1) {
        colMatching[rowMatching[row]] = -1;
      }
      
      if (colMatching[col] != -1) {
        rowMatching[colMatching[col]] = -1;
      }
      
      rowMatching[row] = col;
      colMatching[col] = row;
    }
    
    private void updatePotentials(int[] rowsInTree, int numRowsInTree, int[] colsInTree, int numColsInTree) {
      double minVal = Double.POSITIVE_INFINITY;
      for (int i = 0; i < numRowsInTree; i++) {
        int rowIndex = rowsInTree[i];
        for (int j = 0; j < numCols; j++) {
          int index = Ints.indexOf(colsInTree, j);
          if (index != -1 && index < numColsInTree) {
            continue;
          }
          
          minVal = Math.min(minVal, rowPotentials[rowIndex] + colPotentials[j] - costs[rowIndex][j]);
        }
      }
      Preconditions.checkState(minVal != Double.POSITIVE_INFINITY);
      
      // System.out.println("old row potentials: " + Arrays.toString(rowPotentials));
      // System.out.println("old col potentials: " + Arrays.toString(colPotentials));
      for (int i = 0; i < numRowsInTree; i++) {
        rowPotentials[rowsInTree[i]] -= minVal;
      }
      
      for (int j = 0; j < numColsInTree; j++) {
        colPotentials[colsInTree[j]] += minVal;
      }
      // System.out.println("new row potentials: " + Arrays.toString(rowPotentials));
      // System.out.println("new col potentials: " + Arrays.toString(colPotentials));
    }

    private boolean subarrayEquals(int[] array1, int[] array2, int start, int end) {
      for (int i = start; i < end; i++) {
        if (array1[i] != array2[i]) {
          return false;
        }
      }
      return true;
    }

    public int findNeighbors(int[] rowsInTree, int numRowsInTree, int[] neighbors,
        int[] neighborBackpointers) {
      int numNeighbors = 0;
      for (int i = 0; i < numRowsInTree; i++) {
        int rowIndex = rowsInTree[i];
        
        for (int j = 0; j < numCols; j++) {
          if (costs[rowIndex][j] == rowPotentials[rowIndex] + colPotentials[j]) {
            int index = Ints.indexOf(neighbors, j);
            if (!(index != -1 && index < numNeighbors)) {
              neighbors[numNeighbors] = j;
              neighborBackpointers[j] = rowIndex;
              numNeighbors++;
            }
          }
        }
      }
      return numNeighbors;
    }

    public int getFreeRowIndex() {
      for (int i = 0; i < numRows; i++) {
        if (rowMatching[i] == -1) {
          return i;
        }
      }
      return -1;
    }
  }
}
