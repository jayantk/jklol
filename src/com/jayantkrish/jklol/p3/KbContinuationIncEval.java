package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Function;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.AmbEval;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.EvalContext;
import com.jayantkrish.jklol.lisp.LispUtil;
import com.jayantkrish.jklol.lisp.SExpression;
import com.jayantkrish.jklol.lisp.inc.ContinuationFunctionValue;
import com.jayantkrish.jklol.lisp.inc.ContinuationIncEval;
import com.jayantkrish.jklol.lisp.inc.IncEvalChart;
import com.jayantkrish.jklol.lisp.inc.IncEvalCost;
import com.jayantkrish.jklol.lisp.inc.IncEvalSearchLog;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricContinuationIncEval.StateFeatures;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;

public class KbContinuationIncEval extends ContinuationIncEval {
  private final KbModel kbModel;
  
  public static final String SET_KB_CONTINUATIONS = "kb-set-k";

  public KbContinuationIncEval(AmbEval eval, Environment env,
      Function<Expression2, Expression2> cpsTransform, SExpression defs,
      List<String> functionNames, List<ContinuationFunctionValue> functions,
      KbModel kbModel) {
    super(eval, env, cpsTransform, defs, functionNames, functions);
    this.kbModel = kbModel;
  }

  public KbContinuationIncEval(AmbEval eval, Environment env,
      Function<Expression2, Expression2> cpsTransform, SExpression defs, KbModel kbModel) {
    super(eval, env, cpsTransform, defs,
        Arrays.asList(FINAL_CONTINUATION, SET_KB_CONTINUATIONS),
        Arrays.asList(new FinalKbContinuation(), new QueueSetKbContinuations()));
    this.kbModel = kbModel;
  }
  
  public KbContinuationIncEval(ContinuationIncEval eval, KbModel kbModel) {
    super(eval.getEval(), eval.getInitialEnvironment(), eval.getCpsTransform(), eval.getDefs(),
        Arrays.asList(FINAL_CONTINUATION, SET_KB_CONTINUATIONS),
        Arrays.asList(new FinalKbContinuation(), new QueueSetKbContinuations()));
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

    // log.startTimer("evaluate_continuation/queue/model");
    KbState state = (KbState) diagram;
    double logProb = 0.0;
    List<Tensor> classifiers = kbModel.getClassifiers();
    List<Tensor> predClassifiers = kbModel.getPredicateClassifiers();

    // Score the current kb state.
    // XXX: this code assumes that the scoring models are linear.
    List<FunctionAssignment> assignments = state.getAssignments();
    // log.startTimer("evaluate_continuation/queue/model/predicates");
    for (int i : state.getUpdatedFunctionIndexes()) {
      Tensor classifier = classifiers.get(i);
      FunctionAssignment assignment = assignments.get(i);

      Tensor featureVector = assignment.getFeatureVector();
      logProb += classifier.innerProductScalar(featureVector);
      
      Tensor predClassifier = predClassifiers.get(i);
      Tensor predFeatureVector = assignment.getPredicateFeatureVector();
      logProb += predClassifier.innerProductScalar(predFeatureVector);
    }
    // log.stopTimer("evaluate_continuation/queue/model/predicates");

    // Score actions.
    // XXX: this code assumes that the action scoring model is linear.
    // log.startTimer("evaluate_continuation/queue/model/actions");
    Tensor actionFeaturesSum = prev.getFeatures();
    if (otherArg != null) {
      // log.startTimer("evaluate_continuation/queue/model/actions_generate");
      Tensor actionFeatures = kbModel.generateActionFeatures(new StateFeatures(
          prev, continuation, env, denotation, diagram, otherArg));
      actionFeaturesSum = actionFeaturesSum.elementwiseAddition(actionFeatures);
      // log.stopTimer("evaluate_continuation/queue/model/actions_generate");
    }
    logProb += kbModel.getActionClassifier().innerProductScalar(actionFeaturesSum);
    // log.stopTimer("evaluate_continuation/queue/model/actions");

    next.set(continuation, env, denotation, diagram, Math.exp(logProb), actionFeaturesSum);
    // log.stopTimer("evaluate_continuation/queue/model");
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

    // Initialize the chart and their KbStates.
    KbIncEvalChart chart = new KbIncEvalChart(beamSize, cost, searchLog, kb);

    IncEvalState initialState = chart.alloc();
    KbState initialKb = chart.allocDeepCopyOf(kb);
    initialState.set(continuation, startEnv, null, initialKb, 1.0, featureVector);
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
    public Object continuationApply(List<Object> args, List<Object> args2,
        EvalContext context, EvalContext context2) {
      LispUtil.checkArgument(args.size() == 4,
          "Expected 4 arguments, got: %s", args);
      AmbFunctionValue continuation = (AmbFunctionValue) args.get(0);
      String functionName = (String) args.get(1);
      List<Object> functionArgs = ConsValue.consListToList(args.get(2));
      List<Object> functionValues = ConsValue.consListToList(args.get(3));

      LispUtil.checkArgument(args2.size() == 1, "Expected 1 argument, got: %s", args2);
      KbState currentKb = (KbState) args2.get(0);
      int functionIndex = currentKb.getFunctions().getIndex(functionName);
      KbIncEvalChart kbChart = (KbIncEvalChart) chart;

      for (int i = 0; i < functionValues.size(); i++) {
        IncEvalState next = kbChart.alloc();
        KbState nextKb = kbChart.allocCopyOf(currentKb);

        kbChart.allocAssignment(nextKb, functionIndex);
        currentKb.getAssignment(functionIndex).copyTo(nextKb.getAssignment(functionIndex));
        Object value = functionValues.get(i);
        nextKb.putFunctionValue(functionName, functionArgs, value);

        Object nextCont = continuation.apply(Arrays.asList(value), context2, null);
        eval.nextState(current, next, nextCont, Environment.extend(current.getEnvironment()),
            value, nextKb, null, log);
        chart.offer(current, next);
      }

      return ConstantValue.NIL;
    }
  }
  
  public static class FinalKbContinuation extends ContinuationFunctionValue {
    public FinalKbContinuation() {
      super();
    }

    @Override
    public FinalKbContinuation copy() {
      return new FinalKbContinuation();
    }

    public Object continuationApply(List<Object> args1, List<Object> args2, EvalContext context1,
        EvalContext context2) {
      LispUtil.checkArgument(args1.size() == 1);
      Object denotation = args1.get(0);

      LispUtil.checkArgument(args2.size() == 1);
      KbState diagram = (KbState) args2.get(0);

      KbIncEvalChart kbChart = (KbIncEvalChart) chart;
      IncEvalState next = kbChart.alloc();
      KbState nextKb = kbChart.allocCopyOf(diagram);

      eval.nextState(current, next, null, Environment.extend(current.getEnvironment()),
          denotation, nextKb, null, log);
      chart.offerFinished(current, next);

      return ConstantValue.NIL;
    }
  }
}