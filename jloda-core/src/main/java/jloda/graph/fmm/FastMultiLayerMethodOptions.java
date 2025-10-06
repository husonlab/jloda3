/*
 * FastMultiLayerMethodOptions.java (updated & documented)
 * Copyright (C) 2024 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jloda.graph.fmm;

/**
 * Options for the Fast Multilayer Method layout (FMMM) without the multipole step.
 *
 * <p>Notes on scales:</p>
 * <ul>
 *   <li>{@code unitEdgeLength} (UEL) serves as the base scale for ideal edge lengths. Some forces are later scaled by UEL^2.</li>
 *   <li>Many thresholds and temperatures are unitless multipliers applied during force integration.</li>
 * </ul>
 *
 * <p>Defaults correspond roughly to a reasonable “general layout” profile. For domain-specific presets,
 * see {@link #getDefaultForMicrobialGenomes()}.</p>
 * <p>
 * Original C++ author: Stefan Hachul, original license: GPL
 * Reimplemented in Java by Daniel Huson, 3.2021; updated using ChatGPT 2025-09
 */
public class FastMultiLayerMethodOptions {

	/* =========================
	 * Global / scale options
	 * ========================= */

	/**
	 * Base ideal edge length; must be positive. Typical: 1–200.
	 */
	private float unitEdgeLength;

	/**
	 * Random seed used wherever determinism is desired.
	 */
	private int randSeed;

	/**
	 * How to measure ideal edge lengths when node radii matter.
	 */
	public enum EdgeLengthMeasurement {BoundingCircle, MidPoint}

	private EdgeLengthMeasurement edgeLengthMeasurement;

	/**
	 * Whether node positions are free or snapped to integers.
	 */
	public enum AllowedPositions {Integer, All}

	private AllowedPositions allowedPositions;

	/* =========================
	 * Divide & conquer options
	 * ========================= */

	/**
	 * Optional component rotation passes (per level). 0 disables.
	 */
	private int stepsForRotatingComponents;

	/* =========================
	 * Multilevel options
	 * ========================= */

	/**
	 * Minimum graph size for coarsening (below → stop multilevel).
	 */
	private int minGraphSize;

	/**
	 * Choice of coarse node sampling during galaxy coarsening.
	 */
	public enum GalaxyChoice {
		UniformProb,
		NonUniformProbLowerMass,
		NonUniformProbHigherMass
	}

	private GalaxyChoice galaxyChoice;

	/**
	 * Number of random tries used in certain heuristics (e.g., smoothing).
	 */
	private int numberRandomTries;

	/**
	 * How the per-level iteration budget changes from coarse to fine.
	 */
	public enum MaxIterChange {Constant, LinearlyDecreasing, RapidlyDecreasing}

	private MaxIterChange maxIterChange;

	/**
	 * Factor (&ge;1) that inflates coarse-level iterations, relative to {@code fixedIterations}.
	 */
	private int maxIterFactor;

	/**
	 * Initial placement strategy for multilevel (reserved for future use).
	 */
	public enum InitialPlacementMultiLayer {Advanced}

	private InitialPlacementMultiLayer initialPlacementMult;

	/**
	 * Force single-level behavior (skip multilevel).
	 */
	private boolean mSingleLevel;

	/* =========================
	 * Force calculation options
	 * ========================= */

	/**
	 * Spring (attractive) model.
	 */
	public enum ForceModel {FruchtermanReingold, New, Eades}

	private ForceModel forceModel;

	/**
	 * Spring strength multiplier (&ge;0).
	 */
	private int springStrength;

	/**
	 * Repulsion strength multiplier (&ge;0).
	 */
	private int repForcesStrength;

	/**
	 * Repulsive field calculation scheme.
	 */
	public enum RepulsiveForcesCalculation {Exact, GridApproximation /* , MultipoleMethod*/}

	private RepulsiveForcesCalculation repulsiveForcesCalculation;

	/**
	 * Stopping rule for the force iterations.
	 */
	public enum StopCriterion {FixedIterationsOrThreshold, FixedIterations, Threshold}

	private StopCriterion stopCriterion;

	/**
	 * Threshold (average force length) for early stopping (&ge;0).
	 */
	private float threshold;

	/**
	 * Base iterations per level (&ge;1). Coarse levels may multiply this via {@code maxIterFactor}.
	 */
	private int fixedIterations;

	/**
	 * Global multiplier on final force vector before moving nodes (&gt;0).
	 */
	private float forceScalingFactor;

	/**
	 * Enable geometric cooling over iterations.
	 */
	private boolean coolTemperature;

