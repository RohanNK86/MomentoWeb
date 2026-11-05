package com.momento.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** API tests: the app runs in memory through Ktor's test engine, no port or server needed. */
class ApplicationTest {

    @Test
    fun `health endpoint reports UP`() = testApplication {
        application { module() }
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"UP\""))
    }

    @Test
    fun `ready endpoint returns 200 after start-up`() = testApplication {
        application { module() }
        val response = client.get("/api/ready")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("READY"))
    }

    @Test
    fun `seeded tasks are listed`() = testApplication {
        application { module() }
        val body = client.get("/api/tasks").bodyAsText()
        assertTrue(body.contains("Finalize Q4 Marketing Deck"))
    }

    @Test
    fun `creating a task returns 201`() = testApplication {
        application { module() }
        val response = client.post("/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Write DevOps report","priority":"HIGH"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("Write DevOps report"))
    }

    @Test
    fun `blank task title is rejected with 400`() = testApplication {
        application { module() }
        val response = client.post("/api/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `unknown route returns 404`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/does-not-exist").status)
    }
}
