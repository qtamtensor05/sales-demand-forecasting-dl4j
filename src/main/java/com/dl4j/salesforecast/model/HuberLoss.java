package com.dl4j.salesforecast.model;

import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.lossfunctions.LossUtil;
import org.nd4j.linalg.ops.transforms.Transforms;

/** Standard Huber with fixed delta=1 in scaled residual error units. No fitted state. */
public final class HuberLoss implements ILossFunction {
    public static final double DELTA = 1.0;
    private static final long serialVersionUID = 1L;

    private INDArray error(INDArray labels, INDArray preOutput, IActivation activation) {
        if (labels.rank() != 2 || !labels.equalShapes(preOutput)) {
            throw new IllegalArgumentException("Huber expects equal rank-2 labels/output (RNN layer flattens time).");
        }
        return activation.getActivation(preOutput.dup(), true).subi(labels.castTo(preOutput.dataType()));
    }

    @Override public INDArray computeScoreArray(INDArray labels, INDArray preOutput,
                                               IActivation activation, INDArray mask) {
        INDArray absolute = Transforms.abs(error(labels, preOutput, activation), false);
        INDArray quadratic = Transforms.min(absolute, DELTA, true);
        INDArray scores = quadratic.mul(quadratic).muli(0.5)
                .addi(absolute.sub(quadratic).muli(DELTA));
        if (mask != null) { LossUtil.applyMask(scores, mask); }
        return scores.sum(true, 1);
    }

    @Override public double computeScore(INDArray labels, INDArray preOutput,
                                         IActivation activation, INDArray mask, boolean average) {
        double score = computeScoreArray(labels, preOutput, activation, mask).sumNumber().doubleValue();
        return average ? score / labels.size(0) : score;
    }

    @Override public INDArray computeGradient(INDArray labels, INDArray preOutput,
                                              IActivation activation, INDArray mask) {
        INDArray gradient = Transforms.min(Transforms.max(error(labels, preOutput, activation), -DELTA, false), DELTA, false);
        if (mask != null && LossUtil.isPerOutputMasking(gradient, mask)) { LossUtil.applyMask(gradient, mask); }
        gradient = activation.backprop(preOutput.dup(), gradient).getFirst();
        if (mask != null) { LossUtil.applyMask(gradient, mask); }
        return gradient;
    }

    @Override public Pair<Double, INDArray> computeGradientAndScore(INDArray labels, INDArray preOutput,
            IActivation activation, INDArray mask, boolean average) {
        return new Pair<>(computeScore(labels, preOutput, activation, mask, average),
                computeGradient(labels, preOutput, activation, mask));
    }
    @Override public String name() { return "Huber(delta=1.0)"; }
    @Override public String toString() { return name(); }
}
