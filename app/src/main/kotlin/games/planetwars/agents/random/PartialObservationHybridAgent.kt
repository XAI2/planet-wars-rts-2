package games.planetwars.agents.random

import games.planetwars.agents.Action
import games.planetwars.agents.PartialObservationPlayer
import games.planetwars.core.Observation

class PartialObservationHybridAgent : PartialObservationPlayer() {

    override fun getAction(observation: Observation): Action {
        // Step 1: Greedy part
        val myPlanets = observation.observedPlanets.filter { it.owner == player && it.transporter == null }
        val enemyPlanets = observation.observedPlanets.filter { it.owner != player }

        if (myPlanets.isEmpty() || enemyPlanets.isEmpty()) {
            return Action.doNothing()
        }

        var bestScore = Double.NEGATIVE_INFINITY
        var bestAction: Action = Action.doNothing()

        for (source in myPlanets) {
            for (target in enemyPlanets) {
                val score = evaluateMove(source.nShips ?: 0.0, target.nShips ?: 0.0, target.growthRate.toInt())
                if (score > bestScore && (source.nShips ?: 0.0) > 10) {
                    bestScore = score
                    bestAction = Action(player, source.id, target.id, (source.nShips ?: 0.0) / 2)
                }
            }
        }

        if (bestScore > 0) {
            return bestAction
        }

        // Step 2: Fallback - simple RHEA logic
        val randomSource = myPlanets.random()
        val randomTarget = enemyPlanets.random()

        val ships = randomSource.nShips ?: return Action.doNothing()
        return Action(player, randomSource.id, randomTarget.id, ships / 2)
    }

    private fun evaluateMove(sourceShips: Double, targetShips: Double, growthRate: Int): Double {
        // Simple heuristic: prefer low target ships and high growth
        return (growthRate.toDouble() / (targetShips + 1)) * (sourceShips / 100)
    }

    override fun getAgentType(): String {
        return "Partial Hybrid Greedy + RHEA"
    }
}
