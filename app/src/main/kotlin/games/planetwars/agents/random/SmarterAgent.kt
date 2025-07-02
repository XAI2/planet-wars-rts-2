package games.planetwars.agents.random


// package games.planetwars.agents.smart

import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.GameParams
import games.planetwars.core.GameState
import games.planetwars.core.GameStateFactory
import games.planetwars.runners.GameRunner

// private val SmarterAgent.source: Any

class SmarterAgent : PlanetWarsPlayer() {

    override fun getAction(gameState: GameState): Action {
        // Filter for source planets owned by the agent
        val ownedPlanets = gameState.planets.filter { it.owner == player && it.nShips > 0 }

        if (ownedPlanets.isEmpty()) {
            // No action possible, fallback to some default behavior
            // For example, do nothing or select any planet
            val fallbackPlanet = gameState.planets.firstOrNull { it.nShips > 0 } // or any suitable fallback
            if (fallbackPlanet != null) {
                // Send half ships from fallback planet if possible
                return Action(player, fallbackPlanet.id, -1, fallbackPlanet.nShips / 2)
            } else {
                // No planets to act from, do nothing or return a default Action
                return Action(player, -1, -1, 0.toDouble() )
            }
        }

        // Select the strongest planet as the source (most ships)
        val source = ownedPlanets.maxByOrNull { it.nShips }!!

        // Filter target planets not owned by us
        val targetPlanets = gameState.planets.filter { it.owner != player && it.id != source.id }

        if (targetPlanets.isEmpty()) {
            // No valid target, fallback
            return Action(player, -1, -1, source.nShips/2) // added source.nShips/2
        }

        // Choose the weakest target (least ships)
        val target = targetPlanets.minByOrNull { it.nShips }!!

        val numShipsToSend = source.nShips / 2

        return Action(player, source.id, target.id, numShipsToSend)
    }

    override fun getAgentType(): String {
        return "Smarter Agent"
    }
}

// Test run
fun main() {
    val agent = SmarterAgent()
    val gameState = GameStateFactory(GameParams()).createGame()
    val action = agent.getAction(gameState)
    println("Agent Type: ${agent.getAgentType()}")
    println("Action: $action")

    val agent1 = SmarterAgent()
    val agent2 = CarefulRandomAgent()
    val gameParams = GameParams(numPlanets = 20, maxTicks = 1000) // Provide actual parameters here
    val gameRunner = GameRunner(agent1, agent2, gameParams)
    val finalModel = gameRunner.runGame()
    println("Game over! Winner: ${finalModel.getLeader()}")
    println(finalModel.statusString())
}


/*

Summary
Purpose:
This code defines a "Smarter Agent" for the PlanetWars game, which is an AI player designed to make more strategic decisions than a purely random agent.

Main Logic:

Source Selection: The agent picks the strongest planet it owns (with the most ships) as the source for an attack.

Target Selection: It targets the weakest planet not owned by the agent (with the fewest ships).

Action: The agent sends half the ships from the strongest source planet to the weakest target planet.

Fallback Logic: If there are no available source or target planets, it defaults to sending half the ships from a fallback planet or doing nothing.

Algorithm Used
Algorithm Type:
Heuristic-based (not a classic algorithm like A, Dijkstra, or Genetic Algorithm)*

Heuristic: "Strongest-to-weakest" strategy.

Selects the strongest owned planet as the source.

Selects the weakest enemy planet as the target.

Sends half the source's ships to the target.

Key Features:

Determines the best available move based on ship count.

Does not use randomness for decision-making.

Uses a simple, rule-based strategy (not learning or optimization-based).

Bullet Points for Slides
Agent Type: Smarter (heuristic-based) agent for PlanetWars

Source Selection: Strongest owned planet (most ships)

Target Selection: Weakest enemy planet (least ships)

Action: Sends half of source’s ships to target

Fallback: Handles edge cases with simple fallback logic

Algorithm: Heuristic ("strongest-to-weakest"), not classic search or learning algorithm

In short:
This agent uses a simple, deterministic heuristic to select the strongest owned planet as a source and the weakest enemy planet as a target, sending half the source’s ships in each move, with basic fallback logic for edge cases.


 */