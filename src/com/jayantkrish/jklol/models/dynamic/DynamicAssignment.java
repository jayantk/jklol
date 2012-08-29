package com.jayantkrish.jklol.models.dynamic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An assignment of values to the variables and plates in a dynamic factor
 * graph. This class determines how many times replications of each plate are
 * constructed in the dynamic graph, as well as assigning values to
 * variables. This class is a generalization of {@code Assignment} for dynamic
 * factor graphs.
 *
 * <p> {@code DynamicAssignment}s have recursive structure in order to support
 * models with nested plates. Each plate takes a list of {@code
 * DynamicAssignment} values, and the size of this list determines how many
 * times the variables in the plate are replicated.
 *
 * @author jayantk
 */
public class DynamicAssignment {

  public static final DynamicAssignment EMPTY = new DynamicAssignment(Assignment.EMPTY, 
      Collections.<String>emptyList(), Collections.<List<DynamicAssignment>>emptyList());
  
  private final Assignment assignment;
  private final Map<String, List<DynamicAssignment>> plateAssignments;
  
  /**
   * Create a dynamic assignment that assigns {@code assignment} to any
   * unreplicated variables, and assigns {@code plateValues} to the plates named
   * {@code plateNames}.
   *
   * @param assignment
   * @param plateNames
   * @param plateValues
   * @return
   */
  public DynamicAssignment(Assignment assignment, List<String> plateNames, 
      List<List<DynamicAssignment>> plateValues) {
    Preconditions.checkArgument(plateValues.size() == plateNames.size());
    this.assignment = assignment;
    
    this.plateAssignments = Maps.newHashMap();
    for (int i = 0; i < plateValues.size(); i++) {
      plateAssignments.put(plateNames.get(i), plateValues.get(i));
    }
  }
  
  private DynamicAssignment(Assignment assignment, 
      Map<String, List<DynamicAssignment>> plateValues) {
    this.assignment = assignment;
    this.plateAssignments = plateValues;
  }

  
  /**
   * Creates a {@code DynamicAssignment} without any replicated structure.
   * 
   * @param assignment
   * @return
   */
  public static DynamicAssignment fromAssignment(Assignment assignment) {
    return new DynamicAssignment(assignment, Collections.<String>emptyList(), 
        Collections.<List<DynamicAssignment>>emptyList());
  }
  
  public static DynamicAssignment createPlateAssignment(String plateName, List<Assignment> assignments) {
    List<DynamicAssignment> dynamicAssignments = Lists.newArrayListWithCapacity(assignments.size());
    for (int i = 0; i < assignments.size(); i++) {
      dynamicAssignments.add(DynamicAssignment.fromAssignment(assignments.get(i)));
    }
    List<List<DynamicAssignment>> plateValues = Lists.newArrayList();
    plateValues.add(dynamicAssignments);
    return new DynamicAssignment(Assignment.EMPTY, Arrays.asList(plateName), plateValues);    
  }
  
  public boolean containsPlateValue(String plateName) {
    return plateAssignments.containsKey(plateName);
  }
  
  public List<DynamicAssignment> getPlateValue(String plateName) {
    return plateAssignments.get(plateName);
  }
  
  public List<Assignment> getPlateFixedAssignments(String plateName) {
    List<Assignment> assignments = Lists.newArrayList();
    for (DynamicAssignment dynamic : getPlateValue(plateName)) {
      assignments.add(dynamic.getFixedAssignment());
    }
    return assignments;
  }
  
  public Assignment getFixedAssignment() {
    return assignment;
  }
  
  public DynamicAssignment union(DynamicAssignment other) {
    Preconditions.checkNotNull(other);
    Map<String, List<DynamicAssignment>> newPlateAssignments = Maps.newHashMap(other.plateAssignments);
    for (String plateName : plateAssignments.keySet()) {
      if (other.plateAssignments.containsKey(plateName)) {
        List<DynamicAssignment> myValue = plateAssignments.get(plateName);
        List<DynamicAssignment> otherValue = other.plateAssignments.get(plateName);
        Preconditions.checkArgument(myValue.size() == otherValue.size(), 
            "Plate values not equal: %s and %s", myValue, otherValue);
        
        List<DynamicAssignment> newValue = Lists.newArrayListWithCapacity(myValue.size());
        for (int i = 0; i < myValue.size(); i++) {
          newValue.add(myValue.get(i).union(otherValue.get(i)));
        }

        newPlateAssignments.put(plateName, newValue);
      } else {
        newPlateAssignments.put(plateName, plateAssignments.get(plateName));
      }
    }
    
    return new DynamicAssignment(assignment.union(other.assignment), newPlateAssignments);
  }
  
  @Override
  public String toString() {
    return "(" + assignment.toString() + ", "  + plateAssignments.toString() + ")";
  }
  
  @Override
  public int hashCode() {
    return assignment.hashCode() * 72354123 + plateAssignments.hashCode();
  }
  
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other instanceof DynamicAssignment) {
      DynamicAssignment otherAssignment = (DynamicAssignment) other;
      return this.assignment.equals(otherAssignment.assignment) && 
          this.plateAssignments.equals(otherAssignment.plateAssignments);
    }
    return false;
  }
}
