package io.averkhoglyad.tuber

import io.averkhoglyad.tuber.layout.MainLayout
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.stage.Stage
import org.apache.logging.log4j.jul.Log4jBridgeHandler
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.GlyphFontRegistry
import tornadofx.App
import tornadofx.FX
import java.awt.SplashScreen

class TuberApp : App(MainLayout::class) {
    init {
        Log4jBridgeHandler.install(true, ".", true)
        FX.layoutDebuggerShortcut = KeyCodeCombination(KeyCode.F12)
//        addStageIcon(Image(resources.stream("/img/arrow.png")))
        GlyphFontRegistry.register(FontAwesome(resources.stream("/org/controlsfx/glyphfont/fontawesome-webfont.ttf")))
//        FX.dicontainer = createDIContainer()
        SplashScreen.getSplashScreen()?.close()
    }
}