package com.lannie.morningalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.service.SyncService
import com.lannie.morningalarm.ui.DaughterHome
import com.lannie.morningalarm.ui.MomHome
import com.lannie.morningalarm.ui.MorningTheme
import com.lannie.morningalarm.ui.OnboardingScreen
import com.lannie.morningalarm.ui.TzState

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = Prefs(this)
        TzState.zoneId = prefs.displayTz
        requestNotifPermission()
        if (prefs.onboarded) SyncService.start(this)

        setContent {
            MorningTheme {
                var onboarded by remember { mutableStateOf(prefs.onboarded) }
                if (!onboarded) {
                    OnboardingScreen(onDone = {
                        onboarded = true
                        SyncService.start(this)
                    })
                } else if (prefs.role == "mom") {
                    MomHome(prefs)
                } else {
                    DaughterHome(prefs)
                }
            }
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
