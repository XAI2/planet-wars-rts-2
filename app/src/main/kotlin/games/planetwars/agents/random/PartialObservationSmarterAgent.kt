package games.planetwars.agents.random

import games.planetwars.agents.Action
import games.planetwars.agents.PartialObservationPlayer
import games.planetwars.core.*
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.max
import kotlin.random.Random

class PartialObservationSmarterAgent : PartialObservationPlayer() {
    private val sequenceLength: Int = 200
    private val nEvals: Int = 60
    private val mutationRate: Double = 0.4
    private val useShiftBuffer: Boolean = true
    private val eliteCount: Int = 3

    private var bestSolution: FloatArray? = null
    private var bestScore: Double = Double.NEGATIVE_INFINITY

    override fun getAction(observation: Observation): Action {
        val myPlanets = observation.observedPlanets.filter { it.owner == player && it.transporter == null }
        val targetPlanets = observation.observedPlanets.filter { it.owner != player }

        if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()

        val greedyMove = getGreedyMove(myPlanets, targetPlanets, observation)
        if (greedyMove != null) return greedyMove

        val wrapper = ObservationWrapper(observation, player)
        val population = mutableListOf<ScoredSeq>()

        // Initialize population
        for (i in 0 until eliteCount) {
            val seq = randomSequence()
            val score = wrapper.runForwardModel(seq)
            population.add(ScoredSeq(seq, score))
        }

        if (bestSolution == null || !useShiftBuffer) {
            bestSolution = population.maxByOrNull { it.score }?.seq ?: randomSequence()
            bestScore = population.maxByOrNull { it.score }?.score ?: -1e9
        } else {
            bestSolution = shiftAndMutate(bestSolution!!)
            bestScore = wrapper.runForwardModel(bestSolution!!)
        }

        var currentBest = bestSolution!!
        var currentBestScore = bestScore

        repeat(nEvals) {
            val parent = population[Random.nextInt(eliteCount)].seq
            val mutated = mutate(parent)
            val score = wrapper.runForwardModel(mutated)
            if (score > currentBestScore) {
                currentBestScore = score
                currentBest = mutated
            }
            if (score > population.minByOrNull { it.score }!!.score) {
                val worstIdx = population.indexOf(population.minByOrNull { it.score })
                population[worstIdx] = ScoredSeq(mutated, score)
            }
        }

        bestSolution = currentBest
        bestScore = currentBestScore

        return wrapper.getAction(observation, bestSolution!![0], bestSolution!![1])
    }

    private fun getGreedyMove(myPlanets: List<PlanetObservation>, targetPlanets: List<PlanetObservation>, observation: Observation): Action? {
        var bestScore = Double.NEGATIVE_INFINITY
        var bestAction: Action? = null
        for (source in myPlanets) {
            for (target in targetPlanets) {
                val score = evaluateMove(source, target)
  //              val sourceShips = (observation.observedPlanets[source.id].nShips ?: 0).toInt()

//                val targetShips = observation.observedPlanets[target.id].nShips ?: Int.MAX_VALUE
//                if (score > bestScore && sourceShips > 8 && targetShips < 30)

                val sourceShips = observation.observedPlanets[source.id].nShips ?: 0.0
                if (sourceShips > 10)
                {
                    bestScore = score
                    bestAction = Action(player, source.id, target.id, max(1.0, source.nShips?.div(2.0) ?: 2.0 ).toDouble())

//                    bestAction = Action(player, source.id, target.id, max(1.0, source.nShips / 2.0).toDouble()) // max(1.0, source.nShips / 2.0).toDouble()
                }
            }
        }
        return bestAction
    }

    private fun evaluateMove(source: PlanetObservation, target: PlanetObservation): Double {
        val distance = sqrt((source.position.x - target.position.x).pow(2.0) + (source.position.y - target.position.y).pow(2.0))
        val effectiveShips = max(1.0, target.nShips ?: 1.0)
        return (target.growthRate.toDouble() / effectiveShips) / (distance + 1) +
                ((source.nShips ?: 1).toDouble() / (effectiveShips * 1.5))
    }

    private fun randomSequence(): FloatArray = FloatArray(sequenceLength) { Random.nextFloat() }

    private fun mutate(seq: FloatArray): FloatArray =
        FloatArray(seq.size) { i ->
            if (Random.nextDouble() < mutationRate) Random.nextFloat() else seq[i]
        }

    private fun shiftAndMutate(seq: FloatArray): FloatArray {
        val shifted = FloatArray(seq.size)
        for (i in 0 until seq.size - 2) {
            shifted[i] = seq[i + 2]
        }
        shifted[shifted.size - 2] = Random.nextFloat()
        shifted[shifted.size - 1] = Random.nextFloat()
        return shifted
    }

    override fun getAgentType(): String = "SmarterAgent-PartialObservation"

    data class ScoredSeq(val seq: FloatArray, val score: Double)

    class ObservationWrapper(
        val observation: Observation,
        val player: Player
    ) {
        fun getAction(observation: Observation, from: Float, to: Float): Action {
            val myPlanets = observation.observedPlanets.filter { it.owner == player && it.transporter == null }
            val targetPlanets = observation.observedPlanets.filter { it.owner != player }
            if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()
            val source = myPlanets[(from * myPlanets.size).toInt().coerceIn(0, myPlanets.lastIndex)]
            val target = targetPlanets[(to * targetPlanets.size).toInt().coerceIn(0, targetPlanets.lastIndex)]
            val sourceShips = observation.observedPlanets[source.id].nShips ?: 1
            return Action(player, source.id, target.id, max(1.0, source.nShips?.div(2.0) ?: 2.0).toDouble())   // max(1.0, source.nShips / 2.0).toDouble()
        }

        fun runForwardModel(seq: FloatArray): Double {
            val myPlanets = observation.observedPlanets.filter { it.owner == player && it.transporter == null }
            val targetPlanets = observation.observedPlanets.filter { it.owner != player }
            if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return -1e9
            val source = myPlanets[(seq[0] * myPlanets.size).toInt().coerceIn(0, myPlanets.lastIndex)]
            val target = targetPlanets[(seq[1] * targetPlanets.size).toInt().coerceIn(0, targetPlanets.lastIndex)]
            val sourceShips = observation.observedPlanets[source.id].nShips ?: 1
            val targetShips = observation.observedPlanets[target.id].nShips ?: 1
            val distance = sqrt((source.position.x - target.position.x).pow(2.0) + (source.position.y - target.position.y).pow(2.0))
            val effectiveShips = max(1.0, targetShips.toDouble())
            return (target.growthRate.toDouble() / effectiveShips) / (distance + 1) +
                    (sourceShips.toDouble() / (effectiveShips * 1.5))
        }
    }
}