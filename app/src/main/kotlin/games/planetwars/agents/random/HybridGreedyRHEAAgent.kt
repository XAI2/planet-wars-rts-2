package games.planetwars.agents.hybrid

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.sqrt

class HybridGreedyRHEAAgent : PlanetWarsPlayer() {

    override fun getAction(gameState: GameState): Action {
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        val targetPlanets = gameState.planets.filter {
            it.owner != player
        }

        if (myPlanets.isEmpty() || targetPlanets.isEmpty()) {
            return Action.doNothing()
        }

        // Check if any obvious greedy move exists (e.g., weak nearby target)
        val greedyMove = getGreedyMove(myPlanets, targetPlanets)
        if (greedyMove != null) {
            return greedyMove
        }

        // Fall back to RHEA planning
        return getRHEAMove(gameState)
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
        val distance = euclideanDistance(source.position.x, source.position.y, target.position.x, target.position.y)
        return (target.growthRate.toDouble() / (target.nShips + 1)) / (distance + 1)
    }

    private fun euclideanDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

    private fun getRHEAMove(gameState: GameState): Action {
        // Simplified fallback: pick random valid action as a placeholder for RHEA logic
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        val otherPlanets = gameState.planets.filter { it.owner != player }

        if (myPlanets.isNotEmpty() && otherPlanets.isNotEmpty()) {
            val source = myPlanets.random()
            val target = otherPlanets.random()
            return Action(player, source.id, target.id, source.nShips / 2)
        }
        return Action.doNothing()
    }

    override fun getAgentType(): String {
        return "Hybrid Greedy + RHEA Agent"
    }
}

fun main() {
    val agent = HybridGreedyRHEAAgent()
    agent.prepareToPlayAs(Player.Player1, GameParams())
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println(action)
}
