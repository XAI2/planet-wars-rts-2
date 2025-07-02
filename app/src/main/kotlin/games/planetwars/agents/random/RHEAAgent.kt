package games.planetwars.agents.random

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.hybrid.GameStateWrapper
import games.planetwars.core.*
import kotlin.random.Random

class RHEAAgent(
    private val sequenceLength: Int = 200,
    private val populationSize: Int = 60,
    private val generations: Int = 40,
    private val mutationRate: Double = 0.3,
    private val eliteCount: Int = 5,
    private val useShiftBuffer: Boolean = true
) : PlanetWarsPlayer() {

    private var bestSolution: FloatArray? = null

    override fun getAction(gameState: GameState): Action {
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        val targetPlanets = gameState.planets.filter { it.owner != player }

        if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()

        val floatLength = sequenceLength * 2

        if (bestSolution == null || !useShiftBuffer) {
            bestSolution = greedyFloatSequence(myPlanets, targetPlanets, sequenceLength)
        } else {
            bestSolution = shiftAndMutate(bestSolution!!)
        }

        var bestScore = evaluateFloatSequence(bestSolution!!, gameState.deepCopy())

        repeat(generations) {
            val population = List(populationSize) { mutateFloatSequence(bestSolution!!) }
            val evaluated = population.map { it to evaluateFloatSequence(it, gameState.deepCopy()) }
                .sortedByDescending { it.second }
            val elites = evaluated.take(eliteCount).map { it.first.copyOf() }
            bestSolution = elites.first()
            bestScore = evaluated.first().second
        }

        val fromIndex = (bestSolution!![0] * myPlanets.size).toInt().coerceIn(0, myPlanets.lastIndex)
        val toIndex = (bestSolution!![1] * targetPlanets.size).toInt().coerceIn(0, targetPlanets.lastIndex)
        val ships = myPlanets[fromIndex].nShips * 0.5
        return Action(player, myPlanets[fromIndex].id, targetPlanets[toIndex].id, ships)
    }

    private fun randomFloatSequence(length: Int): FloatArray = FloatArray(length) { Random.nextFloat() }

    private fun mutateFloatSequence(seq: FloatArray): FloatArray = FloatArray(seq.size) { i ->
        if (Random.nextDouble() < mutationRate) Random.nextFloat() else seq[i]
    }

    private fun shiftAndMutate(seq: FloatArray): FloatArray {
        val shifted = FloatArray(seq.size)
        for (i in 0 until seq.size - 2) {
            shifted[i] = seq[i + 2]
        }
        shifted[seq.size - 2] = Random.nextFloat()
        shifted[seq.size - 1] = Random.nextFloat()
        return shifted
    }

    private fun greedyFloatSequence(myPlanets: List<Planet>, targetPlanets: List<Planet>, length: Int): FloatArray {
        val fromIndex = myPlanets.indexOf(myPlanets.maxByOrNull { it.nShips } ?: myPlanets.random())
        val toIndex = targetPlanets.indexOf(targetPlanets.minByOrNull { it.nShips } ?: targetPlanets.random())
        return FloatArray(length * 2) { i -> if (i % 2 == 0) fromIndex / myPlanets.size.toFloat() else toIndex / targetPlanets.size.toFloat() }
    }

    private fun evaluateFloatSequence(seq: FloatArray, state: GameState): Double {
        val wrapper = GameStateWrapper(state, GameParams(), player)
        return wrapper.runForwardModel(seq)
    }

    override fun getAgentType(): String = "RHEA Agent"
}

fun main() {
    val agent = RHEAAgent()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}
