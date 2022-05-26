package io.averkhoglyad.tuber.util

typealias CallbackFn<E> = (E) -> Unit

val noop: () -> Unit = {}
val noop1: (Any?) -> Unit = {_1 -> }
val noop2: (_1: Any?, _2: Any?) -> Unit = {_1, _2 -> }
val noop3: (_1: Any?, _2: Any?, _3: Any?) -> Unit = {_1, _2, _3 -> }
val noop4: (_1: Any?, _2: Any?, _3: Any?, _4: Any?) -> Unit = {_1, _2, _3,_4 -> }
val noop5: (_1: Any?, _2: Any?, _3: Any?, _4: Any?, _5: Any?) -> Unit = {_1, _2, _3,_4, _5 -> }
val noop6: (_1: Any?, _2: Any?, _3: Any?, _4: Any?, _5: Any?, _6: Any?) -> Unit = {_1, _2, _3,_4, _5, _6 -> }
val noop7: (_1: Any?, _2: Any?, _3: Any?, _4: Any?, _5: Any?, _6: Any?, _7: Any?) -> Unit = {_1, _2, _3,_4, _5, _6, _7 -> }
val noop8: (_1: Any?, _2: Any?, _3: Any?, _4: Any?, _5: Any?, _6: Any?, _7: Any?, _8: Any?) -> Unit = {_1, _2, _3,_4, _5, _6, _7, _8 -> }
val noop9: (_1: Any?, _2: Any?, _3: Any?, _4: Any?, _5: Any?, _6: Any?, _7: Any?, _8: Any?, _9: Any?) -> Unit = {_1, _2, _3,_4, _5, _6, _7, _8, _9 -> }
