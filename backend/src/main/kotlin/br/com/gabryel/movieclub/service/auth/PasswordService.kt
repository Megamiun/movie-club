package br.com.gabryel.movieclub.service.auth

import de.mkammerer.argon2.Argon2Factory

interface PasswordService {
    fun hash(password: String): String

    fun verify(hash: String, password: String): Boolean
}

private val argon2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id)

// OWASP recommended minimums for Argon2id
private const val ITERATIONS = 2
private const val MEMORY_KB = 19456
private const val PARALLELISM = 1

class Argon2PasswordService : PasswordService {
    override fun hash(password: String): String =
        argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, password.toCharArray())

    override fun verify(hash: String, password: String): Boolean =
        argon2.verify(hash, password.toCharArray())
}
