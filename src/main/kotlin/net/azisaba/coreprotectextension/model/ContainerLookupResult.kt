package net.azisaba.coreprotectextension.model

data class ContainerLookupResult(
    val data: List<ContainerLog>,
    val lastPageIndex: Int = -1,
)
