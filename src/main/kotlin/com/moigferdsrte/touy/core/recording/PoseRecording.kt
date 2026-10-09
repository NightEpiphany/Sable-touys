package com.moigferdsrte.touy.core.recording

import kotlin.math.abs
import kotlin.math.sqrt

/** Seven primitive doubles per frame; a recording never allocates a frame in its tick loop. */
class PoseRecording private constructor(private val poses: DoubleArray) {
    val frameCount: Int get() = poses.size / COMPONENTS_PER_POSE

    fun component(frame: Int, component: Int): Double {
        require(frame in 0 until frameCount && component in 0 until COMPONENTS_PER_POSE)
        return poses[frame * COMPONENTS_PER_POSE + component]
    }

    fun toBits(): LongArray = LongArray(poses.size) { poses[it].toRawBits() }

    companion object {
        const val COMPONENTS_PER_POSE = 7
        // 672 kB of uncompressed pose data per tape, below vanilla's component NBT limit.
        const val MAX_FRAMES = 12_000
        const val DEFAULT_MAX_FRAMES = 6_000

        fun fromBits(bits: LongArray): PoseRecording? {
            if (bits.isEmpty() || bits.size % COMPONENTS_PER_POSE != 0 || bits.size > MAX_FRAMES * COMPONENTS_PER_POSE) return null
            val values = DoubleArray(bits.size) { Double.fromBits(bits[it]) }
            var offset = 0
            while (offset < values.size) {
                if (!validateAndNormalize(values, offset)) return null
                offset += COMPONENTS_PER_POSE
            }
            return PoseRecording(values)
        }

        internal fun take(values: DoubleArray): PoseRecording = PoseRecording(values)

        internal fun validateAndNormalize(values: DoubleArray, offset: Int): Boolean {
            for (i in 0 until COMPONENTS_PER_POSE) if (!values[offset + i].isFinite()) return false
            for (i in 0..2) if (abs(values[offset + i]) > 30_000_000.0) return false
            val qx = values[offset + 3]
            val qy = values[offset + 4]
            val qz = values[offset + 5]
            val qw = values[offset + 6]
            val norm = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
            if (!norm.isFinite() || norm < 1.0e-12) return false
            for (i in 3..6) values[offset + i] /= norm
            return true
        }
    }
}

/** Allocated once when recording begins, with constant memory use thereafter. */
class PrimitivePoseBuffer(val capacity: Int) {
    private val poses: DoubleArray
    var size: Int = 0
        private set
    val isFull: Boolean get() = size == capacity

    init {
        require(capacity in 1..PoseRecording.MAX_FRAMES)
        poses = DoubleArray(capacity * PoseRecording.COMPONENTS_PER_POSE)
    }

    fun append(x: Double, y: Double, z: Double, qx: Double, qy: Double, qz: Double, qw: Double): Boolean {
        if (isFull) return false
        val offset = size * PoseRecording.COMPONENTS_PER_POSE
        poses[offset] = x
        poses[offset + 1] = y
        poses[offset + 2] = z
        poses[offset + 3] = qx
        poses[offset + 4] = qy
        poses[offset + 5] = qz
        poses[offset + 6] = qw
        if (!PoseRecording.validateAndNormalize(poses, offset)) return false
        size++
        return true
    }

    fun snapshot(): PoseRecording? = if (size == 0) null else PoseRecording.take(poses.copyOf(size * PoseRecording.COMPONENTS_PER_POSE))
}
