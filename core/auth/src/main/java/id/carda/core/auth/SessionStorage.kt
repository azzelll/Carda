package id.carda.core.auth

/** Domain access to local identity. Tokens never enter UI state. */
interface SessionStorage {
    fun read(): AuthSession?
    fun clear()
}
