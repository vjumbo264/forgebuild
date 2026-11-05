package com.motionsalt.ccstudio.core

import kotlin.math.*

data class SolverProgress(
    val step: Int,
    val totalSteps: Int,
    val stepName: String,
    val progressFraction: Float
)

data class SolverResult(
    val transform: FittedTransform,
    val meanDeltaE: Float,
    val p95DeltaE: Float,
    val sampleCount: Int
)

object LutSolver {

    fun fit(
        pairs: List<PixelPair>,
        gridSize: Int = 17,
        onProgress: (SolverProgress) -> Unit = {}
    ): SolverResult {
        if (pairs.isEmpty()) {
            val identity = createIdentityTransform(gridSize)
            return SolverResult(identity, 0f, 0f, 0)
        }

        // STEP 1: PREPARE
        onProgress(SolverProgress(1, 6, "Prepare", 0.15f))
        val maxSamples = 4000
        val trainingPairs = if (pairs.size > maxSamples) {
            val step = pairs.size / maxSamples
            pairs.filterIndexed { index, _ -> index % step == 0 }.take(maxSamples)
        } else {
            pairs
        }

        // STEP 2: 1D TONE CURVES
        onProgress(SolverProgress(2, 6, "Curves", 0.35f))
        val curveR = fitToneCurve(trainingPairs.map { it.base.first to it.target.first })
        val curveG = fitToneCurve(trainingPairs.map { it.base.second to it.target.second })
        val curveB = fitToneCurve(trainingPairs.map { it.base.third to it.target.third })

        // Apply curves to training base
        val afterCurves = trainingPairs.map { pair ->
            val cr = curveR[((pair.base.first.coerceIn(0f, 1f) * 255f).roundToInt()).coerceIn(0, 255)]
            val cg = curveG[((pair.base.second.coerceIn(0f, 1f) * 255f).roundToInt()).coerceIn(0, 255)]
            val cb = curveB[((pair.base.third.coerceIn(0f, 1f) * 255f).roundToInt()).coerceIn(0, 255)]
            PixelPair(Triple(cr, cg, cb), pair.target)
        }

        // STEP 3: 3x3 MATRIX + OFFSET
        onProgress(SolverProgress(3, 6, "Matrix", 0.55f))
        val (matrix, matrixOffset) = fitMatrix(afterCurves)

        // Apply matrix
        val afterMatrix = afterCurves.map { pair ->
            val cr = pair.base.first
            val cg = pair.base.second
            val cb = pair.base.third
            var mr = matrix[0][0] * cr + matrix[0][1] * cg + matrix[0][2] * cb + matrixOffset[0]
            var mg = matrix[1][0] * cr + matrix[1][1] * cg + matrix[1][2] * cb + matrixOffset[1]
            var mb = matrix[2][0] * cr + matrix[2][1] * cg + matrix[2][2] * cb + matrixOffset[2]
            mr = mr.coerceIn(0f, 1f)
            mg = mg.coerceIn(0f, 1f)
            mb = mb.coerceIn(0f, 1f)
            PixelPair(Triple(mr, mg, mb), pair.target)
        }

        // STEP 4: RESIDUAL 3D LATTICE
        onProgress(SolverProgress(4, 6, "Residual", 0.75f))
        val residual = fitResidualLattice(afterMatrix, gridSize)

        val transform = FittedTransform(
            curveR = curveR,
            curveG = curveG,
            curveB = curveB,
            matrix = matrix,
            matrixOffset = matrixOffset,
            gridSize = gridSize,
            residual = residual,
            strength = 1.0f
        )

        // STEP 5: VALIDATE & COMPUTE DELTA E
        onProgress(SolverProgress(5, 6, "Validate", 0.90f))
        val deltaEs = FloatArray(trainingPairs.size)
        for (i in trainingPairs.indices) {
            val p = trainingPairs[i]
            val eval = transform.evaluate(p.base.first, p.base.second, p.base.third)
            val labEval = ColorMath.rgbToLab(eval.first, eval.second, eval.third)
            val labTarget = ColorMath.rgbToLab(p.target.first, p.target.second, p.target.third)
            deltaEs[i] = ColorMath.deltaE76(labEval, labTarget)
        }
        deltaEs.sort()

        val meanDeltaE = if (deltaEs.isNotEmpty()) deltaEs.average().toFloat() else 0f
        val p95Index = ((deltaEs.size - 1) * 0.95f).roundToInt().coerceIn(0, max(0, deltaEs.size - 1))
        val p95DeltaE = if (deltaEs.isNotEmpty()) deltaEs[p95Index] else 0f

        // STEP 6: BUILD LUT / HALD READY
        onProgress(SolverProgress(6, 6, "Build LUT/Hald", 1.0f))

        return SolverResult(
            transform = transform,
            meanDeltaE = meanDeltaE,
            p95DeltaE = p95DeltaE,
            sampleCount = trainingPairs.size
        )
    }