	/**
	 * Cooling factor per iteration (0&lt;c&lt;1) when cooling is enabled.
	 */
	private float coolValue;

	/**
	 * Initial placement at the finest level.
	 */
	public enum InitialPlacementForces {UniformGrid, RandomTime, RandomRandIterNr, KeepPositions}

	private InitialPlacementForces initialPlacementForces;

	/* =========================
	 * Postprocessing options
	 * ========================= */

	/**
	 * Whether to resize final drawing to match ideal average edge length.
	 */
	private boolean resizeDrawing;

	/**
	 * Extra scaling applied during resizing (typically 1).
	 */
	private int resizingScalar;

	/**
	 * Fine-tuning iterations (&ge;0).
	 */
	private int fineTuningIterations;

	/**
	 * Fine-tuning temperature scalar (0&lt;s≤1).
	 */
	private float fineTuneScalar;

	/**
	 * If true, adapt post repulsion dynamically w.r.t. graph size.
	 */
	private boolean adjustPostRepStrengthDynamically;

	/**
	 * Post-processing spring strength.
	 */
	private float postSpringStrength;

	/**
	 * Post-processing repulsion strength (ignored if dynamic is true).
	 */
	private float postStrengthOfRepForces;

	/* =========================
	 * Repulsion grid options
	 * ========================= */

	/**
	 * Grid coarsening parameter for FR grid approximation.
	 * Effective grid size is roughly sqrt(n) / frGridQuotient, so this must be ≥1.
	 */
	private int frGridQuotient;

	/* =========================
	 * DHH extensions
	 * ========================= */

	/**
	 * Use simple chain/cycle placement heuristics when applicable.
	 */
	private boolean useSimpleAlgorithmForChainsAndCycles;

	/**
	 * If &gt;0, enable chain smoothing rounds after force steps.
	 */
	private int numberOfChainSmoothingRounds = 0;

	/* =========================
	 * Construction / defaults
	 * ========================= */

	public FastMultiLayerMethodOptions() {
		initialize();
	}

	/**
	 * Reset all options to defaults.
	 * Defaults chosen for general-purpose layouts; guarded with basic validity constraints.
	 */
	public void initialize() {
		setUnitEdgeLength(100f);

		setRandSeed(666);
		setEdgeLengthMeasurement(EdgeLengthMeasurement.BoundingCircle);
		setAllowedPositions(AllowedPositions.Integer);

		setStepsForRotatingComponents(10);

		// Multilevel
		setMinGraphSize(50);
		setGalaxyChoice(GalaxyChoice.NonUniformProbLowerMass);
		setNumberRandomTries(20);
		setMaxIterChange(MaxIterChange.LinearlyDecreasing);
		setMaxIterFactor(10);
		setInitialPlacementMult(InitialPlacementMultiLayer.Advanced);
		setMSingleLevel(false);

		// Forces
		setForceModel(ForceModel.New);
		setSpringStrength(1);
		setRepForcesStrength(1);
		setRepulsiveForcesCalculation(RepulsiveForcesCalculation.GridApproximation);
		setStopCriterion(StopCriterion.FixedIterationsOrThreshold);
		setThreshold(0.01f);
		setFixedIterations(30);
		setForceScalingFactor(0.05f);
		setCoolTemperature(false);
		setCoolValue(0.99f);
		setInitialPlacementForces(InitialPlacementForces.RandomRandIterNr);

		// Post
		setResizeDrawing(true);
		setResizingScalar(1);
		setFineTuningIterations(20);
		setFineTuneScalar(0.2f);
		setAdjustPostRepStrengthDynamically(true);
		setPostSpringStrength(2.0f);
		setPostStrengthOfRepForces(0.01f);

		// Grid repulsion
		setFrGridQuotient(2);

		// Heuristics
		setUseSimpleAlgorithmForChainsAndCycles(true);
		setNumberOfChainSmoothingRounds(0);
	}

	/* =========================
	 * Getters / Setters (with guards)
	 * ========================= */

	public float getUnitEdgeLength() {
		return unitEdgeLength;
	}

	public void setUnitEdgeLength(float unitEdgeLength) {
		this.unitEdgeLength = (unitEdgeLength > 0 ? unitEdgeLength : 1f);
	}

	public int getRandSeed() {
		return randSeed;
	}

	public void setRandSeed(int randSeed) {
		this.randSeed = randSeed;
	}

	public EdgeLengthMeasurement getEdgeLengthMeasurement() {
		return edgeLengthMeasurement;
	}

	public void setEdgeLengthMeasurement(EdgeLengthMeasurement edgeLengthMeasurement) {
		this.edgeLengthMeasurement = (edgeLengthMeasurement == null ? EdgeLengthMeasurement.BoundingCircle : edgeLengthMeasurement);
	}

