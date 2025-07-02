package games.planetwars.agents.random


import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.max
import kotlin.random.Random

// fullObservationsAgent4
class FullObsSmarterAgent4(
    private val sequenceLength: Int = 200,
    private val nEvals: Int = 60, // More evaluations
    private val mutationRate: Double = 0.4, // Lower mutation for stability
    private val useShiftBuffer: Boolean = true,
    private val eliteCount: Int = 3 // Elitism for solution retention
) : PlanetWarsPlayer() {

    private var bestSolution: FloatArray? = null
    private var bestScore: Double = Double.NEGATIVE_INFINITY

    override fun getAction(gameState: GameState): Action {
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        val targetPlanets = gameState.planets.filter { it.owner != player }

        if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()

        // Greedy move first, but more robust
        val greedyMove = getGreedyMove(myPlanets, targetPlanets)
        if (greedyMove != null) return greedyMove

        // RHEA with population and elitism
        val wrapper = GameStateWrapper(gameState, GameParams(), player)
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
            // Select parent from elite
            val parent = population[Random.nextInt(eliteCount)].seq
            val mutated = mutate(parent)
            val score = wrapper.runForwardModel(mutated)
            if (score > currentBestScore) {
                currentBestScore = score
                currentBest = mutated
            }
            // Insert into population if elite-worthy
            if (score > population.minByOrNull { it.score }!!.score) {
                val worstIdx = population.indexOf(population.minByOrNull { it.score })
                population[worstIdx] = ScoredSeq(mutated, score)
            }
        }

        bestSolution = currentBest
        bestScore = currentBestScore

        return wrapper.getAction(gameState, bestSolution!![0], bestSolution!![1])
    }

    // More robust greedy selection, prioritizes best ratio with more flexible thresholds
    private fun getGreedyMove(myPlanets: List<Planet>, targetPlanets: List<Planet>): Action? {
        var bestScore = Double.NEGATIVE_INFINITY
        var bestAction: Action? = null
        for (source in myPlanets) {
            for (target in targetPlanets) {
                val score = evaluateMove(source, target)
                if (score > bestScore && source.nShips > 8 && target.nShips < 30) {
                    bestScore = score
                    bestAction = Action(player, source.id, target.id, max(1.0, source.nShips / 2.0).toDouble())
                }
            }
        }
        return bestAction
    }

    // Improved move evaluation: includes growth rate, ship ratio, and distance
    private fun evaluateMove(source: Planet, target: Planet): Double {
        val distance = sqrt((source.position.x - target.position.x).pow(2.0) + (source.position.y - target.position.y).pow(2.0))
        val effectiveShips =  max(1.0, source.nShips / 2.0).toDouble() //max(1, target.nShips)
        return (target.growthRate.toDouble() / effectiveShips) / (distance + 1) +
                (source.nShips.toDouble() / (effectiveShips * 1.5))
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

    override fun getAgentType(): String =
        "FulllyObsSmarterAgent4:PopRHEA+Greedy+Elitism"

    data class ScoredSeq(val seq: FloatArray, val score: Double) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ScoredSeq

            if (score != other.score) return false
            if (!seq.contentEquals(other.seq)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = score.hashCode()
            result = 31 * result + seq.contentHashCode()
            return result
        }
    }
}


fun main() {
    val agent = FullObsSmarterAgent4()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}