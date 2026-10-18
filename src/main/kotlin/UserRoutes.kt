package com.java_dragons

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun Route.userRoutes(repository: UserRepository) {
    get("/api") { call.respond(ApiInfo("Kotlin Users API", "/users")) }
    route("/users") {
        get {
            val name = call.request.queryParameters["name"]?.trim()
            if (name != null && name.isBlank()) throw BadRequestException("Параметр name не должен быть пустым")
            val limit = call.queryInt("limit", default = 20, range = 1..100)
            val offset = call.queryInt("offset", default = 0, range = 0..Int.MAX_VALUE)
            call.respond(repository.findAll(name, limit, offset))
        }
        get("/{id}") {
            val user = repository.findById(call.userId())
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Пользователь не найден"))
            } else {
                call.respond(user)
            }
        }
        post {
            if (!call.request.contentType().match(ContentType.Application.Json)) {
                throw UnsupportedMediaTypeException(call.request.contentType())
            }
            val request = try {
                call.receiveNullable<CreateUserRequest>()
            } catch (cause: ContentTransformationException) {
                throw BadRequestException("Ожидается JSON-объект с полями name и email", cause)
            } ?: throw BadRequestException("Ожидается JSON-объект с полями name и email")
            val name = request.name.trim()
            val email = request.email.trim()
            if (name.isBlank() || name.length > 100) {
                throw BadRequestException("Имя должно содержать от 1 до 100 символов")
            }
            if (email.length > 254 || !emailPattern.matches(email)) {
                throw BadRequestException("Укажите корректный email длиной до 254 символов")
            }
            val user = repository.create(name, email)
            call.response.headers.append(HttpHeaders.Location, "/users/${user.id}")
            call.respond(HttpStatusCode.Created, user)
        }
        delete("/{id}") {
            if (repository.delete(call.userId())) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Пользователь не найден"))
            }
        }
        route("/{id}") {
            handle {
                call.response.headers.append(HttpHeaders.Allow, "GET, DELETE")
                call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }
        handle {
            call.response.headers.append(HttpHeaders.Allow, "GET, POST")
            call.respond(HttpStatusCode.MethodNotAllowed)
        }
    }
}

private fun ApplicationCall.userId(): Int = parameters["id"]
    ?.toIntOrNull()?.takeIf { it > 0 }
    ?: throw BadRequestException("Параметр id должен быть положительным целым числом")

private fun ApplicationCall.queryInt(name: String, default: Int, range: IntRange): Int {
    val raw = request.queryParameters[name] ?: return default
    return raw.toIntOrNull()?.takeIf { it in range }
        ?: throw BadRequestException("Параметр $name должен быть целым числом от ${range.first} до ${range.last}")
}
