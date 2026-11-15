package app.glass.tresor.disguise

import androidx.compose.runtime.Composable
import app.glass.tresor.App
import app.glass.tresor.Disguise
import app.glass.tresor.Screen

@Composable
fun DisguiseRoot() {
    when (App.disguise) {
        Disguise.TRESOR -> app.glass.tresor.ui.screens.LockScreen(asRoot = true)
        Disguise.RECHNER -> CalculatorApp()
        Disguise.KALENDER -> CalendarApp()
        Disguise.CHECKLISTE -> ChecklistApp()
        Disguise.NEWS -> NewsApp()
    }
}

/** Geheimer Zugang ohne Code-Eingabe in der Tarnung → PIN-Abfrage. */
fun openSecretLock() {
    App.nav.push(Screen.Lock)
}