	public AllowedPositions getAllowedPositions() {
		return allowedPositions;
	}

	public void setAllowedPositions(AllowedPositions allowedPositions) {
		this.allowedPositions = (allowedPositions == null ? AllowedPositions.All : allowedPositions);
	}

	public int getStepsForRotatingComponents() {
		return stepsForRotatingComponents;
	}

	public void setStepsForRotatingComponents(int stepsForRotatingComponents) {
		this.stepsForRotatingComponents = Math.max(0, stepsForRotatingComponents);
	}

	public int getMinGraphSize() {
		return minGraphSize;
	}

	public void setMinGraphSize(int minGraphSize) {
		this.minGraphSize = Math.max(1, minGraphSize);
	}

	public GalaxyChoice getGalaxyChoice() {
		return galaxyChoice;
	}

	public void setGalaxyChoice(GalaxyChoice galaxyChoice) {
		this.galaxyChoice = (galaxyChoice == null ? GalaxyChoice.UniformProb : galaxyChoice);
	}

	public int getNumberRandomTries() {
		return numberRandomTries;
	}

	public void setNumberRandomTries(int numberRandomTries) {
		this.numberRandomTries = Math.max(0, numberRandomTries);
	}

	public MaxIterChange getMaxIterChange() {
		return maxIterChange;
	}

	public void setMaxIterChange(MaxIterChange maxIterChange) {
		this.maxIterChange = (maxIterChange == null ? MaxIterChange.Constant : maxIterChange);
	}

	public int getMaxIterFactor() {
		return maxIterFactor;
	}

	public void setMaxIterFactor(int maxIterFactor) {
		this.maxIterFactor = Math.max(1, maxIterFactor);
	}

	public InitialPlacementMultiLayer getInitialPlacementMult() {
		return initialPlacementMult;
	}

	public void setInitialPlacementMult(InitialPlacementMultiLayer initialPlacementMult) {
		this.initialPlacementMult = (initialPlacementMult == null ? InitialPlacementMultiLayer.Advanced : initialPlacementMult);
	}

	public boolean isMSingleLevel() {
		return mSingleLevel;
	}

	public void setMSingleLevel(boolean mSingleLevel) {
		this.mSingleLevel = mSingleLevel;
	}

	public ForceModel getForceModel() {
		return forceModel;
	}

	public void setForceModel(ForceModel forceModel) {
		this.forceModel = (forceModel == null ? ForceModel.FruchtermanReingold : forceModel);
	}

	public int getSpringStrength() {
		return springStrength;
	}

	public void setSpringStrength(int springStrength) {
		this.springStrength = Math.max(0, springStrength);
	}

	public int getRepForcesStrength() {
		return repForcesStrength;
	}

	public void setRepForcesStrength(int repForcesStrength) {
		this.repForcesStrength = Math.max(0, repForcesStrength);
	}

	public RepulsiveForcesCalculation getRepulsiveForcesCalculation() {
		return repulsiveForcesCalculation;
	}

	public void setRepulsiveForcesCalculation(RepulsiveForcesCalculation repulsiveForcesCalculation) {
		this.repulsiveForcesCalculation = (repulsiveForcesCalculation == null ? RepulsiveForcesCalculation.Exact : repulsiveForcesCalculation);
	}

	public StopCriterion getStopCriterion() {
		return stopCriterion;
	}

	public void setStopCriterion(StopCriterion stopCriterion) {
		this.stopCriterion = (stopCriterion == null ? StopCriterion.FixedIterations : stopCriterion);
	}

	public float getThreshold() {
		return threshold;
	}

	public void setThreshold(float threshold) {
		this.threshold = Math.max(0f, threshold);
	}

	public int getFixedIterations() {
		return fixedIterations;
	}

	public void setFixedIterations(int fixedIterations) {
		this.fixedIterations = Math.max(1, fixedIterations);
	}

	public float getForceScalingFactor() {
		return forceScalingFactor;
	}

	public void setForceScalingFactor(float forceScalingFactor) {
		this.forceScalingFactor = (forceScalingFactor > 0f ? forceScalingFactor : 1e-4f);
	}

	public boolean isCoolTemperature() {
		return coolTemperature;
	}

	public void setCoolTemperature(boolean coolTemperature) {
		this.coolTemperature = coolTemperature;
	}

	public float getCoolValue() {
		return coolValue;
	}

	public void setCoolValue(float coolValue) {
		// If cooling is enabled, keep 0<coolValue<1 for geometric cooling.
		if (coolTemperature) {
			this.coolValue = (coolValue > 0f && coolValue < 1f) ? coolValue : 0.99f;
		} else {
			this.coolValue = coolValue; // ignored
		}
	}

