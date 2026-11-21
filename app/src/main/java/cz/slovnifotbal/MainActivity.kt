package cz.slovnifotbal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cz.slovnifotbal.ui.WordFootballApp

/** Jediná aktivita aplikace – veškerá logika je ve ViewModelech a herním enginu. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as SlovniFotbalApplication).container
        setContent {
            WordFootballApp(settingsFlow = container.dataStoreManager.settingsFlow)
        }
    }
}
