package com.alibaba.alink.operator.common.tree;

import java.io.Serializable;
import java.util.Arrays;

public class LabelCounter implements Serializable, DeepCopyable <LabelCounter> {
	private static final long serialVersionUID = 5749266833722532209L;
	private double weightSum;
	private int numInst;
	private double[] distributions;

	public LabelCounter() {
	}

	public LabelCounter(
		double weightSum,
		int numInst,
		double[] distributions) {
		this.weightSum = weightSum;
		this.numInst = numInst;
		this.distributions = distributions;
	}

	public double getWeightSum() {
		return weightSum;
	}

	public int getNumInst() {
		return numInst;
	}

	public double[] getDistributions() {
		return distributions;
	}

	public LabelCounter add(LabelCounter other, double weight) {
		this.weightSum += weight;

		if (distributions != null) {
			for (int i = 0; i < distributions.length; ++i) {
				distributions[i] += other.distributions[i] * weight;
			}
		}

		return this;
	}

	public LabelCounter normWithWeight() {
		if (weightSum == 0.) {
			return this;
		}

		if (distributions != null) {
			for (int i = 0; i < distributions.length; ++i) {
				distributions[i] /= weightSum;
			}
		}

		return this;
	}

	@Override
	public LabelCounter deepCopy() {
		return new LabelCounter(
			this.weightSum,
			this.numInst,
			this.distributions == null
				? null
				: Arrays.copyOf(this.distributions, this.distributions.length)
		);
	}
}