	public InitialPlacementForces getInitialPlacementForces() {
		return initialPlacementForces;
	}

	public void setInitialPlacementForces(InitialPlacementForces initialPlacementForces) {
		this.initialPlacementForces = (initialPlacementForces == null ? InitialPlacementForces.RandomRandIterNr : initialPlacementForces);
	}

	public boolean isResizeDrawing() {
		return resizeDrawing;
	}

	public void setResizeDrawing(boolean resizeDrawing) {
		this.resizeDrawing = resizeDrawing;
	}

	public int getResizingScalar() {
		return resizingScalar;
	}

	public void setResizingScalar(int resizingScalar) {
		this.resizingScalar = Math.max(1, resizingScalar);
	}

	public int getFineTuningIterations() {
		return fineTuningIterations;
	}

	public void setFineTuningIterations(int fineTuningIterations) {
		this.fineTuningIterations = Math.max(0, fineTuningIterations);
	}

	public float getFineTuneScalar() {
		return fineTuneScalar;
	}

	public void setFineTuneScalar(float fineTuneScalar) {
		// usual range (0,1]; clamp to avoid amplification
		if (fineTuneScalar <= 0f) this.fineTuneScalar = 0.1f;
		else this.fineTuneScalar = Math.min(fineTuneScalar, 1f);
	}

	public boolean isAdjustPostRepStrengthDynamically() {
		return adjustPostRepStrengthDynamically;
	}

	public void setAdjustPostRepStrengthDynamically(boolean adjustPostRepStrengthDynamically) {
		this.adjustPostRepStrengthDynamically = adjustPostRepStrengthDynamically;
	}

	public float getPostSpringStrength() {
		return postSpringStrength;
	}

	public void setPostSpringStrength(float postSpringStrength) {
		this.postSpringStrength = Math.max(0f, postSpringStrength);
	}

	public float getPostStrengthOfRepForces() {
		return postStrengthOfRepForces;
	}

	public void setPostStrengthOfRepForces(float postStrengthOfRepForces) {
		this.postStrengthOfRepForces = Math.max(0f, postStrengthOfRepForces);
	}

	public int getFrGridQuotient() {
		return frGridQuotient;
	}

	public void setFrGridQuotient(int frGridQuotient) {
		// used as a divisor; must be ≥1 to avoid division by zero / huge grids
		this.frGridQuotient = Math.max(1, frGridQuotient);
	}

	public boolean isUseSimpleAlgorithmForChainsAndCycles() {
		return useSimpleAlgorithmForChainsAndCycles;
	}

	public void setUseSimpleAlgorithmForChainsAndCycles(boolean useSimpleAlgorithmForChainsAndCycles) {
		this.useSimpleAlgorithmForChainsAndCycles = useSimpleAlgorithmForChainsAndCycles;
	}

	public int getNumberOfChainSmoothingRounds() {
		return numberOfChainSmoothingRounds;
	}

	public void setNumberOfChainSmoothingRounds(int numberOfChainSmoothingRounds) {
		this.numberOfChainSmoothingRounds = Math.max(0, numberOfChainSmoothingRounds);
	}

	/**
	 * A domain-specific preset used by DHH.
	 * Tweaks some defaults for (typically) smaller-scale microbial genome graphs.
	 */
	public static FastMultiLayerMethodOptions getDefaultForMicrobialGenomes() {
		var options = new FastMultiLayerMethodOptions();

		// If needed, you can uncomment to use exact repulsion:
		// options.setRepulsiveForcesCalculation(RepulsiveForcesCalculation.Exact);

		options.setGalaxyChoice(GalaxyChoice.NonUniformProbHigherMass);
		options.setAllowedPositions(AllowedPositions.All);
		options.setUnitEdgeLength(1.0f);
		options.setStepsForRotatingComponents(50);

		options.setUseSimpleAlgorithmForChainsAndCycles(false);
		options.setNumberOfChainSmoothingRounds(100);

		options.setMaxIterChange(MaxIterChange.LinearlyDecreasing);
		// options.setFixedIterations(30);
		// options.setFineTuningIterations(20);
		return options;
	}

	public void validate() {
		validate(false);
	}

