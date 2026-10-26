package io.github.Earth1283.aptMc.graph

import net.kyori.adventure.text.Component

data class GraphMessage(val key: String, val arguments: Map<String, String> = emptyMap())

class GraphException(val detail: GraphMessage) : IllegalArgumentException(detail.key)

fun interface GraphMessages {
    fun render(key: String, arguments: Map<String, Component>): Component
}

fun GraphMessages.text(key: String, vararg arguments: Pair<String, String>): Component =
    render(key, arguments.associate { it.first to Component.text(it.second) })

fun GraphMessages.text(message: GraphMessage): Component =
    render(message.key, message.arguments.mapValues { Component.text(it.value) })
