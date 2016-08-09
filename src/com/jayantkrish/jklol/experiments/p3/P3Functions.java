package com.jayantkrish.jklol.experiments.p3;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.EvalContext;
import com.jayantkrish.jklol.lisp.FunctionValue;
import com.jayantkrish.jklol.p3.KbState;

public class P3Functions {
  
  public static abstract class LoggingFunctionValue implements FunctionValue {
    
    private final String timerName;
    private static final boolean ENABLE_LOGGING = false;
    
    public LoggingFunctionValue(String timerName) {
      this.timerName = Preconditions.checkNotNull(timerName);
    }

    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      if (ENABLE_LOGGING) {
        context.getLog().startTimer(timerName);
        Object result = apply2(argumentValues, context);
        context.getLog().stopTimer(timerName);
        return result;
      } else {
        return apply2(argumentValues, context);
      }
    }

    public abstract Object apply2(List<Object> argumentValues, EvalContext context);
  }

  public static class GetEntities extends LoggingFunctionValue {
    public GetEntities() {
      super("p3functions/get_entities");
    }
    
    @Override
    public Object apply2(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      KbState state = (KbState) argumentValues.get(0);
      return ConsValue.listToConsList(state.getTypeVar(P3Utils.ENTITY_TYPE_NAME).getValues());
    }
  }
  
  public static class KbGet extends LoggingFunctionValue {
    public KbGet() {
      super("p3functions/kb_get");
    }

    @Override
    public Object apply2(List<Object> argumentValues, EvalContext context) {
      KbState state = (KbState) argumentValues.get(0);
      String predicate = (String) argumentValues.get(1);
      List<Object> args = ConsValue.consListToList(argumentValues.get(2));
      return state.getFunctionValue(predicate, args);
    }
  }

  public static class ListToSet extends LoggingFunctionValue {
    public ListToSet() {
      super("p3functions/list_to_set");
    }
    
    @Override
    public Object apply2(List<Object> argumentValues, EvalContext context) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return Sets.newHashSet(ConsValue.consListToList(argumentValues.get(0)));
    }
  }
}
