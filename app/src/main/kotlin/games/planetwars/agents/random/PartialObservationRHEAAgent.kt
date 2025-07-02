package games.planetwars.agents.random

import games.planetwars.agents.Action
import games.planetwars.agents.PartialObservationPlayer
import games.planetwars.core.Observation
import util.Vec2d
import kotlin.math.sqrt
import kotlin.random.Random

class PartialObservationRHEAAgent(
    private val sequenceLength: Int = 10,
    private val populationSize: Int = 60,
    private val generations: Int = 50,
    private val mutationRate: Double = 0.2,
    private val eliteCount: Int = 6
) : PartialObservationPlayer() {

    override fun getAction(observation: Observation): Action {
        val myPlanets = observation.observedPlanets.filter { it.owner == player && it.transporter == null }
        val targetPlanets = observation.observedPlanets.filter { it.owner != player && it.owner != null }

        if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()

        val dynamicLength = maxOf(1, sequenceLength)
        val floatLength = dynamicLength * 2

        val population = MutableList(populationSize) {
            if (it == 0) greedyFloatSequence(myPlanets, targetPlanets, dynamicLength)
            else randomFloatSequence(floatLength)
        }

        repeat(generations) {
            val scored = population.map {
                it to evaluateFloatSequence(it, observation, myPlanets, targetPlanets, dynamicLength)
            }.sortedByDescending { it.second }

            val elites = scored.take(eliteCount).map { it.first.copyOf() }
            val best = scored.take(populationSize / 2).map { it.first }

            population.clear()
            population.addAll(elites)
            while (population.size < populationSize) {
                val parent = best.random()
                population.add(mutateFloatSequence(parent))
            }
        }

        val bestSeq = population.maxByOrNull {
            evaluateFloatSequence(it, observation, myPlanets, targetPlanets, dynamicLength)
        } ?: return Action.doNothing()

        if (bestSeq.isEmpty() || myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()
        val from = (bestSeq[0] * myPlanets.size).toInt().coerceIn(0, myPlanets.lastIndex)
        val to = (bestSeq[1] * targetPlanets.size).toInt().coerceIn(0, targetPlanets.lastIndex)
        val ships = myPlanets[from].nShips?.times(0.5) ?: return Action.doNothing()
        return Action(player, myPlanets[from].id, targetPlanets[to].id, ships)
    }

    private fun randomFloatSequence(length: Int): FloatArray {
        return FloatArray(length) { Random.nextFloat().coerceIn(0f, 1f) }
    }

    private fun mutateFloatSequence(seq: FloatArray): FloatArray {
        return FloatArray(seq.size) { i ->
            if (Random.nextDouble() < mutationRate) Random.nextFloat().coerceIn(0f, 1f) else seq[i]
        }
    }

    private fun greedyFloatSequence(
        myPlanets: List<games.planetwars.core.PlanetObservation>,
        targetPlanets: List<games.planetwars.core.PlanetObservation>,
        length: Int
    ): FloatArray {
        if (myPlanets.isEmpty() || targetPlanets.isEmpty() || length <= 0) return FloatArray(0)

        val fromIndex = myPlanets.indexOf(myPlanets.maxByOrNull { it.nShips ?: 0.0 } ?: return FloatArray(0))
        val toIndex = targetPlanets.indexOf(targetPlanets.minByOrNull { it.nShips ?: Double.MAX_VALUE } ?: return FloatArray(0))
        return FloatArray(length * 2) { i ->
            if (i % 2 == 0) fromIndex / myPlanets.size.toFloat() else toIndex / targetPlanets.size.toFloat()
        }
    }

    private fun evaluateFloatSequence(
        seq: FloatArray,
        observation: Observation,
        myPlanets: List<games.planetwars.core.PlanetObservation>,
        targetPlanets: List<games.planetwars.core.PlanetObservation>,
        length: Int
    ): Double {
        var score = 0.0
        for (i in 0 until length) {
            val fromIndex = (seq[i * 2] * myPlanets.size).toInt().coerceIn(0, myPlanets.lastIndex)
            val toIndex = (seq[i * 2 + 1] * targetPlanets.size).toInt().coerceIn(0, targetPlanets.lastIndex)

            val source = myPlanets[fromIndex]
            val target = targetPlanets[toIndex]

            val sourceShips = source.nShips ?: continue
            val targetShips = target.nShips ?: continue

            if (sourceShips > 5) {
                val potentialGain = if (target.owner == player.opponent()) {
                    sourceShips - targetShips
                } else {
                    sourceShips * 0.3
                }
                score += potentialGain
            }
        }
        return score
    }

    private fun computeVelocity(src: Vec2d, dst: Vec2d): Vec2d {
        val dx = dst.x - src.x
        val dy = dst.y - src.y
        val length = sqrt(dx * dx + dy * dy)
        return Vec2d(dx / length, dy / length)
    }

    override fun getAgentType(): String = "PartialObservationRHEAAgent"
}
