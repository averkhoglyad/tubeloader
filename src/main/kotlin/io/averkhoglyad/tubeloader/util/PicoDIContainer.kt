package io.averkhoglyad.tubeloader.util

import org.picocontainer.PicoContainer
import tornadofx.*
import kotlin.reflect.KClass

class PicoDIContainer(private val pico: PicoContainer) : DIContainer {

    override fun <T : Any> getInstance(type: KClass<T>) = pico.getComponent(type.java)!!

}
