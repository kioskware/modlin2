import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import pl.modlin.ModlinApp
import pl.modlin.Module
import pl.modlin.ModuleRoute
import pl.modlin.child
import pl.modlin.launch
import pl.modlin.launchPausable
import pl.modlin.modlin

class OnboardingModule : Module by modlin {

    val languageChoiceModule by child { LanguageChoiceModule() }
    val operatorLoginModule by child { OperatorLoginModule() }
    val communicationChoiceModule by child { CommunicationChoiceModule() }

    init {
        launch {

        }
        launchPausable {

        }
    }

}

class LanguageChoiceModule : Module by modlin {


}

class OperatorLoginModule : Module by modlin {


}

class CommunicationChoiceModule : Module by modlin {


}

class ModuleSwitch(
    vararg modules: ModuleRoute<*>
) {

    inline fun <reified T : Module> get(): T {
        TODO("Module switching is not implemented yet")
    }

}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Modlin") {
        ModlinApp()
    }
}
