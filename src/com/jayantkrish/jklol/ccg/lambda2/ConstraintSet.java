package com.jayantkrish.jklol.ccg.lambda2;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.util.CountAccumulator;

/**
 * A set of typing constraints between type variables and types.
 * Both subtype and equality constraints are permitted.
 * A constraint set can be solved to find an assignment from type
 * variables to types (if such an assignment is possible).
 * 
 * @author jayantk
 *
 */
public class ConstraintSet {

  private final List<Constraint> eqConstraints;
  private final List<Constraint> subConstraints;
  private final TypeReplacement bindings;
  private final boolean solvable;
  
  private int typeVarCounter = 0;
  
  public ConstraintSet(List<Constraint> equalityConstraints,
      List<Constraint> subConstraints, TypeReplacement bindings,
      boolean solvable, int typeVarCounter) {
    this.eqConstraints = equalityConstraints;
    this.subConstraints = subConstraints;
    this.bindings = bindings;
    this.solvable = solvable;
    this.typeVarCounter = typeVarCounter;
  }
  
  public static ConstraintSet empty() {
    return new ConstraintSet(Lists.newArrayList(), Lists.newArrayList(),
        new TypeReplacement(Maps.newHashMap()), true, 0);
  }
  
  public Map<Integer, Type> getBindings() {
    return bindings.getBindings();
  }

  public boolean isSolvable() {
    return solvable;
  }

  public void add(Constraint constraint) {
    subConstraints.add(constraint.substitute(bindings.getBindings()));
  }
  
  public void addAll(Collection<Constraint> constraintCollection) {
    for (Constraint c : constraintCollection) {
      subConstraints.add(c.substitute(bindings.getBindings()));
    }
  }
  
  public void addEquality(Type subt, Type supert) {
    eqConstraints.add(new Constraint(subt, supert));
  }

  public Type getFreshTypeVar() {
    return Type.createTypeVariable(typeVarCounter++);
  }

  public Type upcast(Type t, boolean positiveCtx) {
    return upcast(t, subConstraints, positiveCtx);
  }

  private Type upcast(Type t, List<Constraint> acc, boolean positiveCtx) {
    if (t.isAtomic()) {
      Type v = getFreshTypeVar();
      if (positiveCtx) {
        acc.add(new Constraint(t, v));
      } else {
        acc.add(new Constraint(v, t));
      }
      return v;
    } else {
      Type argType = upcast(t.getArgumentType(), acc, !positiveCtx);
      Type retType = upcast(t.getReturnType(), acc, positiveCtx);
      return Type.createFunctional(argType, retType, t.acceptsRepeatedArguments());
    }
  }
  
  public ConstraintSet solve(TypeDeclaration types) {
    // System.out.println(this);
    ConstraintSet equalSolved = this.solveEquality();
    // System.out.println(equalSolved);
    ConstraintSet atomic = equalSolved.makeAtomic();
    // System.out.println(atomic);
    ConstraintSet solved = atomic.solveAtomic(types);
    // System.out.println(solved);
    return solved;
  }

