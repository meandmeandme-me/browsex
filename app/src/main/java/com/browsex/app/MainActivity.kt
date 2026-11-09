            activity = this,
            privateMode = tab.privateMode,
            hasPrivateTabsOpen = { credentialVault.privateBrowsingActive },
            onDeviceAuthenticated = credentialVault::unlockForSession,
            isStillValid = { session, origin ->
                !shuttingDown && tabs.any { it === tab } && tab.session === session &&
                    CredentialOriginPolicy.canonicalHttpsOrigin(tab.url) == origin
            },
            translate = ::tr
        )
    }

    private fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        topBar.visibility = if (enabled) View.GONE else View.VISIBLE
        bottomBar.visibility = if (enabled) View.GONE else View.VISIBLE
        val controller = WindowInsetsControllerCompat(window, window.decorView)