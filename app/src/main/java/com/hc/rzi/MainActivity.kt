package com.hc.rzi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hc.rzi.domain.repository.AdminRepository
import com.hc.rzi.ui.navigation.RziNavHost
import com.hc.rzi.ui.onboarding.PinSetupScreen
import com.hc.rzi.ui.theme.RziTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var adminRepository: AdminRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RziTheme {
                val isPinSet by adminRepository.isPinSet
                    .collectAsStateWithLifecycle(initialValue = false)
                if (isPinSet) RziNavHost() else PinSetupScreen()
            }
        }
    }
}
