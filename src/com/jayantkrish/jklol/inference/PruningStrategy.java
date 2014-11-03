package com.jayantkrish.jklol.inference;

import java.io.Serializable;

import com.jayantkrish.jklol.models.Factor;

public interface PruningStrategy extends Serializable {

  Factor apply(Factor factor);
}
