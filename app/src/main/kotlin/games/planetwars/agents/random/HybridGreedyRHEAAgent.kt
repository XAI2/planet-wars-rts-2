package games.planetwars.agents.hybrid

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.math.pow

class HybridGreedyRHEAAgent(
    private val sequenceLength: Int = 200,
    private val nEvals: Int = 30,
    private val mutationRate: Double = 0.5,
    private val useShiftBuffer: Boolean = true
) : PlanetWarsPlayer() {

    private var bestSolution: FloatArray? = null

    override fun getAction(gameState: GameState): Action {
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        val targetPlanets = gameState.planets.filter { it.owner != player }

        if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()

        // Try greedy move first
        val greedyMove = getGreedyMove(myPlanets, targetPlanets)
        if (greedyMove != null) return greedyMove

        // Fallback to forward-model based RHEA move
        val wrapper = GameStateWrapper(gameState, GameParams(), player)

        if (bestSolution == null || !useShiftBuffer) {
            bestSolution = randomSequence()
        } else {
            bestSolution = shiftAndMutate(bestSolution!!)
        }

        var bestScore = wrapper.runForwardModel(bestSolution!!)

        repeat(nEvals) {
            val mutated = mutate(bestSolution!!)
            val score = wrapper.runForwardModel(mutated)
            if (score > bestScore) {
                bestScore = score
                bestSolution = mutated
            }
        }

        return wrapper.getAction(gameState, bestSolution!![0], bestSolution!![1])
    }

    private fun getGreedyMove(myPlanets: List<Planet>, targetPlanets: List<Planet>): Action? {
        var bestScore = Double.NEGATIVE_INFINITY
        var bestAction: Action? = null
        for (source in myPlanets) {
            for (target in targetPlanets) {
                val score = evaluateMove(source, target)
                if (score > bestScore && source.nShips > 10 && target.nShips < 20) {
                    bestScore = score
                    bestAction = Action(player, source.id, target.id, source.nShips / 2)
                }
            }
        }
        return bestAction
    }

    private fun evaluateMove(source: Planet, target: Planet): Double {
        val distance = sqrt((source.position.x - target.position.x).pow(2.0) + (source.position.y - target.position.y).pow(2.0))
        return (target.growthRate.toDouble() / (target.nShips + 1)) / (distance + 1)
    }

    private fun randomSequence(): FloatArray {
        return FloatArray(sequenceLength) { Random.nextFloat() }
    }

    private fun mutate(seq: FloatArray): FloatArray {
        return FloatArray(seq.size) { i ->
            if (Random.nextDouble() < mutationRate) Random.nextFloat() else seq[i]
        }
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

    override fun getAgentType(): String {
        return "Hybrid Greedy + Forward-RHEA Agent"
    }
}

class GameStateWrapper(
    val gameState: GameState,
    val params: GameParams,
    val player: Player
) {
    var forwardModel = ForwardModel(gameState, params)

    fun getAction(state: GameState, from: Float, to: Float): Action {
        val myPlanets = state.planets.filter { it.owner == player && it.transporter == null }
        val targetPlanets = state.planets.filter { it.owner != player }
        if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()
        val source = myPlanets[(from * myPlanets.size).toInt().coerceIn(0, myPlanets.lastIndex)]
        val target = targetPlanets[(to * targetPlanets.size).toInt().coerceIn(0, targetPlanets.lastIndex)]
        return Action(player, source.id, target.id, source.nShips / 2)
    }

    fun runForwardModel(seq: FloatArray): Double {
        forwardModel = ForwardModel(gameState.deepCopy(), params)
        var i = 0
        while (i + 1 < seq.size && !forwardModel.isTerminal()) {
            val action = getAction(gameState, seq[i], seq[i + 1])
            val opponentAction = Action.doNothing()
            forwardModel.step(mapOf(player to action, player.opponent() to opponentAction))
            i += 2
        }
        return forwardModel.getShips(player) - forwardModel.getShips(player.opponent())
    }
}

fun main() {
    val agent = HybridGreedyRHEAAgent()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}
