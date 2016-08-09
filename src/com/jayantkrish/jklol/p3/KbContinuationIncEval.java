package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.AmbEval.WrappedBuiltinFunction;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.EvalContext;
import com.jayantkrish.jklol.lisp.FunctionValue;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.ContinuationFunctionValue;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.lisp.inc.IncEvalSearchLog;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.util.IndexedList;

public class KbContinuationIncEval extends ContinuationIncEval {
  private final KbModel kbModel;
  
  public static final String SET_KB_CONTINUATIONS = "kb-set-k";

  public KbContinuationIncEval(AmbEval eval, Environment env,
      Function<Expression2, Expression2> cpsTransform, SExpression defs, KbModel kbModel) {
    super(eval, env, cpsTransform, defs,
        Arrays.asList(FINAL_CONTINUATION, QUEUE_CONTINUATIONS, SET_KB_CONTINUATIONS),
        Arrays.asList(new FinalContinuation(), new QueueContinuations(), new QueueSetKbContinuations()));
    this.kbModel = kbModel;
  }
  
  public KbContinuationIncEval(ContinuationIncEval eval, KbModel kbModel) {
    super(eval.getEval(), eval.getInitialEnvironment(), eval.getCpsTransform(), eval.getDefs(),
        Arrays.asList(FINAL_CONTINUATION, QUEUE_CONTINUATIONS, SET_KB_CONTINUATIONS),
        Arrays.asList(new FinalContinuation(), new QueueContinuations(), new QueueSetKbContinuations()));
    this.kbModel = kbModel;
  }

  public KbModel getKbModel() {
    return kbModel;
  }
  
  @Override
  public Tensor getInitialFeatureVector(Object initialDiagram) {
    if (kbModel == null) {
      return null;
    } else {
      return SparseTensor.empty(new int[] {0},
          new int[] {kbModel.getActionFeatureGenerator().getNumberOfFeatures()});
    }
  }

  @Override
  public void nextState(IncEvalState prev, IncEvalState next, Object continuation,
      Environment env, Object denotation, Object diagram, Object otherArg, LogFunction log) {
    if (kbModel == null) {
      // Allow kbModel to be null to enable evaluation of programs 
      // without scoring them using the same code.
      next.set(continuation, env, denotation, diagram, 1.0, prev.getFeatures());
      return;
    }

    log.startTimer("evaluate_continuation/queue/model");
    KbState state = (KbState) diagram;
    double prob = 1.0;
    IndexedList<String> predicateNames = kbModel.getPredicateNames();
    List<Tensor> classifiers = kbModel.getClassifiers();
    List<Tensor> predClassifiers = kbModel.getPredicateClassifiers();

    // Score the current kb state.
    IndexedList<String> stateFunctionNames = state.getFunctions();
    List<FunctionAssignment> assignments = state.getAssignments();
    for (int i : state.getUpdatedFunctionIndexes()) {
      int index = predicateNames.getIndex(stateFunctionNames.get(i));
      Tensor classifier = classifiers.get(index);

      Tensor featureVector = assignments.get(i).getFeatureVector().relabelDimensions(
          classifier.getDimensionNumbers());
      prob *= Math.exp(classifier.innerProduct(featureVector).getByDimKey());
      
      Tensor predClassifier = predClassifiers.get(index);
      Tensor predFeatureVector = assignments.get(i).getPredicateFeatureVector().relabelDimensions(
          classifier.getDimensionNumbers());
      prob *= Math.exp(predClassifier.innerProduct(predFeatureVector).getByDimKey());
    }

    // Score actions.
    // XXX: this code assumes that the action scoring model is linear. 
    Tensor actionFeaturesSum = prev.getFeatures();
    if (otherArg != null) {
      Tensor actionFeatures = kbModel.generateActionFeatures(new StateFeatures(
          prev, continuation, env, denotation, diagram, otherArg));
      actionFeaturesSum = actionFeaturesSum.elementwiseAddition(actionFeatures);
    }
    prob *= Math.exp(kbModel.getActionClassifier().innerProduct(actionFeaturesSum).getByDimKey());

    log.stopTimer("evaluate_continuation/queue/model");

    next.set(continuation, env, denotation, diagram, prob, actionFeaturesSum);
  }
  
  @Override
  public IncEvalChart initializeChart(Expression2 lf, Object initialDiagram,
    IncEvalCost cost, Environment startEnv, IncEvalSearchLog searchLog,
    int beamSize) {
    KbState kb = (KbState) initialDiagram;
    
    // Construct and queue the start state. 
    // Note that continuation may be null, meaning that lf cannot
    // be evaluated by this class. If this is the case, this method
    // will return the initialState as the only result.
    Object continuation = lfToContinuation(lf, startEnv);
    Tensor featureVector = getInitialFeatureVector(initialDiagram);
    IncEvalState sizingState = new IncEvalState(continuation, startEnv, null,
        kb.copy(), 1.0, featureVector);

    // Initialize the chart and their KbStates.
    IncEvalChart chart = new IncEvalChart(beamSize, sizingState, cost, searchLog);
    Preconditions.checkState(chart.getNumAllocated() == 0);
    IncEvalState[] free = chart.getFree();
    for (int i = 0; i < free.length; i++) {
      IncEvalState s = free[i];
      s.setDiagram(kb.copy());
    }

    IncEvalState initialState = chart.alloc();
    sizingState.copyTo(initialState);
    chart.offer(null, initialState);

    return chart;
  }
  
  /**
   * ((kb-set-k [continuation] [function-name]
   *   [function-args] [list of values to set]) [world])
   * 
   * @author jayantk
   *
   */
  public static class QueueSetKbContinuations extends ContinuationFunctionValue {

    public QueueSetKbContinuations() {super();}

    public QueueSetKbContinuations copy() {
      return new QueueSetKbContinuations();
    }

    @Override
    public Object apply(List<Object> args, EvalContext context) {
      LispUtil.checkArgument(args.size() == 4,
          "Expected 4 arguments, got: %s", args);
      AmbFunctionValue continuation = (AmbFunctionValue) args.get(0);
      String functionName = (String) args.get(1);
      List<Object> functionArgs = ConsValue.consListToList(args.get(2));
      List<Object> functionValues = ConsValue.consListToList(args.get(3));

      return new WrappedBuiltinFunction(new FunctionValue() {
        public Object apply(List<Object> args2, EvalContext context2) {
          LispUtil.checkArgument(args2.size() == 1, "Expected 1 argument, got: %s", args2);
          KbState currentKb = (KbState) args2.get(0);

          for (int i = 0; i < functionValues.size(); i++) {
            IncEvalState next = chart.alloc();
            KbState nextKb = (KbState) next.getDiagram();
            currentKb.copyTo(nextKb);
            
            Object value = functionValues.get(i);
            nextKb.putFunctionValue(functionName, functionArgs, value);
            
            Object nextCont = continuation.apply(Arrays.asList(value), context2, null);
            eval.nextState(current, next, nextCont, Environment.extend(current.getEnvironment()),
                value, nextKb, null, log);
            chart.offer(current, next);
          }

          return ConstantValue.NIL;
        }
      });
    }
  }
}