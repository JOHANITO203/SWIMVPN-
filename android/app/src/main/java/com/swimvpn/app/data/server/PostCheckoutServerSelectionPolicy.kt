package com.swimvpn.app.data.server

object PostCheckoutServerSelectionPolicy {
    fun chooseActiveServerId(
        previousActiveId: String?,
        previousActiveSource: String?,
        savedSelectedId: String?,
        rebuiltActiveId: String?,
        firstBackendId: String?,
        previousImportedStillAvailable: Boolean,
    ): String? {
        if (previousActiveSource == "imported" && previousImportedStillAvailable) {
            return previousActiveId
        }

        if (!savedSelectedId.isNullOrBlank() && rebuiltActiveId != null) {
            return rebuiltActiveId
        }

        if (previousActiveSource == "backend" && rebuiltActiveId != null) {
            return rebuiltActiveId
        }

        if (previousActiveId == null && savedSelectedId == null && firstBackendId != null) {
            return firstBackendId
        }

        return rebuiltActiveId ?: firstBackendId ?: previousActiveId
    }
}
