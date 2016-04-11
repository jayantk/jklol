package com.jayantkrish.jklol.experiments.p3;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.EvalContext;
import com.jayantkrish.jklol.lisp.FunctionValue;

public class P3Functions {

  public static class GetEntities implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      KbState state = (KbState) argumentValues.get(0);
      return ConsValue.listToConsList(state.getEnvironment().getEntities());
    }
  }
  
  public static class KbGet implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      KbState state = (KbState) argumentValues.get(0);
      String predicate = (String) argumentValues.get(1);
      Object entity = argumentValues.get(2);
      
      if (entity instanceof ConsValue) {
        Object arg1 = ((ConsValue) entity).getCar();
        Object arg2 = ((ConsValue) entity).getCar();
        
        return state.getRelationValue(predicate, arg1, arg2);
      } else {
        return state.getCategoryValue(predicate, entity);
      }
    }
  }
  
  public static class KbSet implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      KbState state = (KbState) argumentValues.get(0);
      String predicate = (String) argumentValues.get(1);
      Object entity = argumentValues.get(2);
      Object value = argumentValues.get(3);
      
      if (entity instanceof ConsValue) {
        Object arg1 = ((ConsValue) entity).getCar();
        Object arg2 = ((ConsValue) entity).getCar();

        return state.setRelationValue(predicate, arg1, arg2, value);
      } else {
        return state.setCategoryValue(predicate, entity, value);
      }
    }
  }
  
  public static class ListToSet implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return Sets.newHashSet(ConsValue.consListToList(argumentValues.get(0)));
    }
  }
}
