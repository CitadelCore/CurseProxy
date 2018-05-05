package moe.nikky.curseproxy

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.gson.gson
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.html.*
import moe.nikky.curseproxy.Widget.widget
import moe.nikky.curseproxy.exceptions.*
import moe.nikky.encodeBase64
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File


val LOG: Logger = LoggerFactory.getLogger("curseproxy")

const val REST_ENDPOINT = "/api"

fun Application.main() {

    install(DefaultHeaders)
    install(CallLogging)
    //TODO: enable in production
//    install(HttpsRedirect)
//    install(HSTS)
//    install(CORS) {
//        maxAge = Duration.ofDays(1)
//    }
//    install(Metrics) {
//        val reporter = Slf4jReporter.forRegistry(registry)
//                .outputTo(log)
//                .convertRatesTo(TimeUnit.SECONDS)
//                .convertDurationsTo(TimeUnit.MILLISECONDS)
//                .build()
//        reporter.start(10, TimeUnit.SECONDS)
//    }
    install(ContentNegotiation) {
        //        jackson {
//            configure(SerializationFeature.INDENT_OUTPUT, true)
//        }

        gson {
            //            setup()
            setPrettyPrinting()
            disableHtmlEscaping()
        }
    }

    routing {
        get("/api/ids") {
            LOG.debug("Get all AddOns")
            val ids = IDCache.get()
            LOG.info("ids count: ${ids.count()}")

            call.respond(ids)
        }

        get("/api/ids/{id}") {
            val id = call.parameters["id"]?.toInt()
                    ?: throw NumberFormatException("id")
            LOG.debug("Get AddOn with id=$id")
            with(IDCache.get(id)) {
                call.respond(this)
            }
        }

        get("/api/widget/{id}") {
            val id = call.parameters["id"]?.toInt()
                    ?: throw NumberFormatException("id")
            val versions = call.parameters.getAll("version") ?: emptyList()
            call.respondHtml {
                widget(id, versions.toMutableList())
            }
        }

        get("/api/widget.css") {
            call.respondFile(File(Widget::class.java.getResource("/css/widget.css").file))
        }

        get("/api/url/{id}") {
            val id = call.parameters["id"]?.toInt()
                    ?: throw NumberFormatException("id")
            val addon = CurseUtil.getAddon(id) ?: throw AddonNotFoundException(id)
            val versions = call.parameters.getAll("version") ?: emptyList()
            val file = addon.latestFile(versions)
            call.respondRedirect(url = file.downloadURL, permanent = false)
        }

        get("/api/url/{id}/{fileid}") {
            val id = call.parameters["id"]?.toInt()
                    ?: throw NumberFormatException("id")
            val fileid = call.parameters["fileid"]?.toInt()
                    ?: throw NumberFormatException("fileid")
            val file = CurseUtil.getAddonFile(id, fileid) ?: throw AddonFileNotFoundException(id, fileid)
            call.respondRedirect(url = file.downloadURL, permanent = false)
        }

        get("/api/img/{id}") {
            val id = call.parameters["id"]?.toInt()
                    ?: throw NumberFormatException("id")
            val style = call.parameters["style"]
            val link = call.parameters.contains("link")
            val logo = call.parameters.contains("logo")

            val addon = CurseUtil.getAddon(id) ?: throw AddonNotFoundException(id)
            val versions = call.parameters.getAll("version") ?: emptyList()
            val file = addon.latestFile(versions)

            val name = addon.name
                    .replace("-", "--")
                    .replace("_", "__")
            val fileName = file.fileName.replace(addon.name, "")
                    .replace(Regex("^[\\s-._]+"), "")
                    .replace("-", "--")
                    .replace("_", "__")

            var url = "https://img.shields.io/badge/$name-$fileName-orange.svg?maxAge=3600"
            if (logo) {
                val logoData = "data:image/png;base64," + File(Widget::class.java.getResource("/anvil.png").file).encodeBase64()
                url += "&logo=$logoData"
            }
            if (link) {
                val left = "https://minecraft.curseforge.com/projects/$id"
                val right = file.downloadURL
                url += "&link=$left&link=$right"
            }
            if (style != null) {
                url += "&style=$style"
            }

            call.respondRedirect(url = url, permanent = false)
        }

        get("/api/img/{id}/files") {
            val id = call.parameters["id"]?.toInt()
                    ?: throw NumberFormatException("id")
            val style = call.parameters["style"]
            val link = call.parameters.contains("link")
            val logo = call.parameters.contains("logo")

            val addon = CurseUtil.getAddon(id) ?: throw AddonNotFoundException(id)
            val versions = call.parameters.getAll("version") ?: emptyList()
            val files = addon.files(versions)
            val count = files.count()

            val name = addon.name
                    .replace("-", "--")
                    .replace("_", "__")
            val label = "$count Files"
            var url = "https://img.shields.io/badge/$name-$label-orange.svg?maxAge=3600"
            if (logo) {
                val logoData = "data:image/png;base64," + File(Widget::class.java.getResource("/anvil.png").file).encodeBase64()
                url += "&logo=$logoData"
            }
            if (link) {
                val left = "https://minecraft.curseforge.com/projects/$id"
                val right = "https://minecraft.curseforge.com/projects/$id/files"
                url += "&link=$left&link=$right"
            }
            if (style != null) {
                url += "&style=$style"
            }

            call.respondRedirect(url = url, permanent = false)
        }

        get("/api/img/{id}/{fileid}") {
            val id = call.parameters["id"]?.toInt()
                    ?: throw NumberFormatException("id")
            val fileid = call.parameters["fileid"]?.toInt()
                    ?: throw NumberFormatException("fileid")
            val style = call.parameters["style"]
            val link = call.parameters.contains("link")
            val logo = call.parameters.contains("logo")

            val addon = CurseUtil.getAddon(id) ?: throw AddonNotFoundException(id)
            val file = CurseUtil.getAddonFile(id, fileid) ?: throw AddonFileNotFoundException(id, fileid)

            val name = addon.name
                    .replace("-", "--")
                    .replace("_", "__")
            val fileName = file.fileName.replace(addon.name, "")
                    .replace(Regex("^[\\s-._]+"), "")
                    .replace("-", "--")
                    .replace("_", "__")

            var url = "https://img.shields.io/badge/$name-$fileName-orange.svg?maxAge=3600"
            if (logo) {
                val logoData = "data:image/png;base64," + File(Widget::class.java.getResource("/anvil.png").file).encodeBase64()
                url += "&logo=$logoData"
            }
            if (link) {
                val left = "https://minecraft.curseforge.com/projects/$id"
                val right = file.downloadURL
                url += "&link=$left&link=$right"
            }
            if (style != null) {
                url += "&style=$style"
            }

            call.respondRedirect(url = url, permanent = false)
        }


        get("/api/demo/{id}") {
            val id = call.parameters["id"]?.toInt()
                    ?: throw NumberFormatException("id")
            call.respondHtml {
                body {
                    a(href = "/api/url/$id") {
                        img(src = "/api/img/$id")
                    }
                    br {}
                    img(src = "/api/img/$id?link")

                }
            }
        }

        get("/") {
            call.respondHtml {
                head {
                    title("CurseProxy API")
                }
                body {
                    h1 { +"CurseProxy API" }
                    p {
                        +"How are you doing?"
                    }
                    a(href = "https://github.com/NikkyAI/CurseProxy/blob/master/README.md") { +"get started here" }
                }
            }
        }
        get("/debug/") {
            val scheme = call.request.header("X-Forwarded-Proto") ?: call.request.local.scheme
            val host = call.request.header("Host") ?: "${call.request.local.host}:${call.request.local.port}"
            call.respondHtml {
                head {
                    title("CurseProxy API")
                }
                body {
                    h1 { +"CurseProxy API debug" }
                    p {
                        +"Hello World"
                    }
                    p {
                        +"scheme = $scheme"
                    }
                    p {
                        +"host = $host"
                    }
                    h2 { +"call.request.local" }
                    listOf(
                            "scheme = ${call.request.local.scheme}",
                            "version = ${call.request.local.version}",
                            "port = ${call.request.local.port}",
                            "host = ${call.request.local.host}",
                            "uri = ${call.request.local.uri}",
                            "method = ${call.request.local.method}"
                    ).forEach { p { +it } }

                    h2 { +"Headers" }
                    call.request.headers.entries().forEach { (key, value) ->
                        p {
                            +"$key = $value"
                        }
                    }
                }
            }
        }
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(
                    HttpStatusCode.InternalServerError,
                    StackTraceMessage(cause)
            )
        }
        exception<AddonNotFoundException> { cause ->
            call.respond(
                    HttpStatusCode.NotFound,
                    cause
            )
        }
        exception<AddonFileNotFoundException> { cause ->
            call.respond(
                    HttpStatusCode.NotFound,
                    cause
            )
        }
        exception<MissingParameterException> { cause ->
            call.respond(
                    HttpStatusCode.NotAcceptable,
                    cause
            )
        }
        exception<MessageException> { cause ->
            call.respond(
                    HttpStatusCode.NotAcceptable,
                    cause
            )
        }
        exception<IllegalArgumentException> { cause ->
            call.respond(
                    HttpStatusCode.NotAcceptable,
                    StackTraceMessage(cause)
            )
        }
        exception<NumberFormatException> { cause ->
            call.respond(
                    HttpStatusCode.NotAcceptable,
                    StackTraceMessage(cause)
            )
        }
    }

    LOG.info("loading IDs")
    val idMap = IDCache.get()
    LOG.info("loaded ${idMap.size} IDs")
//    LOG.info("loaded addon test complete")
}
