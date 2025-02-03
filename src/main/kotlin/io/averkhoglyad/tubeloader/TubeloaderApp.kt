package io.averkhoglyad.tubeloader

import com.github.kiulian.downloader.YoutubeDownloader
import io.averkhoglyad.tubeloader.layout.MainLayout
import io.averkhoglyad.tubeloader.service.ProfileService
import io.averkhoglyad.tubeloader.service.YoutubeVideoService
import io.averkhoglyad.tubeloader.util.PicoDIContainer
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
import kotlin.io.path.Path

class TubeloaderApp : App(MainLayout::class) {

    init {
        Log4jBridgeHandler.install(true, ".", true)
        FX.dicontainer = createDIContainer()
        FX.layoutDebuggerShortcut = KeyCodeCombination(KeyCode.F12)
        addStageIcon(Image(resources.stream("/img/logo.png")))
        GlyphFontRegistry.register(FontAwesome(resources.stream("/org/controlsfx/glyphfont/fontawesome-webfont.ttf")))
    }

    private fun createDIContainer(): DIContainer {
        val pico = DefaultPicoContainer()

        val userDir = Path(System.getProperty("user.home"))
        val appDir = userDir.resolve(".tubeloader")
        pico.addComponent(ProfileService(appDir.resolve("details"), userDir))

        pico.addComponent(YoutubeVideoService(YoutubeDownloader()))
        return PicoDIContainer(pico)
    }

    override fun init() {
        SplashScreen.getSplashScreen()?.close()
    }
}