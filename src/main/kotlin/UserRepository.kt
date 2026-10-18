package com.java_dragons

/** Данные живут до остановки сервера. База данных для задания не требуется. */
class UserRepository {
    private val users = linkedMapOf<Int, User>()
    private var nextId = 1

    @Synchronized
    fun findAll(name: String?, limit: Int, offset: Int): List<User> = users.values
        .filter { name == null || it.name.contains(name, ignoreCase = true) }
        .drop(offset)
        .take(limit)

    @Synchronized
    fun findById(id: Int): User? = users[id]

    // Проверка уникальности и добавление выполняются под одной блокировкой.
    @Synchronized
    fun create(name: String, email: String): User {
        if (users.values.any { it.email.equals(email, ignoreCase = true) }) {
            throw DuplicateEmailException()
        }
        val user = User(nextId++, name, email)
        users[user.id] = user
        return user
    }

    @Synchronized
    fun delete(id: Int): Boolean = users.remove(id) != null
}

class DuplicateEmailException : RuntimeException()