	public void validate(boolean strict) {
		StringBuilder issues = new StringBuilder();

		// --- Basic positive guards / clamps ---
		if (unitEdgeLength <= 0f) {
			issues.append("unitEdgeLength <= 0; clamped to 1. ").append('\n');
			unitEdgeLength = 1f;
		}
		if (fixedIterations < 1) {
			issues.append("fixedIterations < 1; set to 1. ").append('\n');
			fixedIterations = 1;
		}
		if (resizingScalar < 1) {
			issues.append("resizingScalar < 1; set to 1. ").append('\n');
			resizingScalar = 1;
		}
		if (fineTuningIterations < 0) {
			issues.append("fineTuningIterations < 0; set to 0. ").append('\n');
			fineTuningIterations = 0;
		}
		if (springStrength < 0) {
			issues.append("springStrength < 0; set to 0. ").append('\n');
			springStrength = 0;
		}
		if (repForcesStrength < 0) {
			issues.append("repForcesStrength < 0; set to 0. ").append('\n');
			repForcesStrength = 0;
		}
		if (frGridQuotient < 1) {
			issues.append("frGridQuotient < 1; set to 1. ").append('\n');
			frGridQuotient = 1;
		}
		if (maxIterFactor < 1) {
			issues.append("maxIterFactor < 1; set to 1. ").append('\n');
			maxIterFactor = 1;
		}
		if (minGraphSize < 1) {
			issues.append("minGraphSize < 1; set to 1. ").append('\n');
			minGraphSize = 1;
		}
		if (threshold < 0f) {
			issues.append("threshold < 0; set to 0. ").append('\n');
			threshold = 0f;
		}
		if (forceScalingFactor <= 0f) {
			issues.append("forceScalingFactor <= 0; set to 1e-4. ").append('\n');
			forceScalingFactor = 1e-4f;
		}

		// --- Enum null-safety (in case of external deserialization) ---
		if (edgeLengthMeasurement == null) {
			issues.append("edgeLengthMeasurement == null; defaulted to BoundingCircle. ").append('\n');
			edgeLengthMeasurement = EdgeLengthMeasurement.BoundingCircle;
		}
		if (allowedPositions == null) {
			issues.append("allowedPositions == null; defaulted to All. ").append('\n');
			allowedPositions = AllowedPositions.All;
		}
		if (galaxyChoice == null) {
			issues.append("galaxyChoice == null; defaulted to UniformProb. ").append('\n');
			galaxyChoice = GalaxyChoice.UniformProb;
		}
		if (maxIterChange == null) {
			issues.append("maxIterChange == null; defaulted to Constant. ").append('\n');
			maxIterChange = MaxIterChange.Constant;
		}
		if (initialPlacementMult == null) {
			issues.append("initialPlacementMult == null; defaulted to Advanced. ").append('\n');
			initialPlacementMult = InitialPlacementMultiLayer.Advanced;
		}
		if (forceModel == null) {
			issues.append("forceModel == null; defaulted to FruchtermanReingold. ").append('\n');
			forceModel = ForceModel.FruchtermanReingold;
		}
		if (repulsiveForcesCalculation == null) {
			issues.append("repulsiveForcesCalculation == null; defaulted to Exact. ").append('\n');
			repulsiveForcesCalculation = RepulsiveForcesCalculation.Exact;
		}
		if (stopCriterion == null) {
			issues.append("stopCriterion == null; defaulted to FixedIterations. ").append('\n');
			stopCriterion = StopCriterion.FixedIterations;
		}
		if (initialPlacementForces == null) {
			issues.append("initialPlacementForces == null; defaulted to RandomRandIterNr. ").append('\n');
			initialPlacementForces = InitialPlacementForces.RandomRandIterNr;
		}

		// --- Cooling semantics ---
		if (coolTemperature) {
			if (!(coolValue > 0f && coolValue < 1f)) {
				issues.append("coolTemperature=true but coolValue not in (0,1); set to 0.99. ").append('\n');
				coolValue = 0.99f;
			}
		}
		// If cooling disabled, coolValue is ignored; no change needed.

		// --- Fine-tuning scalar bounds ---
		if (fineTuneScalar <= 0f) {
			issues.append("fineTuneScalar <= 0; set to 0.1. ").append('\n');
			fineTuneScalar = 0.1f;
		} else if (fineTuneScalar > 1f) {
			issues.append("fineTuneScalar > 1; clamped to 1. ").append('\n');
			fineTuneScalar = 1f;
		}

		// --- Stop-criterion semantics hints (non-fatal adjustments) ---
		// If purely threshold-based, we still rely on internal safety caps in the solver; nothing to fix here.
		// If purely fixed-iterations, threshold is ignored; keep for API stability.

		// --- Optional strict mode: throw on hard contradictions (rare after sanitization) ---
		if (strict && !issues.isEmpty()) {
			throw new IllegalArgumentException("FastMultiLayerMethodOptions.validate(strict) found issues:\n" + issues);
		}
	}
}