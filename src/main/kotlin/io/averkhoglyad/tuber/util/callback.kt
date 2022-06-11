package io.averkhoglyad.tuber.util

typealias CallbackFn<E> = (E) -> Unit
val noCallback: (Any?) -> Unit = {_ -> }
