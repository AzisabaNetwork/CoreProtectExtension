package net.azisaba.coreprotectextension.model

class LookupException(val type: Type, message: String) : RuntimeException("[${type.name}] $message") {
    enum class Type {
        NO_USER,
    }

    companion object {
        fun throwNoUser(name: String): Nothing = throw LookupException(Type.NO_USER, "No such user: $name")
    }
}
