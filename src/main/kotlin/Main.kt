
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.modlin.*
import kotlin.time.Duration.Companion.milliseconds

object AppModule : Module by modlin {

    val onboardingModule by child { OnboardingModule() }
    val controlModule by child { ControlModule() }

    val mainSwitch = childSwitch(onboardingModule)




}

class OnboardingModule : Module by modlin {

    val languageChoiceModule by child { LanguageChoiceModule() }
    val operatorLoginModule by child { OperatorLoginModule() }
    val communicationChoiceModule by child { CommunicationChoiceModule() }

    init {
        launchPausable {
            val language = languageChoiceModule.awaitData()
            val communication = communicationChoiceModule.awaitData()
        }
    }

}

class ControlModule : Module by modlin {

    val settingsModule by child { SettingsModule() }
    val telemetryModule by child { TelemetryModule() }
    val mapModule by child { MapModule() }
    val playerModule by child { PlayerModule(settingsModule) }

    init {

    }

}

class SettingsModule : Module by modlin {

}

class TelemetryModule : Module by modlin {

}

class MapModule : Module by modlin {

}

class PlayerModule(
    settings: SettingsModule
) : Module by modlin {

}

class LanguageChoiceModule : Module by modlin, ReturnsData<String> by returnsData() {

    val selectedLanguage: StateFlow<String>
        field = MutableStateFlow("")

    fun selectLanguage(language: String) {
        selectedLanguage.value = language
        returnData(language)
    }

    init {
        launch {
            delay(1000.milliseconds)
            selectLanguage(selectedLanguage.value)
        }
    }

}

class OperatorLoginModule : Module by modlin {

    val typedPIN: StateFlow<String>
        field = MutableStateFlow("")

    val triesLeft: StateFlow<Int>
        field = MutableStateFlow(3)

}

class CommunicationChoiceModule : Module by modlin, ReturnsData<String> by returnsData() {

    val selectedCommunication: StateFlow<String>
        field = MutableStateFlow("")

    fun selectCommunication(communication: String) {
        selectedCommunication.value = communication
        returnData(communication)
    }

}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Modlin") {

    }
}