  /**
   * Solve equality constraints to generate an entailed mapping
   * from type variables to types (or other type variables). 
   *  
   * @return
   */
  private ConstraintSet solveEquality() {
    List<Constraint> current = eqConstraints;
    List<Constraint> next = Lists.newArrayList();
    boolean done = false;
    boolean solvable = this.solvable;

    TypeReplacement nextBindings = new TypeReplacement(bindings);
    while (!done) {
      done = true;
      // Iterative constraint solving algorithm with four cases
      for (Constraint c : current) {
        Type subtype = c.subtype.substitute(nextBindings.getBindings());
        Type supertype = c.supertype.substitute(nextBindings.getBindings());
        
        if (subtype.isFunctional() && supertype.isFunctional()) {
          // Case 1: <a,b> = <c,d> => a = c && b = d
          
          // Replace this constraint with the entailed subtype
          // constraints on argument/return types.
          next.add(new Constraint(supertype.getArgumentType(), subtype.getArgumentType()));
          next.add(new Constraint(subtype.getReturnType(), supertype.getReturnType()));
          done = false;
        } else if (!subtype.isFunctional() && supertype.isFunctional()) {
          // Case 2: A = <c,d> => A = <c,d> (if A is a type var, else fail)
          if (subtype.hasTypeVariables()) {
            nextBindings.add(subtype.getAtomicTypeVar(), supertype);
            done = false;
          } else {
            solvable = false;
          }
        } else if (subtype.isFunctional() && !supertype.isFunctional()) {
          // Case 3: <c,d> = A => A = <c,d> (if A is a type var, else fail)
          if (supertype.hasTypeVariables()) {
            nextBindings.add(supertype.getAtomicTypeVar(), subtype);
            done = false;
          } else {
            solvable = false;
          }
        } else {
          // Case 4: A = B => A = B
          if (subtype.hasTypeVariables() && supertype.hasTypeVariables()) {
            if (subtype.getAtomicTypeVar() != supertype.getAtomicTypeVar()) {
              nextBindings.add(subtype.getAtomicTypeVar(), supertype);
            }
          } else if (subtype.hasTypeVariables()) {
            nextBindings.add(subtype.getAtomicTypeVar(), supertype);
            done = false;
          } else if (supertype.hasTypeVariables()) {
            nextBindings.add(supertype.getAtomicTypeVar(), subtype);
            done = false;
          } else {
            if (!subtype.equals(supertype)) {
              solvable = false;
            }
          }
        }
      }

      current = next;
      next = Lists.newArrayList();
    }
    
    List<Constraint> newSubConstraints = Lists.newArrayList();
    for (Constraint c : subConstraints) {
      newSubConstraints.add(c.substitute(nextBindings.getBindings()));
    }
    return new ConstraintSet(Lists.newArrayList(), newSubConstraints, nextBindings,
        solvable, typeVarCounter);
  }

  /**
   * Make all subtype constraints atomic by generating new
   * type variables for all argument/return types of function-types
   * along with the corresponding subtype constraints.
   * 
   * @return
   */
  private ConstraintSet makeAtomic() {
    boolean done = false;
    List<Constraint> current = subConstraints;
    List<Constraint> next = Lists.newArrayList();
    TypeReplacement nextBindings = new TypeReplacement(bindings);
    boolean solvable = this.solvable;
    while (!done) {
      done = true;
      for (Constraint c : current) {
        Constraint substituted = c.substitute(nextBindings.getBindings());
        Type subtype = substituted.subtype;
        Type supertype = substituted.supertype;
        
        if (subtype.isFunctional() && supertype.isFunctional()) {
          next.add(new Constraint(supertype.getArgumentType(), subtype.getArgumentType()));
          next.add(new Constraint(subtype.getReturnType(), supertype.getReturnType()));
          done = false;
        } else if (!subtype.isFunctional() && supertype.isFunctional()) {
          if (subtype.hasTypeVariables()) {
            Type subtypeReplacement = upcast(supertype, next, false);
            nextBindings.add(subtype.getAtomicTypeVar(), subtypeReplacement);
            done = false;
          } else {
            solvable = false;
          }
        } else if (subtype.isFunctional() && !supertype.isFunctional()) {
          if (supertype.hasTypeVariables()) {
            Type supertypeReplacement = upcast(subtype, next, true);
            nextBindings.add(supertype.getAtomicTypeVar(), supertypeReplacement);
            done = false;
          } else {
            solvable = false;
          }
        } else {
          next.add(substituted);
        }
      }

      current = next;
      next = Lists.newArrayList();
    }

    return new ConstraintSet(Lists.newArrayList(), current, nextBindings,
        solvable, typeVarCounter);
  }

