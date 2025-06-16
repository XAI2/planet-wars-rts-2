package json_rmi

import games.planetwars.agents.Action
import games.planetwars.core.GameParams
import games.planetwars.core.GameState
import games.planetwars.core.GameStateFactory
import games.planetwars.core.Player
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.PolymorphicSerializer
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking


val json = Json {
    serializersModule = customSerializersModule
    classDiscriminator = "type"
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun encodeArgument(arg: Any?): JsonElement = when (arg) {
    is RemoteConstructable -> json.encodeToJsonElement(PolymorphicSerializer(RemoteConstructable::class), arg)
    is Player -> json.encodeToJsonElement(Player.serializer(), arg)
    is String -> JsonPrimitive(arg)
    is Int -> JsonPrimitive(arg)
    is Double -> JsonPrimitive(arg)
    is Boolean -> JsonPrimitive(arg)
    else -> error("Unsupported argument type: \${arg::class}")
}

suspend fun DefaultClientWebSocketSession.invokeRemoteMethod(
    objectId: String,
    method: String,
    args: List<Any?> = emptyList(),
    logger: JsonLogger = JsonLogger(ignore = true),
): String {
    val jsonArgs: List<JsonElement> = args.map(transform = ::encodeArgument)
    val request = RemoteInvocationRequest(
        requestType = RpcConstants.TYPE_INVOKE,
        target = RpcConstants.TARGET_AGENT,
        method = method,
        objectId = objectId,
        args = jsonArgs
    )
    val jsonRequest = json.encodeToString(RemoteInvocationRequest.serializer(), request)
    logger.logSend(jsonRequest)
    send(jsonRequest)

    val response = (incoming.receive() as Frame.Text).readText()
    logger.logRecv(response)
    return response
}

//suspend fun DefaultClientWebSocketSession.invokeRemoteMethod(
//    objectId: String,
//    method: String,
//    vararg args: Any,
//    logger: JsonLogger = JsonLogger(ignore = true) // default: ignore = true,
//): String {
//    val jsonArgs: List<JsonElement> = args.map { arg -> encodeArgument(arg) }
//    val request = RemoteInvocationRequest(
//        requestType = RpcConstants.TYPE_INVOKE,
//        target = RpcConstants.TARGET_AGENT,
//        method = method,
//        objectId = objectId,
//        args = jsonArgs
//    )
//    send(json.encodeToString(RemoteInvocationRequest.serializer(), request))
//    return (incoming.receive() as Frame.Text).readText()
//}

suspend fun DefaultClientWebSocketSession.initAgent(className: String): String {
    val request = RemoteInvocationRequest(
        requestType = RpcConstants.TYPE_INIT,
        target = RpcConstants.TARGET_AGENT,
        className = className,
        method = "<ignored>"
    )
    send(json.encodeToString(RemoteInvocationRequest.serializer(), request))
    val initResponse = (incoming.receive() as Frame.Text).readText()
    println("INIT Response: $initResponse")
    return json.parseToJsonElement(initResponse)
        .jsonObject["result"]
        ?.jsonObject?.get("objectId")
        ?.jsonPrimitive?.content
        ?: error("Missing objectId")
}

suspend fun DefaultClientWebSocketSession.endAgent(objectId: String) {
    val request = RemoteInvocationRequest(
        requestType = RpcConstants.TYPE_END,
        target = RpcConstants.TARGET_AGENT,
        method = "",
        objectId = objectId
    )
    send(json.encodeToString(RemoteInvocationRequest.serializer(), request))
    val response = (incoming.receive() as Frame.Text).readText()
    println("END Response: $response")
}

fun main() = runBlocking {
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    val className = "games.planetwars.agents.random.CarefulRandomAgent"
    client.webSocket("ws://localhost:8080/ws") {
        val objectId = initAgent(className)

        val params = GameParams(numPlanets = 10, initialNeutralRatio = 0.0)

        val prepareResponse = invokeRemoteMethod(
            objectId=objectId,
            method="prepareToPlayAs",
            args = listOf(Player.Player1, params, "DummyOpponent", null),
        )
        println("prepareToPlayAs Response: $prepareResponse")

        val gameState = GameStateFactory(params).createGame()
        val actionResponse = invokeRemoteMethod(
            objectId,
            "getAction",
            args = listOf(gameState),
        )
        println("getAction Response: $actionResponse")

        // also check what type the response is
        val jsonResp = json.parseToJsonElement(actionResponse).jsonObject
        val result = jsonResp["result"]
        if (result != null && result is JsonObject) {
            val action = json.decodeFromJsonElement(Action.serializer(), result)
            println("Decoded Action: $action")
        }

        endAgent(objectId)
    }

    client.close()
}
