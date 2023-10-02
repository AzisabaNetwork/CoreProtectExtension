package net.azisaba.coreprotectextension.model

import java.util.UUID

data class User(val id: Int, val name: String, val uuid: UUID?) {
    companion object {
        fun unknown(id: Int) = User(id, "<uid:$id>", null)
    }
}
