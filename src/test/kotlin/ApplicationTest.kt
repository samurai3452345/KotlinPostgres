package com.java_dragons

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `API information is available as JSON`() = testApplication {
        application { module() }
        val response = client.get("/api")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("/users", Json.parseToJsonElement(response.bodyAsText()).jsonObject["users"]?.jsonPrimitive?.content)
    }

    @Test
    fun `root serves the frontend HTML`() = testApplication {
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        assertTrue(response.bodyAsText().contains("<html lang=\"ru\">"))
        assertTrue(response.bodyAsText().contains("/assets/app.js"))
    }

    @Test
    fun `frontend assets are served with correct types`() = testApplication {
        application { module() }
        for ((path, type) in listOf("styles.css" to "text/css", "app.js" to "text/javascript", "favicon.svg" to "image/svg+xml")) {
            val response = client.get("/assets/$path")
            assertEquals(HttpStatusCode.OK, response.status, path)
            assertEquals(type, response.contentType()?.withoutParameters().toString(), path)
            assertTrue(response.bodyAsText().isNotBlank())
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/assets/missing.css").status)
    }

    @Test
    fun `new repository starts with empty list`() = testApplication {
        application { module() }
        val response = client.get("/users")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `user can be created fetched and deleted`() = testApplication {
        application { module() }
        val created = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"  Анна  ","email":"  anna@example.com  "}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val user = Json.decodeFromString<User>(created.bodyAsText())
        assertEquals("Анна", user.name)
        assertEquals("anna@example.com", user.email)
        assertTrue(user.id > 0)
        assertEquals("/users/${user.id}", created.headers[HttpHeaders.Location])
        val fetched = client.get("/users/${user.id}")
        assertEquals(HttpStatusCode.OK, fetched.status)
        assertEquals(user, Json.decodeFromString<User>(fetched.bodyAsText()))
        val deleted = client.delete("/users/${user.id}")
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals("", deleted.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/users/${user.id}").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/users/${user.id}").status)
        assertEquals("[]", client.get("/users").bodyAsText())
    }

    @Test
    fun `name filter ignores case and pagination follows filtering`() = testApplication {
        application { module() }
        for ((index, name) in listOf("Anna", "Boris", "Annabel", "ANNA Maria").withIndex()) {
            assertEquals(HttpStatusCode.Created, client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"$name","email":"user$index@example.com"}""")
            }.status)
        }
        val response = client.get("/users?name=anna&limit=1&offset=1")
        assertEquals(HttpStatusCode.OK, response.status)
        val users = Json.decodeFromString<List<User>>(response.bodyAsText())
        assertEquals(listOf("Annabel"), users.map { it.name })
        assertEquals("[]", client.get("/users?offset=100").bodyAsText())
        assertEquals("[]", client.get("/users?name=Nobody").bodyAsText())
    }

    @Test
    fun `duplicate email returns conflict without creating another user`() = testApplication {
        application { module() }
        for ((index, email) in listOf("anna@example.com", "ANNA@example.com").withIndex()) {
            val response = client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Anna","email":"$email"}""")
            }
            assertEquals(if (index == 0) HttpStatusCode.Created else HttpStatusCode.Conflict, response.status)
            if (index == 1) assertJsonError(response.bodyAsText())
        }
        assertEquals(1, Json.parseToJsonElement(client.get("/users").bodyAsText()).jsonArray.size)
    }

    @Test
    fun `invalid IDs return bad request for get and delete`() = testApplication {
        application { module() }
        for (id in listOf("abc", "0", "-1", "2147483648")) {
            for (response in listOf(client.get("/users/$id"), client.delete("/users/$id"))) {
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertJsonError(response.bodyAsText())
            }
        }
    }

    @Test
    fun `missing users and routes return JSON not found`() = testApplication {
        application { module() }
        for (response in listOf(client.get("/users/999"), client.delete("/users/999"), client.get("/missing"))) {
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertJsonError(response.bodyAsText())
        }
    }

    @Test
    fun `invalid query parameters return bad request`() = testApplication {
        application { module() }
        for (query in listOf("limit=0", "limit=101", "limit=x", "limit=", "offset=-1", "offset=x", "offset=2147483648", "name=%20")) {
            val response = client.get("/users?$query")
            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertJsonError(response.bodyAsText())
        }
    }

    @Test
    fun `invalid fields do not create users`() = testApplication {
        application { module() }
        val invalidRequests = listOf(
            CreateUserRequest(" ", "a@example.com"),
            CreateUserRequest("a".repeat(101), "a@example.com"),
            CreateUserRequest("Anna", ""),
            CreateUserRequest("Anna", "not-an-email"),
            CreateUserRequest("Anna", "a b@example.com"),
            CreateUserRequest("Anna", "a".repeat(250) + "@example.com")
        )
        for (request in invalidRequests) {
            val response = client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(request))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertJsonError(response.bodyAsText())
        }
        assertEquals("[]", client.get("/users").bodyAsText())
    }

    @Test
    fun `malformed JSON and missing fields return bad request`() = testApplication {
        application { module() }
        for (body in listOf("{", "", "{}", "null", "[]", """{"name":"Anna"}""", """{"name":null,"email":"a@example.com"}""")) {
            val response = client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, "JSON body: '$body'")
            assertJsonError(response.bodyAsText())
        }
    }

    @Test
    fun `non JSON request returns unsupported media type`() = testApplication {
        application { module() }
        val response = client.post("/users") {
            contentType(ContentType.Text.Plain)
            setBody("name=Anna")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertJsonError(response.bodyAsText())
    }

    @Test
    fun `unsupported method returns JSON method not allowed`() = testApplication {
        application { module() }
        for ((path, allowed) in listOf("/users" to "GET, POST", "/users/1" to "GET, DELETE")) {
            val response = client.put(path)
            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertEquals(allowed, response.headers[HttpHeaders.Allow])
            assertJsonError(response.bodyAsText())
        }
    }

    @Test
    fun `deleted IDs are not reused`() = testApplication {
        application { module() }
        suspend fun createUser(): User {
            val response = client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Anna","email":"anna@example.com"}""")
            }
            assertEquals(HttpStatusCode.Created, response.status)
            return Json.decodeFromString(response.bodyAsText())
        }
        val first = createUser()
        assertEquals(HttpStatusCode.NoContent, client.delete("/users/${first.id}").status)
        assertTrue(createUser().id > first.id)
    }

    private fun assertJsonError(body: String) {
        assertTrue(Json.decodeFromString<ErrorResponse>(body).error.isNotBlank())
    }
}
