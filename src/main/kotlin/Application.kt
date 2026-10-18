package com.java_dragons

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

fun Application.module(repository: UserRepository = UserRepository()) {
    install(ContentNegotiation) { json(Json { prettyPrint = true }) }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Некорректный запрос"))
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("Используйте Content-Type: application/json"))
        }
        exception<DuplicateEmailException> { call, _ ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Пользователь с таким email уже существует"))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            call.application.log.error("Ошибка обработки запроса", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Внутренняя ошибка сервера"))
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Ресурс не найден"))
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(HttpStatusCode.MethodNotAllowed, ErrorResponse("Метод не поддерживается для этого маршрута"))
        }
        status(HttpStatusCode.UnsupportedMediaType) { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("Используйте Content-Type: application/json"))
        }
    }
    routing { userRoutes(repository) }
}
