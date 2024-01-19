package de.ingrid.ingridapi.api1

import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.matchPhrasePrefix
import de.ingrid.ingridapi.core.services.ElasticsearchService
import de.ingrid.ingridapi.core.services.TestDocument
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting1() {

    val service by inject<Api1Service>()
    val elastic by inject<ElasticsearchService>()

    routing {
        route("api1", { specId = "api1" }) {

            get("test") {
                call.respondText("Test von API 1 :-) !!")
            }

            get("json") {
                call.respond(service.getList())
            }

            get("search") {
                val q: SearchDSL = SearchDSL().apply {
                    query = bool {
                        must(
                            // note how we can use property references here
                            matchPhrasePrefix(TestDocument::title, "add")
                        )
                    }
                }
                call.respond(elastic.search(q)?.hits ?: emptyList())
            }
        }
    }
}