    private fun createIdentityTransform(gridSize: Int = 17): FittedTransform {
        val identityCurve = FloatArray(256) { it / 255f }
        val matrix = arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f)
        )
        val offset = floatArrayOf(0f, 0f, 0f)
        val residual = FloatArray(gridSize * gridSize * gridSize * 3)
        return FittedTransform(identityCurve, identityCurve, identityCurve, matrix, offset, gridSize, residual, 1.0f)
    }

    private fun fitToneCurve(pairs: List<Pair<Float, Float>>): FloatArray {
        val bins = 256
        val sumTarget = FloatArray(bins)
        val countTarget = IntArray(bins)

        for (p in pairs) {
            val bin = (p.first.coerceIn(0f, 1f) * (bins - 1)).roundToInt().coerceIn(0, bins - 1)
            sumTarget[bin] += p.second.coerceIn(0f, 1f)
            countTarget[bin]++
        }

        val curve = FloatArray(bins)
        var lastVal = 0f
        for (i in 0 until bins) {
            if (countTarget[i] > 0) {
                lastVal = sumTarget[i] / countTarget[i]
            } else if (i == 0) {
                lastVal = 0f
            } else {
                val nextKnown = (i + 1 until bins).firstOrNull { countTarget[it] > 0 }
                if (nextKnown != null) {
                    val nextVal = sumTarget[nextKnown] / countTarget[nextKnown]
                    val alpha = (i - (i - 1)).toFloat() / (nextKnown - (i - 1))
                    lastVal = lastVal * (1f - alpha) + nextVal * alpha
                }
            }
            curve[i] = lastVal.coerceIn(0f, 1f)
        }

        // Monotonic smoothing
        for (i in 1 until bins) {
            if (curve[i] < curve[i - 1]) {
                curve[i] = curve[i - 1]
            }
        }
        return curve
    }

    private fun fitMatrix(pairs: List<PixelPair>): Pair<Array<FloatArray>, FloatArray> {
        val matrix = arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f)
        )
        val offset = floatArrayOf(0f, 0f, 0f)

        if (pairs.isEmpty()) return matrix to offset

        // Compute mean differences for quick chromatic adaptation
        var sumDiffR = 0f
        var sumDiffG = 0f
        var sumDiffB = 0f
        for (p in pairs) {
            sumDiffR += (p.target.first - p.base.first)
            sumDiffG += (p.target.second - p.base.second)
            sumDiffB += (p.target.third - p.base.third)
        }
        val n = pairs.size.toFloat()
        offset[0] = (sumDiffR / n).coerceIn(-0.3f, 0.3f)
        offset[1] = (sumDiffG / n).coerceIn(-0.3f, 0.3f)
        offset[2] = (sumDiffB / n).coerceIn(-0.3f, 0.3f)

        return matrix to offset
    }

    private fun fitResidualLattice(pairs: List<PixelPair>, gridSize: Int): FloatArray {
        val totalCells = gridSize * gridSize * gridSize
        val residual = FloatArray(totalCells * 3)
        val weights = FloatArray(totalCells)

        for (p in pairs) {
            val r = p.base.first.coerceIn(0f, 1f)
            val g = p.base.second.coerceIn(0f, 1f)
            val b = p.base.third.coerceIn(0f, 1f)

            val x = (r * (gridSize - 1)).roundToInt().coerceIn(0, gridSize - 1)
            val y = (g * (gridSize - 1)).roundToInt().coerceIn(0, gridSize - 1)
            val z = (b * (gridSize - 1)).roundToInt().coerceIn(0, gridSize - 1)

            val idx = (z * gridSize * gridSize + y * gridSize + x) * 3
            val cellIdx = z * gridSize * gridSize + y * gridSize + x

            val diffR = (p.target.first - p.base.first).coerceIn(-0.5f, 0.5f)
            val diffG = (p.target.second - p.base.second).coerceIn(-0.5f, 0.5f)
            val diffB = (p.target.third - p.base.third).coerceIn(-0.5f, 0.5f)

            residual[idx] += diffR
            residual[idx + 1] += diffG
            residual[idx + 2] += diffB
            weights[cellIdx] += 1f
        }

        // Average residuals
        for (i in 0 until totalCells) {
            val w = weights[i]
            if (w > 0f) {
                residual[i * 3] /= w
                residual[i * 3 + 1] /= w
                residual[i * 3 + 2] /= w
            }
        }

        return residual
    }
}
