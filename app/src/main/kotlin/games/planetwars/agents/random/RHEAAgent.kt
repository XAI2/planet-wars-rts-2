package games.planetwars.agents.random

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.GameParams
import games.planetwars.core.GameState
import games.planetwars.core.GameStateFactory
import games.planetwars.core.Player
import util.Vec2d
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class RHEAAgent(
    private val sequenceLength: Int = 10,
    private val populationSize: Int = 60,
    private val generations: Int = 50,
    private val mutationRate: Double = 0.2,
    private val eliteCount: Int = 6
) : PlanetWarsPlayer() {

    override fun getAction(gameState: GameState): Action {
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        val targetPlanets = gameState.planets.filter { it.owner != player }

        if (myPlanets.isEmpty() || targetPlanets.isEmpty()) return Action.doNothing()

        val dynamicLength = maxOf(1, min(sequenceLength, 200 - gameState.gameTick))
        val floatLength = dynamicLength * 2

        val population = MutableList(populationSize) {
            if (it == 0) greedyFloatSequence(myPlanets, targetPlanets, dynamicLength)
            else randomFloatSequence(floatLength)
        }

        repeat(generations) {
            val scored = population.map { it to evaluateFloatSequence(it, gameState.deepCopy(), myPlanets, targetPlanets, dynamicLength) }
                .sortedByDescending { it.second }
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
            evaluateFloatSequence(it, gameState.deepCopy(), myPlanets, targetPlanets, dynamicLength)
        } ?: return Action.doNothing()

        val from = (bestSeq[0] * myPlanets.size).toInt().coerceIn(0, myPlanets.lastIndex)
        val to = (bestSeq[1] * targetPlanets.size).toInt().coerceIn(0, targetPlanets.lastIndex)
        val ships = myPlanets[from].nShips * 0.5
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

    private fun greedyFloatSequence(myPlanets: List<games.planetwars.core.Planet>, targetPlanets: List<games.planetwars.core.Planet>, length: Int): FloatArray {
        val fromIndex = myPlanets.indexOf(myPlanets.maxByOrNull { it.nShips } ?: myPlanets.random())
        val toIndex = targetPlanets.indexOf(targetPlanets.minByOrNull { it.nShips } ?: targetPlanets.random())
        return FloatArray(length * 2) { i -> if (i % 2 == 0) fromIndex / myPlanets.size.toFloat() else toIndex / targetPlanets.size.toFloat() }
    }

    private fun evaluateFloatSequence(seq: FloatArray, state: GameState, myPlanets: List<games.planetwars.core.Planet>, targetPlanets: List<games.planetwars.core.Planet>, length: Int): Double {
        var tick = 0
        for (i in 0 until length) {
            val currentMyPlanets = state.planets.filter { it.owner == player && it.transporter == null }
            val currentTargets = state.planets.filter { it.owner != player }

            if (currentMyPlanets.isEmpty() || currentTargets.isEmpty()) continue

            val fromIndex = (seq[i * 2] * currentMyPlanets.size).toInt().coerceIn(0, currentMyPlanets.lastIndex)
            val toIndex = (seq[i * 2 + 1] * currentTargets.size).toInt().coerceIn(0, currentTargets.lastIndex)
            val source = currentMyPlanets[fromIndex]
            val target = currentTargets[toIndex]

            if (source.transporter == null && source.owner == player && source.nShips > 5) {
                val sendShips = source.nShips * 0.5
                source.nShips -= sendShips
                source.transporter = games.planetwars.core.Transporter(
                    s = source.position,
                    v = computeVelocity(source.position, target.position),
                    owner = player,
                    sourceIndex = source.id,
                    destinationIndex = target.id,
                    nShips = sendShips
                )
            }
            state.gameTick++
            updateState(state)
            tick++
        }
        val myScore = state.planets.filter { it.owner == player }.sumOf { it.nShips + it.growthRate * 3 }
        val enemyScore = state.planets.filter { it.owner == player.opponent() }.sumOf { it.nShips + it.growthRate * 2 }
        return myScore - enemyScore
    }

    private fun computeVelocity(src: Vec2d, dst: Vec2d): Vec2d {
        val dx = dst.x - src.x
        val dy = dst.y - src.y
        val length = sqrt(dx * dx + dy * dy)
        return Vec2d(dx / length, dy / length)
    }

    private fun updateState(state: GameState) {
        for (planet in state.planets) {
            if (planet.owner != Player.Neutral) {
                planet.nShips += planet.growthRate
            }
            planet.transporter?.let { t ->
                t.s = t.s + t.v * 1.0
                val dst = state.planets[t.destinationIndex]
                if ((t.s - dst.position).mag() < dst.radius * 1.2) {
                    if (dst.owner == t.owner) {
                        dst.nShips += t.nShips
                    } else {
                        dst.nShips -= t.nShips
                        if (dst.nShips < 0) {
                            dst.owner = t.owner
                            dst.nShips = -dst.nShips
                        }
                    }
                    planet.transporter = null
                }
            }
        }
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
