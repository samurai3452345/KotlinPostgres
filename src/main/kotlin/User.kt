package com.java_dragons

import kotlinx.serialization.Serializable

@Serializable
data class User(val id: Int, val name: String, val email: String)

@Serializable
data class CreateUserRequest(val name: String, val email: String)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class ApiInfo(val name: String, val users: String)
