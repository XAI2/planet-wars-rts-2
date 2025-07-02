package games.planetwars.agents.random

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.GameParams
import games.planetwars.core.GameState
import games.planetwars.runners.GameRunner

class SmarterAgent3 : PlanetWarsPlayer() {


    override fun getAction(gameState: GameState): Action {
        val ownedPlanets = gameState.planets.filter { it.owner == player && it.nShips > 0 }
        if (ownedPlanets.isEmpty()) {
            return Action(player, -1, -1, 0.0)
        }

        // Prioritize planet with the highest growth-to-ship ratio as the source
        val source = ownedPlanets.maxByOrNull { it.growthRate.toDouble() / it.nShips }!!

        val targetPlanets = gameState.planets.filter { it.owner != player && it.id != source.id }
        if (targetPlanets.isEmpty()) {
            return Action(player, -1, -1, 0.0)
        }

        // Select the best target based on low ship count and high growth
        val target = targetPlanets.minByOrNull { it.nShips - it.growthRate }!!

        val numShipsToSend = (source.nShips * 0.65).toInt() // More aggressive: send 65%

        return Action(player, source.id, target.id, numShipsToSend.toDouble())
    }

    override fun getAgentType(): String = "Optimized Smarter Agent 3"
}

// For quick testing
fun main() {
    val agent1 = SmarterAgent3()
    val agent2 = SmarterAgent() // Replace with a better baseline agent
    val gameParams = GameParams(numPlanets = 20, maxTicks = 1000)
    val gameRunner = GameRunner(agent1, agent2, gameParams)
    val finalModel = gameRunner.runGame()

    println("Game over! Winner: ${finalModel.getLeader()}")
    println(finalModel.statusString())
}