  private ConstraintSet solveAtomic(TypeDeclaration typeDeclaration) {
    List<Constraint> current = subConstraints;
    List<Constraint> next = Lists.newArrayList();
    Map<Integer, Type> lowerBounds = Maps.newHashMap();
    Map<Integer, Type> upperBounds = Maps.newHashMap();
    Set<Integer> boundedVars = Sets.newHashSet();
    boolean done = false;
    boolean solvable = this.solvable;

    TypeReplacement nextBindings = new TypeReplacement(bindings);
    while (!done && solvable) {
      done = true;
      Set<Integer> onLeft = Sets.newHashSet();
      Set<Integer> onRight = Sets.newHashSet();
      CountAccumulator<Integer> varCounts = CountAccumulator.create();
      Map<Integer, Integer> varCountSubVar = Maps.newHashMap();
      for (Constraint c : current) {
        Type subtype = c.subtype;
        Type supertype = c.supertype;
        
        Preconditions.checkState(subtype.isAtomic() && supertype.isAtomic(),
            "solveAtomic() requires atomic subtype constraints, got %s", c);

        if (!subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
          if (!typeDeclaration.isAtomicSubtype(subtype.getAtomicTypeName(),
              supertype.getAtomicTypeName())) {
            solvable = false;
          }
        } else if (!subtype.hasTypeVariables() && supertype.hasTypeVariables()) {        
          int supertypeVar = supertype.getAtomicTypeVar();
          Type bound = subtype;
          if (lowerBounds.containsKey(supertypeVar)) {
            bound = typeDeclaration.join(lowerBounds.get(supertypeVar), bound);
          } 
          lowerBounds.put(supertype.getAtomicTypeVar(), bound);
          boundedVars.add(supertype.getAtomicTypeVar());

        } else if (subtype.hasTypeVariables() && !supertype.hasTypeVariables()) {
          int subtypeVar = subtype.getAtomicTypeVar();
          Type bound = supertype;
          if (upperBounds.containsKey(subtypeVar)) {
            bound = typeDeclaration.meet(upperBounds.get(subtypeVar), bound);
          }
          upperBounds.put(subtype.getAtomicTypeVar(), bound);
          boundedVars.add(subtype.getAtomicTypeVar());
        } else {
          if (!subtype.equals(supertype)) {
            onLeft.add(subtype.getAtomicTypeVar());
            onRight.add(supertype.getAtomicTypeVar());
            varCounts.increment(subtype.getAtomicTypeVar(), 1);
            varCounts.increment(supertype.getAtomicTypeVar(), 1);
            varCountSubVar.put(subtype.getAtomicTypeVar(), supertype.getAtomicTypeVar());
            varCountSubVar.put(supertype.getAtomicTypeVar(), subtype.getAtomicTypeVar());
            next.add(c);
          }
        }
      }

      for (Integer var : boundedVars) {
        Type lb = lowerBounds.get(var);
        Type ub = upperBounds.get(var);
        if (lb != null && ub != null) {
          if (!typeDeclaration.isAtomicSubtype(lb.getAtomicTypeName(),
              ub.getAtomicTypeName())) {
            solvable = false;
          }
        }
        
        if (lb != null && !onRight.contains(var) && !nextBindings.contains(var)) {
          nextBindings.add(var, lb);
          done = false;
        }

        if (ub != null && !onLeft.contains(var) && !nextBindings.contains(var)) {
          nextBindings.add(var, ub);
          done = false;
        }
      }

      for (Integer var : varCounts.keySet()) {
        if (varCounts.getCount(var) <= 1.0 && !boundedVars.contains(var)
            && !nextBindings.contains(var)
            && !nextBindings.contains(varCountSubVar.get(var))) {
          nextBindings.add(var, Type.createTypeVariable(varCountSubVar.get(var)));
          done = false;
        }
      }

      current = Lists.newArrayList();
      for (Constraint c : next) {
        current.add(c.substitute(nextBindings.getBindings()));
      }
      next = Lists.newArrayList();
    }

    return new ConstraintSet(eqConstraints, current, nextBindings, solvable, typeVarCounter);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[ConstraintSet\n");
    for (Constraint constraint : subConstraints) {
      sb.append("  ");
      sb.append(constraint.toString());
      sb.append("\n");
    }
    sb.append(bindings);
    sb.append("]");
    return sb.toString();
  }

  private static class Constraint {
    final Type subtype;
    final Type supertype;

    public Constraint(Type subtype, Type supertype) {
      this.subtype = Preconditions.checkNotNull(subtype);
      this.supertype = Preconditions.checkNotNull(supertype);
    }
    
    public Constraint substitute(Map<Integer, Type> replacements) {
      return new Constraint(subtype.substitute(replacements),
          supertype.substitute(replacements));
    }
    
    @Override
    public String toString() {
      return subtype + " ⊑ " + supertype;
    }
  }
}

