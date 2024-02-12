package de.ingrid.ingridapi.portal

import de.ingrid.ingridapi.core.services.ElasticsearchService
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configurePortalRouting() {

    val elastic by inject<ElasticsearchService>()

    routing {
        route("portal", { specId = "portal" }) {

            post("search", { request { body<String>() } }) {
                call.respond(elastic.search(call.receiveText())?.hits ?: emptyList())
            }
        }
    }
}