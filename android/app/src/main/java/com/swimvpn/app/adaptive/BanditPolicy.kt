package com.swimvpn.app.adaptive

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure, DETERMINISTIC UCB1 bandit over arms with time-decayed success/failure mass. No Android deps, no
 * side effects, no RNG (deterministic → trivially unit-testable and easy to verify against the spec's
 * invariants). See docs/superpowers/specs/2026-06-08-adaptive-bandit-design.md.
 *
 * Exploration comes from the uncertainty BONUS: an under-sampled or never-tried arm gets a high bonus
 * and a fair chance — but ONLY when a selection is already needed (the caller decides that, never this
 * object). This never triggers a switch on a healthy session (spec I1/I2): it is a pure ranking function.
 *
 * RECENCY: callers decay the stored mass with [decay] before scoring, so old outcomes fade continuously
 * (no binary 30-min cliff, no unbounded counter that saturates the score).
 */
object BanditPolicy {
    const val DEFAULT_HALF_LIFE_MS: Long = 48L * 60 * 60 * 1000 // 48h
    // GENTLE exploration on purpose (spec I1/I2): a selection's "exploration cost" is the user's real
    // session, so we exploit by default — the bonus only gives a never-tried arm a fair chance and breaks
    // near-ties, it does NOT let a thin arm dethrone a strongly-proven one. Tunable on device.
    const val DEFAULT_EXPLORATION_C: Double = 0.2

    /** Exponential time-decay of a stored mass toward 0 (half-life [halfLifeMs]). */
    fun decay(mass: Double, lastUpdateMs: Long, nowMs: Long, halfLifeMs: Long = DEFAULT_HALF_LIFE_MS): Double {
        if (mass <= 0.0) return 0.0
        if (nowMs <= lastUpdateMs || halfLifeMs <= 0L) return mass
        return mass * 0.5.pow((nowMs - lastUpdateMs).toDouble() / halfLifeMs)
    }

    /**
     * UCB score for one arm (higher = more preferred). [priorMean] in [0,1] seeds cold-start (e.g. from
     * ping/freshness); with zero observed mass the score's mean term equals [priorMean]. [totalMass] is
     * the summed decayed mass across all candidates (the UCB "N"). [explorationC] weights the bonus.
     *
     * mean  = (successMass + priorMean) / (mass + 1)   // unit pseudo-count toward the prior
     * bonus = c * sqrt(ln(totalMass + 1) / (mass + 1)) // shrinks as the arm accrues recent samples
     */
    fun ucbScore(
        successMass: Double,
        failureMass: Double,
        totalMass: Double,
        priorMean: Double,
        explorationC: Double = DEFAULT_EXPLORATION_C,
    ): Double {
        val mass = (successMass + failureMass).coerceAtLeast(0.0)
        val mean = (successMass.coerceAtLeast(0.0) + priorMean.coerceIn(0.0, 1.0)) / (mass + 1.0)
        val bonus = explorationC * sqrt(ln(totalMass.coerceAtLeast(0.0) + 1.0) / (mass + 1.0))
        return mean + bonus
    }

    /**
     * Pick the candidate with the highest [score]. Candidates MUST already be pre-filtered by the caller
     * (avoided / probe-failed / blocked removed — spec I6) and ordered for a deterministic tie-break
     * (e.g. by ping then id): on equal scores the FIRST candidate wins. Returns null on an empty list.
     */
    fun <T> select(candidates: List<T>, score: (T) -> Double): T? {
        var best: T? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (c in candidates) {
            val s = score(c)
            if (s > bestScore) { bestScore = s; best = c }
        }
        return best
    }
}
