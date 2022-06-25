package io.averkhoglyad.tuber

import com.github.kiulian.downloader.YoutubeDownloader
import io.averkhoglyad.tuber.layout.MainLayout
import io.averkhoglyad.tuber.service.YoutubeVideoService
import io.averkhoglyad.tuber.util.PicoDIContainer
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import org.apache.logging.log4j.jul.Log4jBridgeHandler
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.GlyphFontRegistry
import org.picocontainer.DefaultPicoContainer
import tornadofx.App
import tornadofx.DIContainer
import tornadofx.FX
import tornadofx.addStageIcon
import java.awt.SplashScreen

class TuberApp : App(MainLayout::class) {
    init {
        Log4jBridgeHandler.install(true, ".", true)
        FX.dicontainer = createDIContainer()
        FX.layoutDebuggerShortcut = KeyCodeCombination(KeyCode.F12)
//        addStageIcon(Image(resources.stream("/img/arrow.png")))
        GlyphFontRegistry.register(FontAwesome(resources.stream("/org/controlsfx/glyphfont/fontawesome-webfont.ttf")))
    }

    private fun createDIContainer(): DIContainer {
        val pico = DefaultPicoContainer()
        pico.addComponent(YoutubeVideoService(YoutubeDownloader()))
        return PicoDIContainer(pico)
    }

    override fun init() {
        SplashScreen.getSplashScreen()?.close()
    }

}