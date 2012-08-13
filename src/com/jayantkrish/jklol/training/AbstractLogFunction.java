package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * Implementation of the timer methods from {@link LogFunction}.
 * 
 * @author jayantk
 */
public abstract class AbstractLogFunction implements LogFunction {
  
  // Each thread has its own collection of timers.
  private final Map<Long, Map<String, Long>> activeTimers;
  private final Map<String, Long> timerSumTimes;
  private final Map<String, Long> timerInvocations;
  
  private final long TIME_DENOMINATOR = 1000000;
  
  public AbstractLogFunction() {
    activeTimers = Collections.synchronizedMap(Maps.<Long, Map<String, Long>>newHashMap());
    timerSumTimes = Collections.synchronizedMap(Maps.<String, Long>newHashMap());
    timerInvocations = Collections.synchronizedMap(Maps.<String, Long>newHashMap());
  }

  @Override
  public void startTimer(String timerName) {
    Long threadId = Thread.currentThread().getId();
    if (!activeTimers.containsKey(threadId)) {
      activeTimers.put(threadId, Maps.<String, Long>newHashMap());
    }
    activeTimers.get(threadId).put(timerName, System.nanoTime());
  }

  @Override
  public long stopTimer(String timerName) {
    Long threadId = Thread.currentThread().getId();
    Preconditions.checkArgument(activeTimers.get(threadId).containsKey(timerName));
    long end = System.nanoTime();
    long start = activeTimers.get(threadId).remove(timerName);
    
    if (!timerSumTimes.containsKey(timerName)) {
      timerSumTimes.put(timerName, 0L);
    }
    if (!timerInvocations.containsKey(timerName)) {
      timerInvocations.put(timerName, 0L);
    }
    timerSumTimes.put(timerName, timerSumTimes.get(timerName) + (end - start));
    timerInvocations.put(timerName, timerInvocations.get(timerName) + 1L);
    
    return (end - start) / TIME_DENOMINATOR;
  }

  protected Set<String> getAllTimers() {
    return timerSumTimes.keySet();
  }
  
  protected long getTimerElapsedTime(String timerName) {
    Preconditions.checkArgument(timerSumTimes.containsKey(timerName));
    return timerSumTimes.get(timerName) / TIME_DENOMINATOR; // Return time in milliseconds.
  }
  
  protected long getTimerInvocations(String timerName) {
    Preconditions.checkArgument(timerInvocations.containsKey(timerName));
    return timerInvocations.get(timerName);
  }
}
