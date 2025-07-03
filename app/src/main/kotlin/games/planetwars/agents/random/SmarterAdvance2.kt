package games.planetwars.agents.random

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.GameParams
import games.planetwars.core.GameState
import games.planetwars.core.Planet
import games.planetwars.runners.GameRunner
import games.planetwars.core.GameStateFactory
import kotlin.math.min


class SmarterAdvance2 : PlanetWarsPlayer() {

    override fun getAction(gameState: GameState): Action {
        val ownedPlanets = gameState.planets.filter { it.owner == player && it.nShips > 5 }

        if (ownedPlanets.isEmpty()) {
            return Action(player, -1, -1, 0.0)
        }

        val source = ownedPlanets.maxByOrNull { it.nShips + it.growthRate * 5.0 }!!

        val targetPlanets = gameState.planets.filter { it.owner != player && it.id != source.id }

        if (targetPlanets.isEmpty()) {
            return Action(player, -1, -1, source.nShips / 2.0)
        }

        val target = targetPlanets.minByOrNull {

             it.nShips

            /*
            val distance = gameState.distances[source.id][it.id].toDouble()
            val projectedGrowth = it.nShips + it.growthRate * distance
            projectedGrowth

             */
        }!!

      //  val distance = gameState.distances[source.id][target.id].toDouble()

//        val neededShips = target.nShips + target.growthRate * distance + 1

        val neededShips = source.nShips / 2

        val sendShips = min(source.nShips.toDouble(), neededShips)

        return Action(player, source.id, target.id, sendShips)
    }

    override fun getAgentType(): String {
        return "SmarterAdvance2"
    }
}

fun main() {
    val agent1 = SmarterAdvance2()
    val agent2 = games.planetwars.agents.random.CarefulRandomAgent()
    val gameParams = GameParams(numPlanets = 20, maxTicks = 1000)
    val gameRunner = GameRunner(agent1, agent2, gameParams)
    val finalModel = gameRunner.runGame()
    println("Game over! Winner: ${finalModel.getLeader()}")
    println(finalModel.statusString())
}


