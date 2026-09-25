package me.timeto.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import me.timeto.app.ui.VStack
import me.timeto.app.ui.ZStack
import me.timeto.app.ui.activity.ActivityScreen
import me.timeto.app.ui.home.HomeScreen
import me.timeto.app.ui.navigation.NavigationScreen
import me.timeto.app.ui.settings.SettingsScreen

/**
 * The selected tab is observable so an outside surface, such as the alarm
 * screen, can deep-link to home.
 */
val mainTabFlow = MutableStateFlow(MainTabEnum.home)

@Composable
fun MainScreen() {

    val tab = mainTabFlow.collectAsState()

    VStack {

        ZStack(
            modifier = Modifier
                .weight(1f),
        ) {
            when (tab.value) {
                MainTabEnum.home -> {
                    NavigationScreen {
                        HomeScreen()
                    }
                }
                MainTabEnum.activities -> {
                    NavigationScreen {
                        ActivityScreen(
                            onClose = {
                                mainTabFlow.value = MainTabEnum.home
                            },
                        )
                    }
                }
                MainTabEnum.settings -> {
                    NavigationScreen {
                        SettingsScreen(
                            onClose = {
                                mainTabFlow.value = MainTabEnum.home
                            },
                        )
                    }
                }
            }
        }

        MainTabsView(
            tab = tab.value,
            onTabChanged = { newTab ->
                mainTabFlow.value = newTab
            },
        )
    }
}
