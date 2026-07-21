package de.ingrid.ingridapi

import kotlinx.html.*

fun HTML.renderIndexPage(root: String) {
    attributes["data-theme"] = "light"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("InGrid API")
        styleLink("https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
        style {
            unsafe {
                +"""
                :root {
                    --pico-primary: #1565c0;
                }
                body { background-color: #f4f7f9; }
                main { padding-top: 3rem; padding-bottom: 3rem; }
                .api-card {
                    margin-bottom: 2rem;
                    padding: 1.5rem;
                    border-left: 6px solid var(--pico-primary);
                    box-shadow: var(--pico-card-box-shadow);
                }
                .api-card h2 { margin-top: 0; margin-bottom: 0.5rem; }
                .api-links { display: flex; gap: 1rem; margin-top: 1.25rem; }
                .footer { margin-top: 3rem; text-align: center; color: var(--pico-muted-color); }
                """.trimIndent()
            }
        }
    }
    body {
        main(classes = "container") {
            header {
                h1 { +"InGrid API" }
                p { +"Welcome to the InGrid API services. Below you can find the available APIs and their documentation." }
            }

            article(classes = "api-card") {
                h2 { +"Portal API" }
                p { +"This API is used by the InGrid Portal to retrieve data, providing access to catalogs and search functionality." }
                div(classes = "api-links") {
                    a(href = "$root/portal", classes = "button outline") { +"Swagger UI" }
                }
            }

            article(classes = "api-card") {
                h2 { +"OGC API - Records" }
                p { +"OGC API Records endpoints as specified by OGC. Provides discovery and access to metadata records describing geospatial data and services." }
                div(classes = "api-links") {
                    a(href = "$root/ogc/records", classes = "button") { +"Landing Page" }
                    a(href = "$root/ogc/records/swagger", classes = "button outline") { +"Swagger UI" }
                }
            }

            div(classes = "footer") {
                hr {}
                nav {
                    ul {
                        li { a(href = "$root/admin") { +"Administration" } }
                    }
                }
            }
        }
    }
}
