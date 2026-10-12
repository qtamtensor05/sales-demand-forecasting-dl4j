package com.dl4j.salesforecast.model;

import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.lossfunctions.impl.LossMSE;

/** Loss is the only variable in the paired residual experiment. */
public enum LossMode {
    MSE, HUBER;
    public ILossFunction createLoss() { return this == HUBER ? new HuberLoss() : new LossMSE(); }
}
