                    "Town Builder - Village $village upgrade to level success"
                }
                message.startsWith("Village ") && message.endsWith(" Upgrade Success") -> {
                    val village = message.removePrefix("Village ").removeSuffix(" Upgrade Success")
                    "Town Builder - Village $village upgrade to level success"
                }
                message.startsWith("Village ") && message.endsWith(" no upgrade") -> {
                    val village = message.removePrefix("Village ").removeSuffix(" no upgrade")
                    "Town Builder - Village $village no upgrade"
                }
                else -> return
            }
        } else {
            when {
                message == "CICLE START" -> "CICLE START"
                message == "Click Send All Farmlist Success" -> "Click Send All Farmlist Success"
                message.startsWith("Farmlist Before: ") -> message
                message.startsWith("Farmlist After: ") -> message
                message.startsWith("Farmlist Added: ") -> message
                message == "CICLE END" -> "CICLE END"
                message.startsWith("Village ") && message.contains(" Upgrade to Level ") && message.endsWith(" Success") -> message
                message.startsWith("Village ") && message.endsWith(" Upgrade Success") -> message
                message.startsWith("Village ") && message.endsWith(" no upgrade") -> message
                message.startsWith("Village ") && message.contains(" Updated min L") -> message
                message.startsWith("Next Run: ") -> message
                message == "REFRESH VILLAGE START" -> "REFRESH VILLAGE START"
                message == "REFRESH VILLAGE END" -> "REFRESH VILLAGE END"
                message == "BOT ON" -> "BOT ON"
                message == "BOT OFF" -> "BOT OFF"
                else -> return
            }
        }

        // Next Run di log dibuat sekali saat countdown dimulai dan menyertakan
        // sisa countdown dalam format MM:SS, misalnya 05:20.
        val finalClean = if (clean.startsWith("Next Run: ")) {
            val remaining = (nextAt - System.currentTimeMillis()).coerceAtLeast(0L)
            val totalSeconds = remaining / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            val target = clean.removePrefix("Next Run: ").trim()
            "Next Run: $target - Count Down ${String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)}"
        } else clean

        // Tandai setiap event yang termasuk siklus aktif. LogActivity memakai marker
        // ini untuk memberi warna berbeda pada setiap CYCLE tanpa mengubah isi pesan.
        val cycleMessages = finalClean != "BOT ON" && finalClean != "BOT OFF"
        val cycleTagged = if (cycleMessages && cycleNumber > 0) "[CYCLE $cycleNumber] $finalClean" else finalClean
        val line = "${logTimeFormat.format(Date())} | $cycleTagged"
        try {
            openFileOutput(logFileName, MODE_APPEND).bufferedWriter().use { it.appendLine(line) }
        } catch (_: Exception) {
            // Logging must never interrupt the automation.
        }
    }

    private fun pruneLogs() {
        debugTrace("ENTER pruneLogs")
        try {
            val file = getFileStreamPath(logFileName)
            if (!file.exists()) return
            val cutoff = System.currentTimeMillis() - logMaxAgeMs
            val kept = file.readLines().filter { line ->
                try { logTimeFormat.parse(line.substringBefore(" | "))?.time ?: 0L >= cutoff }
                catch (_: Exception) { false }
            }
            file.writeText(kept.joinToString("\n") + if (kept.isNotEmpty()) "\n" else "")
        } catch (_: Exception) {}
    }

    inner class FarmBridge {
        @JavascriptInterface
        fun onLoginResult(result: String) {
            debugTrace("ENTER onLoginResult")
            handler.post {
                if (!running) return@post
                when (result) {
                    "submitting" -> updateNotification("Farm Assistant — mengirim login")
                    "no_login_form" -> {
                        loginInProgress = false
                        reloginRequested = false
                        loginRetryCount = 0
                        logEvent("Session aktif terdeteksi; membuka Farm List")
                        handler.postDelayed({ triggerStartAllFarmLists() }, 250)
                    }
                    "no_username_field", "no_form" -> {
                        if (loginRetryCount < 20 && running) handler.postDelayed({ autoLoginIfNeeded() }, 1000)
                        else {
                            loginInProgress = false
                            reloginRequested = false
                            logEvent("Form login Travian tidak dikenali")
                        }
                    }
                }
            }
        }

        @JavascriptInterface
        fun onHoldCelebrationClick(villageName: String) {
                        handler.post {
                if (running && holdCelebrationInProgress) {
                    logEvent("Celebration ($villageName) Success")
                }
            }
        }

        @JavascriptInterface
        fun onVillageListResult(result: String) {
            debugTrace("ENTER onVillageListResult")
            handler.post {
                handleVillageListResult(result)
            }
        }
    }

    override fun onDestroy() {
        debugTrace("ENTER onDestroy")
        handler.removeCallbacksAndMessages(null)
        webView?.destroy()
        webView = null
        instanceRef = null
        visibleWebViewRef = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        debugTrace("ENTER onBind")
        return null
