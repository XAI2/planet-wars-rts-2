package games.planetwars.agents.random


import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.random.Random
import kotlin.math.*
import kotlin.math.max

import kotlin.system.*

/*
data class GameStateWrapper(
    val gameState: GameState,
    val params: GameParams,
    val player: Player,
    val opponentModel: PlanetWarsAgent = DoNothingAgent(),
) {
    var forwardModel = ForwardModel(gameState, params)

    companion object {
        const val shiftBy = 2
    }

    fun getAction(gameState: GameState, from: Float, to: Float): Action {
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        if (myPlanets.isEmpty()) return Action.doNothing()
        val otherPlanets = gameState.planets.filter { it.owner == player.opponent() || it.owner == Player.Neutral }
        if (otherPlanets.isEmpty()) return Action.doNothing()
        val source = myPlanets[min((from * myPlanets.size).toInt(), myPlanets.lastIndex)]
        val target = otherPlanets[min((to * otherPlanets.size).toInt(), otherPlanets.lastIndex)]
      //  return Action(player, source.id, target.id, max(1, source.nShips / 2))
//        return Action(player, source.id, target.id, max(1.0, source.nShips / 2.0).toInt())
        return Action(player, source.id, target.id, max(1.0, source.nShips / 2.0).toDouble())

    }

    fun runForwardModel(seq: FloatArray): Double {
        var ix = 0
        forwardModel = ForwardModel(gameState.deepCopy(), params)
        while (ix < seq.size && !forwardModel.isTerminal()) {
            val from = seq[ix]
            val to = seq[ix + 1]
            val myAction = getAction(forwardModel.state, from, to)
            val opponentAction = opponentModel.getAction(forwardModel.state)
            val actions = mapOf(player to myAction, player.opponent() to opponentAction)
            forwardModel.step(actions)
            ix += shiftBy
        }
        return scoreDifference()
    }


    fun scoreDifference(): Double {
        return forwardModel.getShips(player) - forwardModel.getShips(player.opponent())
    }
}


data class RHEAAgent3(
    var sequenceLength: Int = 200,
    var totalEvals: Int = 50,
    var useShiftBuffer: Boolean = true,
    var timeLimitMillis: Long = 30,
    var opponentModel: PlanetWarsAgent = DoNothingAgent(),
    var eliteCount: Int = 4,
    var adaptiveMutation: Boolean = true,
    var minMutation: Double = 0.05,
    var maxMutation: Double = 0.8,
    var stagnationPatience: Int = 4
) : PlanetWarsPlayer() {

    private var random = Random
    private var bestSolution: ScoredSolution? = null
    private var bestScore: Double = Double.NEGATIVE_INFINITY
    private var stagnation = 0
    private var lastImprovementEval = 0
    private var evalCount = 0

    data class ScoredSolution(val score: Double, val solution: FloatArray)
    data class PopulationMember(val score: Double, val seq: FloatArray)

    override fun getAgentType(): String {
        return "RHEAAgent3-$sequenceLength-$totalEvals-$eliteCount"
    }

    override fun getAction(gameState: GameState): Action {
        val wrapper = GameStateWrapper(gameState, params, player, opponentModel)

        // Initial solution
        if (bestSolution == null || !useShiftBuffer) {
            val solution = randomPoint(sequenceLength)
            bestSolution = ScoredSolution(evalSeq(gameState, solution), solution)
            bestScore = bestSolution!!.score
            stagnation = 0
            lastImprovementEval = 0
        } else {
            val nextSeq = shiftLeftAndRandomAppend(bestSolution!!.solution, GameStateWrapper.shiftBy)
            bestSolution = ScoredSolution(evalSeq(gameState, nextSeq), nextSeq)
        }

        var population = mutableListOf<ScoredSolution>()
        population.add(bestSolution!!)
        for (i in 1 until eliteCount) {
            val sol = randomPoint(sequenceLength)
            population.add(ScoredSolution(evalSeq(gameState, sol), sol))
        }

        evalCount = 0
        var mutationProb = maxMutation

        // Evolutionary loop
        val tStart = getTimeMillis()
        while (evalCount < totalEvals && (getTimeMillis() - tStart) < timeLimitMillis) {
            // Adaptive mutation: lower if stagnating, increase if many improvements
            if (adaptiveMutation) {
                mutationProb = if (stagnation > stagnationPatience)
                    min(mutationProb * 1.1, maxMutation)
                else
                    max(mutationProb * 0.95, minMutation)
            }

            // Elitism: keep top eliteCount
            population = population.sortedByDescending { it.score }.take(eliteCount).toMutableList()

            // Generate offspring
            val newPopulation = mutableListOf<ScoredSolution>()
            newPopulation.addAll(population)
            while (newPopulation.size < totalEvals) {
                val parent = population[random.nextInt(population.size)]
                val childSeq = mutate(parent.solution, mutationProb)
                val childScore = evalSeq(gameState, childSeq)
                newPopulation.add(ScoredSolution(childScore, childSeq))
                evalCount++
                if (childScore > bestScore + 1e-6) {
                    bestScore = childScore
                    bestSolution = ScoredSolution(childScore, childSeq)
                    stagnation = 0
                    lastImprovementEval = evalCount
                }
            }

            // Stagnation tracking
            if (lastImprovementEval == evalCount) stagnation = 0 else stagnation++
            population = newPopulation
        }
        val best = bestSolution ?: population.maxByOrNull { it.score } ?: population[0]
        return wrapper.getAction(gameState, best.solution[0], best.solution[1])
    }

    private fun mutate(v: FloatArray, mutProb: Double): FloatArray {
        val n = v.size
        val x = FloatArray(n)
        // At least one mutation per child
        val mustMutate = random.nextInt(n)
        for (i in 0 until n) {
            x[i] = if (i == mustMutate || random.nextDouble() < mutProb) random.nextFloat() else v[i]
        }
        return x
    }

    private fun randomPoint(dim: Int): FloatArray = FloatArray(dim) { random.nextFloat() }

    private fun shiftLeftAndRandomAppend(v: FloatArray, shiftBy: Int): FloatArray {
        val p = FloatArray(v.size)
        for (i in 0 until p.size - shiftBy) {
            p[i] = v[i + shiftBy]
        }
        // Refill last shifted values with new randoms
        for (i in 1..shiftBy) {
            p[p.size - i] = random.nextFloat()
        }
        return p
    }

    private fun evalSeq(state: GameState, seq: FloatArray): Double {
        val wrapper = GameStateWrapper(state.deepCopy(), params, player, opponentModel)
        return wrapper.runForwardModel(seq)
    }

    private fun getTimeMillis(): Long = System.currentTimeMillis()
}

fun main() {
    val gameParams = GameParams(numPlanets = 10)
    val gameState = GameStateFactory(gameParams).createGame()
    val agent = RHEAAgent3()
    agent.prepareToPlayAs(Player.Player1, gameParams)
    println(agent.getAgentType())
    val action = agent.getAction(gameState)
    println(action)
}

 */