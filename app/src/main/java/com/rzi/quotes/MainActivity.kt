package com.rzi.quotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rzi.quotes.domain.repository.AdminRepository
import com.rzi.quotes.ui.navigation.RziNavHost
import com.rzi.quotes.ui.onboarding.PinSetupScreen
import com.rzi.quotes.ui.theme.RziTheme
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
