package io.github.peeresla.ods.ui

import androidx.compose.runtime.Composable
import io.github.peeresla.ods.AppViewModel
import io.github.peeresla.ods.Route

/** Экран по адресу (SCREENS в app.js). */
@Composable
fun RouteContent(route: Route, vm: AppViewModel) {
    when (route.screen) {
        "train" -> TrainScreen(vm)
        "exam" -> ExamScreen(vm, result = route.arg == "result")
        "mistakes" -> MistakesScreen(vm)
        "scenarios" -> if (route.arg != null) ScenarioScreen(vm, route.arg!!) else ScenariosScreen(vm)
        "docs" -> DocsScreen(vm)
        "settings" -> SettingsScreen(vm)
        else -> HomeScreen(vm)
    }
}
