package dev.triplet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.triplet.app.ui.AppsScreen
import dev.triplet.app.ui.HomeScreen
import dev.triplet.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as TripletApp).routesStore
        setContent {
            MaterialTheme {
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        // material-icons-core отсутствует в classpath (material3 1.5+ его не тянет):
                        // текстовые табы вместо иконок, зависимость не добавляем.
                        NavigationBar {
                            NavigationBarItem(tab == 0, onClick = { tab = 0 },
                                icon = { Text(getString(R.string.tab_home)) })
                            NavigationBarItem(tab == 1, onClick = { tab = 1 },
                                icon = { Text(getString(R.string.tab_apps)) })
                            NavigationBarItem(tab == 2, onClick = { tab = 2 },
                                icon = { Text(getString(R.string.tab_settings)) })
                        }
                    },
                ) { pad ->
                    val modifier = Modifier.padding(pad)
                    when (tab) {
                        0 -> HomeScreen(store, modifier)
                        1 -> AppsScreen(store, modifier)
                        else -> SettingsScreen(store, modifier)
                    }
                }
            }
        }
    }
}
