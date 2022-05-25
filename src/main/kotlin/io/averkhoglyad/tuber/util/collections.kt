package io.averkhoglyad.tuber.util

import java.util.*

inline fun <reified E : Enum<E>> emptyEnumSet(): EnumSet<E> = EnumSet.noneOf(E::class.java)
inline fun <reified E : Enum<E>> enumSetOf(vararg elements: E): EnumSet<E> = if (elements.isNotEmpty()) EnumSet.of(elements[0], *elements) else emptyEnumSet()
inline fun <reified E : Enum<E>> enumSetAll(): EnumSet<E> = EnumSet.allOf(E::class.java)

inline fun <reified K : Enum<K>, V : Any> emptyEnumMap(): EnumMap<K, V> = EnumMap<K, V>(K::class.java)
inline fun <reified K : Enum<K>, V : Any> enumMapOf(pair: Pair<K, V>): EnumMap<K, V> = EnumMap<K, V>(K::class.java).apply { this[pair.first] = pair.second }
inline fun <reified K : Enum<K>, V : Any> enumMapOf(vararg pairs: Pair<K, V>): EnumMap<K, V> = EnumMap<K, V>(K::class.java).apply { pairs.forEach { pair -> this[pair.first] = pair.second } }
