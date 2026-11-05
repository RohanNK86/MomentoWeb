package com.momento.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

val APP_VERSION: String = System.getenv("APP_VERSION") ?: "1.0.0"
val APP_ENV: String = System.getenv("APP_ENV") ?: "development"

private fun ApplicationCall.idParam(): Int =
    parameters["id"]?.toIntOrNull() ?: throw IllegalArgumentException("id must be a number")

fun Application.configureRoutes(store: MomentoStore) {
    routing {
        get("/") { call.respondText("Momento API $APP_VERSION - see /api/health") }

        // Liveness: is the process up? Always 200 while the JVM is serving requests.
        get("/api/health") { call.respond(HealthStatus("UP", APP_VERSION, APP_ENV)) }

        // Readiness: 200 only after start-up has finished, 503 before that.
        get("/api/ready") {
            if (store.ready) call.respond(ReadyStatus("READY"))
            else call.respond(HttpStatusCode.ServiceUnavailable, ReadyStatus("NOT_READY"))
        }

        route("/api/tasks") {
            get { call.respond(store.listTasks()) }
            post { call.respond(HttpStatusCode.Created, store.addTask(call.receive<NewTask>())) }
            patch("/{id}/toggle") {
                val task = store.toggleTask(call.idParam())
                if (task == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("task not found"))
                else call.respond(task)
            }
            delete("/{id}") {
                if (store.deleteTask(call.idParam())) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, ErrorResponse("task not found"))
            }
        }

        route("/api/habits") {
            get { call.respond(store.listHabits()) }
            post { call.respond(HttpStatusCode.Created, store.addHabit(call.receive<NewHabit>())) }
            post("/{id}/check-in") {
                val habit = store.checkIn(call.idParam())
                if (habit == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("habit not found"))
                else call.respond(habit)
            }
        }

        route("/api/expenses") {
            get { call.respond(store.listExpenses()) }
            post { call.respond(HttpStatusCode.Created, store.addExpense(call.receive<NewExpense>())) }
        }

        get("/api/summary") { call.respond(store.summary()) }
    }
}
