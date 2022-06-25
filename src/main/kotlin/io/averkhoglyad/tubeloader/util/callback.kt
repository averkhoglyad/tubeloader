package io.averkhoglyad.tubeloader.util

typealias CallbackFn<E> = (E) -> Unit
val noCallback: (Any?) -> Unit = {_ -> }
