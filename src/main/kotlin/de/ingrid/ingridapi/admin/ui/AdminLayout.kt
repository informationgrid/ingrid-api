package de.ingrid.ingridapi.admin.ui

import kotlinx.html.*

fun HTML.adminLayout(
    title: String,
    root: String,
    activeTab: String? = null,
    content: FlowContent.() -> Unit,
) {
    attributes["data-theme"] = "light"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("InGrid API – $title")
        styleLink("https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
        style {
            unsafe { +AdminComponents.CSS }
        }
    }
    body {
        main(classes = "container") {
            nav {
                ul {
                    li { strong { +"InGrid API – Administration" } }
                }
                ul {
                    li {
                        if (activeTab == "indices") {
                            strong { +"Indizes" }
                        } else {
                            a(href = "$root/admin") { +"Indizes" }
                        }
                    }
                    li {
                        if (activeTab == "meta") {
                            strong { +"Meta" }
                        } else {
                            a(href = "$root/admin/meta") { +"Meta" }
                        }
                    }
                    li {
                        if (activeTab == "search") {
                            strong { +"Suche" }
                        } else {
                            a(href = "$root/admin/search") { +"Suche" }
                        }
                    }
                }
            }
            content()
        }
    }
}
