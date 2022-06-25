package io.averkhoglyad.tubeloader.util

import javafx.beans.binding.*
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableNumberValue
import javafx.beans.value.ObservableObjectValue
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableSet
import javafx.collections.SetChangeListener
import tornadofx.*

// ObservableList
fun <E> ObservableList<E>.containsProperty(obj: E): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.contains(obj))
    this.onChange { c: ListChangeListener.Change<out E> ->
        result.set(c.list.contains(obj))
    }
    return result
}

fun <E> ObservableList<E>.containsProperty(objProperty: ObservableValue<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.contains(objProperty.value))
    objProperty.onChange {
        result.set(this.contains(it))
    }
    this.onChange { c: ListChangeListener.Change<out E> ->
        result.set(c.list.contains(objProperty.value))
    }
    return result
}

fun <E> ObservableList<E>.containsAllProperty(list: ObservableList<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.containsAll(list))
    list.onChange { c: ListChangeListener.Change<out E> ->
        result.set(this.containsAll(c.list))
    }
    this.onChange { c: ListChangeListener.Change<out E> ->
        result.set(c.list.containsAll(list))
    }
    return result
}

fun <E> ObservableList<E>.containsAllProperty(set: ObservableSet<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.containsAll(set))
    set.onChange { c: SetChangeListener.Change<out E> ->
        result.set(this.containsAll(c.set))
    }
    this.onChange { c: ListChangeListener.Change<out E> ->
        result.set(c.list.containsAll(set))
    }
    return result
}

fun <E> ObservableList<E>.containsAllProperty(collection: Collection<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.containsAll(collection))
    this.onChange { c: ListChangeListener.Change<out E> ->
        result.set(c.list.containsAll(collection))
    }
    return result
}

fun <E> ObservableList<E>.containsAnyProperty(list: ObservableList<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.any { list.contains(it) })
    list.onChange { c: ListChangeListener.Change<out E> ->
        result.set(this.any { c.list.contains(it) })
    }
    this.onChange { c: ListChangeListener.Change<out E> ->
        result.set(c.list.any { list.contains(it) })
    }
    return result
}

fun <E> ObservableList<E>.containsAnyProperty(set: ObservableSet<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.any { set.contains(it) })
    set.onChange { c: SetChangeListener.Change<out E> ->
        result.set(this.any { c.set.contains(it) })
    }
    this.onChange { c: ListChangeListener.Change<out E> ->
        result.set(c.list.any { set.contains(it) })
    }
    return result
}

fun <E> ObservableList<E>.containsAnyProperty(collection: Collection<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.any { collection.contains(it) })
    this.onChange { c: ListChangeListener.Change<out E> ->
        result.set(c.list.any { collection.contains(it) })
    }
    return result
}

fun <T> ObservableList<T>.onChange(op: (ListChangeListener.Change<out T>) -> Unit) = apply {
    addListener(ListChangeListener { op(it) })
}

// ObservableSet
fun <E> ObservableSet<E>.containsProperty(obj: E): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.contains(obj))
    this.onChange { c: SetChangeListener.Change<out E> ->
        result.set(c.set.contains(obj))
    }
    return result
}

fun <E> ObservableSet<E>.containsProperty(objProperty: ObservableValue<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.contains(objProperty.value))
    objProperty.onChange {
        result.set(this.contains(it))
    }
    this.onChange { c: SetChangeListener.Change<out E> ->
        result.set(c.set.contains(objProperty.value))
    }
    return result
}

fun <E> ObservableSet<E>.containsAllProperty(set: ObservableSet<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.containsAll(set))
    set.onChange { c: SetChangeListener.Change<out E> ->
        result.set(this.containsAll(c.set))
    }
    this.onChange { c: SetChangeListener.Change<out E> ->
        result.set(c.set.containsAll(set))
    }
    return result
}

fun <E> ObservableSet<E>.containsAllProperty(list: ObservableList<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.containsAll(list))
    list.onChange { c: ListChangeListener.Change<out E> ->
        result.set(this.containsAll(c.list))
    }
    this.onChange { c: SetChangeListener.Change<out E> ->
        result.set(c.set.containsAll(list))
    }
    return result
}

fun <E> ObservableSet<E>.containsAllProperty(collection: Collection<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.containsAll(collection))
    this.onChange { c: SetChangeListener.Change<out E> ->
        result.set(c.set.containsAll(collection))
    }
    return result
}

fun <E> ObservableSet<E>.containsAnyProperty(set: ObservableSet<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.any { set.contains(it) })
    set.onChange { c: SetChangeListener.Change<out E> ->
        result.set(this.any { c.set.contains(it) })
    }
    this.onChange { c: SetChangeListener.Change<out E> ->
        result.set(c.set.any { set.contains(it) })
    }
    return result
}

fun <E> ObservableSet<E>.containsAnyProperty(list: ObservableList<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.any { list.contains(it) })
    list.onChange { c: ListChangeListener.Change<out E> ->
        result.set(this.any { c.list.contains(it) })
    }
    this.onChange { c: SetChangeListener.Change<out E> ->
        result.set(c.set.any { list.contains(it) })
    }
    return result
}

fun <E> ObservableSet<E>.containsAnyProperty(collection: Collection<E>): ObservableBooleanValue {
    val result = ReadOnlyBooleanWrapper(this.any { collection.contains(it) })
    this.onChange { c: SetChangeListener.Change<out E> ->
        result.set(c.set.any { collection.contains(it) })
    }
    return result
}

fun <T> ObservableSet<T>.onChange(op: (SetChangeListener.Change<out T>) -> Unit) = apply {
    addListener(SetChangeListener { op(it) })
}

// Other
fun <T> Property<T>.bind(op: () -> ObservableValue<T>) {
    this.bind(op())
}

fun <T> Property<T>.forceWith(other: () -> ObservableValue<T>) = this.forceWith(other())

fun <T> Property<T>.forceWith(other: ObservableValue<T>) {
    this.value = other.value
    other.onChange {
        this.value = other.value
    }
    this.onChange {
        if (this.value != other.value) {
            this.value = other.value
        }
    }
}

fun <T, R> ObservableValue<T>.convert(fn: (T) -> R): ObservableValue<R> {
    val result = ReadOnlyObjectWrapper(fn(this.value))
    this.onChange { result.set(fn(this.value)) }
    return result
}

// Bindings
fun <T> ObservableValue<*>.select(vararg steps: String): ObjectBinding<T> {
    return Bindings.select(this, *steps)
}

fun ObservableValue<*>.selectDouble(vararg steps: String): DoubleBinding {
    return Bindings.selectDouble(this, *steps)
}

fun ObservableValue<*>.selectFloat(vararg steps: String): FloatBinding {
    return Bindings.selectFloat(this, *steps)
}

fun ObservableValue<*>.selectInteger(vararg steps: String): IntegerBinding {
    return Bindings.selectInteger(this, *steps)
}

fun ObservableValue<*>.selectLong(vararg steps: String): LongBinding {
    return Bindings.selectLong(this, *steps)
}

fun ObservableValue<*>.selectBoolean(vararg steps: String): BooleanBinding {
    return Bindings.selectBoolean(this, *steps)
}

fun ObservableValue<*>.selectString(vararg steps: String): StringBinding {
    return Bindings.selectString(this, *steps)
}

fun <T> Any.select(vararg steps: String): ObjectBinding<T> {
    return Bindings.select(this, *steps)
}

fun Any.selectDouble(vararg steps: String): DoubleBinding {
    return Bindings.selectDouble(this, *steps)
}

fun Any.selectFloat(vararg steps: String): FloatBinding {
    return Bindings.selectFloat(this, *steps)
}

fun Any.selectInteger(vararg steps: String): IntegerBinding {
    return Bindings.selectInteger(this, *steps)
}

fun Any.selectLong(vararg steps: String): LongBinding {
    return Bindings.selectLong(this, *steps)
}

fun Any.selectBoolean(vararg steps: String): BooleanBinding {
    return Bindings.selectBoolean(this, *steps)
}

fun Any.selectString(vararg steps: String): StringBinding {
    return Bindings.selectString(this, *steps)
}

// Null/NotNull
fun ObservableObjectValue<*>.isNull(): BooleanBinding = Bindings.isNull(this)
fun ObservableObjectValue<*>.isNotNull(): BooleanBinding = Bindings.isNotNull(this)

// Numeric bindings
fun ObservableNumberValue.negate(): NumberBinding = Bindings.negate(this)
operator fun ObservableNumberValue.unaryMinus() = this.negate()

// +
fun ObservableNumberValue.add(other: ObservableNumberValue): NumberBinding = Bindings.add(this, other)
infix operator fun ObservableNumberValue.plus(other: ObservableNumberValue) = this.add(other)

fun ObservableNumberValue.add(other: Double): DoubleBinding = Bindings.add(this, other)
infix operator fun ObservableNumberValue.plus(other: Double) = this.add(other)

fun Double.add(other: ObservableNumberValue): DoubleBinding = Bindings.add(this, other)
infix operator fun Double.plus(other: ObservableNumberValue) = this.add(other)

fun ObservableNumberValue.add(other: Float): NumberBinding = Bindings.add(this, other)
infix operator fun ObservableNumberValue.plus(other: Float) = this.add(other)

fun Float.add(other: ObservableNumberValue): NumberBinding = Bindings.add(this, other)
infix operator fun Float.plus(other: ObservableNumberValue) = this.add(other)

fun ObservableNumberValue.add(other: Long): NumberBinding = Bindings.add(this, other)
infix operator fun ObservableNumberValue.plus(other: Long) = this.add(other)

fun Long.add(other: ObservableNumberValue): NumberBinding = Bindings.add(this, other)
infix operator fun Long.plus(other: ObservableNumberValue) = this.add(other)

fun ObservableNumberValue.add(other: Int): NumberBinding = Bindings.add(this, other)
infix operator fun ObservableNumberValue.plus(other: Int) = this.add(other)

fun Int.add(other: ObservableNumberValue): NumberBinding = Bindings.add(this, other)
infix operator fun Int.plus(other: ObservableNumberValue) = this.add(other)

// -
fun ObservableNumberValue.subtract(other: ObservableNumberValue): NumberBinding = Bindings.subtract(this, other)
infix operator fun ObservableNumberValue.minus(other: ObservableNumberValue) = this.subtract(other)

fun ObservableNumberValue.subtract(other: Double): DoubleBinding = Bindings.subtract(this, other)
infix operator fun ObservableNumberValue.minus(other: Double) = this.subtract(other)

fun Double.subtract(other: ObservableNumberValue): DoubleBinding = Bindings.subtract(this, other)
infix operator fun Double.minus(other: ObservableNumberValue) = this.subtract(other)

fun ObservableNumberValue.subtract(other: Float): NumberBinding = Bindings.subtract(this, other)
infix operator fun ObservableNumberValue.minus(other: Float) = this.subtract(other)

fun Float.subtract(other: ObservableNumberValue): NumberBinding = Bindings.subtract(this, other)
infix operator fun Float.minus(other: ObservableNumberValue) = this.subtract(other)

fun ObservableNumberValue.subtract(other: Long): NumberBinding = Bindings.subtract(this, other)
infix operator fun ObservableNumberValue.minus(other: Long) = this.subtract(other)

fun Long.subtract(other: ObservableNumberValue): NumberBinding = Bindings.subtract(this, other)
infix operator fun Long.minus(other: ObservableNumberValue) = this.subtract(other)

fun ObservableNumberValue.subtract(other: Int): NumberBinding = Bindings.subtract(this, other)
infix operator fun ObservableNumberValue.minus(other: Int) = this.subtract(other)

fun Int.subtract(other: ObservableNumberValue): NumberBinding = Bindings.subtract(this, other)
infix operator fun Int.minus(other: ObservableNumberValue) = this.subtract(other)

// *
fun ObservableNumberValue.multiply(other: ObservableNumberValue): NumberBinding = Bindings.multiply(this, other)
infix operator fun ObservableNumberValue.times(other: ObservableNumberValue) = this.multiply(other)

fun ObservableNumberValue.multiply(other: Double): DoubleBinding = Bindings.multiply(this, other)
infix operator fun ObservableNumberValue.times(other: Double) = this.multiply(other)

fun Double.multiply(other: ObservableNumberValue): DoubleBinding = Bindings.multiply(this, other)
infix operator fun Double.times(other: ObservableNumberValue) = this.multiply(other)

fun ObservableNumberValue.multiply(other: Float): NumberBinding = Bindings.multiply(this, other)
infix operator fun ObservableNumberValue.times(other: Float) = this.multiply(other)

fun Float.multiply(other: ObservableNumberValue): NumberBinding = Bindings.multiply(this, other)
infix operator fun Float.times(other: ObservableNumberValue) = this.multiply(other)

fun ObservableNumberValue.multiply(other: Long): NumberBinding = Bindings.multiply(this, other)
infix operator fun ObservableNumberValue.times(other: Long) = this.multiply(other)

fun Long.multiply(other: ObservableNumberValue): NumberBinding = Bindings.multiply(this, other)
infix operator fun Long.times(other: ObservableNumberValue) = this.multiply(other)

fun ObservableNumberValue.multiply(other: Int): NumberBinding = Bindings.multiply(this, other)
infix operator fun ObservableNumberValue.times(other: Int) = this.multiply(other)

fun Int.multiply(other: ObservableNumberValue): NumberBinding = Bindings.multiply(this, other)
infix operator fun Int.times(other: ObservableNumberValue) = this.multiply(other)

// /
fun ObservableNumberValue.divide(other: ObservableNumberValue): NumberBinding = Bindings.divide(this, other)
infix operator fun ObservableNumberValue.div(other: ObservableNumberValue) = this.divide(other)

fun ObservableNumberValue.divide(other: Double): DoubleBinding = Bindings.divide(this, other)
infix operator fun ObservableNumberValue.div(other: Double) = this.divide(other)

fun Double.divide(other: ObservableNumberValue): DoubleBinding = Bindings.divide(this, other)
infix operator fun Double.div(other: ObservableNumberValue) = this.divide(other)

fun ObservableNumberValue.divide(other: Float): NumberBinding = Bindings.divide(this, other)
infix operator fun ObservableNumberValue.div(other: Float) = this.divide(other)

fun Float.divide(other: ObservableNumberValue): NumberBinding = Bindings.divide(this, other)
infix operator fun Float.div(other: ObservableNumberValue) = this.divide(other)

fun ObservableNumberValue.divide(other: Long): NumberBinding = Bindings.divide(this, other)
infix operator fun ObservableNumberValue.div(other: Long) = this.divide(other)

fun Long.divide(other: ObservableNumberValue): NumberBinding = Bindings.divide(this, other)
infix operator fun Long.div(other: ObservableNumberValue) = this.divide(other)

fun ObservableNumberValue.divide(other: Int): NumberBinding = Bindings.divide(this, other)
infix operator fun ObservableNumberValue.div(other: Int) = this.divide(other)

fun Int.divide(other: ObservableNumberValue): NumberBinding = Bindings.divide(this, other)
infix operator fun Int.div(other: ObservableNumberValue) = this.divide(other)

// TODO: Add bindings for equal/notEqual with epsilon
infix fun ObservableNumberValue.near(epsilon: Double) = Near(this, epsilon)
class Near internal constructor(val obs: ObservableNumberValue, val epsilon: Double)

// TODO: Add bindings for equal with epsilon
fun ObservableNumberValue.equal(other: ObservableNumberValue, epsilon: Double): BooleanBinding = Bindings.equal(this, other, epsilon)
infix fun ObservableNumberValue.eq(near: Near): BooleanBinding = Bindings.equal(this, near.obs, near.epsilon)

fun ObservableNumberValue.equal(other: ObservableNumberValue): BooleanBinding = Bindings.equal(this, other)
infix fun ObservableNumberValue.eq(other: ObservableNumberValue): BooleanBinding = Bindings.equal(this, other)

fun ObservableNumberValue.equal(other: Long): BooleanBinding = Bindings.equal(this, other)
infix fun ObservableNumberValue.eq(other: Long): BooleanBinding = Bindings.equal(this, other)

fun Long.equal(other: ObservableNumberValue): BooleanBinding = Bindings.equal(this, other)
infix fun Long.eq(other: ObservableNumberValue): BooleanBinding = Bindings.equal(this, other)

fun ObservableNumberValue.equal(other: Int): BooleanBinding = Bindings.equal(this, other)
infix fun ObservableNumberValue.eq(other: Int): BooleanBinding = Bindings.equal(this, other)

fun Int.equal(other: ObservableNumberValue): BooleanBinding = Bindings.equal(this, other)
infix fun Int.eq(other: ObservableNumberValue): BooleanBinding = Bindings.equal(this, other)

// TODO: Add bindings for notEqual with epsilon
fun ObservableNumberValue.notEqual(other: ObservableNumberValue, epsilon: Double): BooleanBinding = Bindings.notEqual(this, other, epsilon)
infix fun ObservableNumberValue.ne(near: Near): BooleanBinding = Bindings.notEqual(this, near.obs, near.epsilon)

fun ObservableNumberValue.notEqual(other: ObservableNumberValue): BooleanBinding = Bindings.notEqual(this, other)
infix fun ObservableNumberValue.ne(other: ObservableNumberValue): BooleanBinding = Bindings.notEqual(this, other)

fun ObservableNumberValue.notEqual(other: Long): BooleanBinding = Bindings.notEqual(this, other)
infix fun ObservableNumberValue.ne(other: Long): BooleanBinding = Bindings.notEqual(this, other)

fun Long.notEqual(other: ObservableNumberValue): BooleanBinding = Bindings.notEqual(this, other)
infix fun Long.ne(other: ObservableNumberValue): BooleanBinding = Bindings.notEqual(this, other)

fun ObservableNumberValue.notEqual(other: Int): BooleanBinding = Bindings.notEqual(this, other)
infix fun ObservableNumberValue.ne(other: Int): BooleanBinding = Bindings.notEqual(this, other)

fun Int.notEqual(other: ObservableNumberValue): BooleanBinding = Bindings.notEqual(this, other)
infix fun Int.ne(other: ObservableNumberValue): BooleanBinding = Bindings.notEqual(this, other)

//
fun ObservableNumberValue.greaterThan(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)
infix fun ObservableNumberValue.gt(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)

fun ObservableNumberValue.greaterThan(other: Double): BooleanBinding = Bindings.greaterThan(this, other)
infix fun ObservableNumberValue.gt(other: Double): BooleanBinding = Bindings.greaterThan(this, other)

fun Double.greaterThan(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)
infix fun Double.gt(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)

fun ObservableNumberValue.greaterThan(other: Float): BooleanBinding = Bindings.greaterThan(this, other)
infix fun ObservableNumberValue.gt(other: Float): BooleanBinding = Bindings.greaterThan(this, other)

fun Float.greaterThan(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)
infix fun Float.gt(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)

fun ObservableNumberValue.greaterThan(other: Long): BooleanBinding = Bindings.greaterThan(this, other)
infix fun ObservableNumberValue.gt(other: Long): BooleanBinding = Bindings.greaterThan(this, other)

fun Long.greaterThan(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)
infix fun Long.gt(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)

fun ObservableNumberValue.greaterThan(other: Int): BooleanBinding = Bindings.greaterThan(this, other)
infix fun ObservableNumberValue.gt(other: Int): BooleanBinding = Bindings.greaterThan(this, other)

fun Int.greaterThan(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)
infix fun Int.gt(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThan(this, other)

//
fun ObservableNumberValue.lessThan(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)
infix fun ObservableNumberValue.lt(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)

fun ObservableNumberValue.lessThan(other: Double): BooleanBinding = Bindings.lessThan(this, other)
infix fun ObservableNumberValue.lt(other: Double): BooleanBinding = Bindings.lessThan(this, other)

fun Double.lessThan(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)
infix fun Double.lt(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)

fun ObservableNumberValue.lessThan(other: Float): BooleanBinding = Bindings.lessThan(this, other)
infix fun ObservableNumberValue.lt(other: Float): BooleanBinding = Bindings.lessThan(this, other)

fun Float.lessThan(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)
infix fun Float.lt(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)

fun ObservableNumberValue.lessThan(other: Long): BooleanBinding = Bindings.lessThan(this, other)
infix fun ObservableNumberValue.lt(other: Long): BooleanBinding = Bindings.lessThan(this, other)

fun Long.lessThan(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)
infix fun Long.lt(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)

fun ObservableNumberValue.lessThan(other: Int): BooleanBinding = Bindings.lessThan(this, other)
infix fun ObservableNumberValue.lt(other: Int): BooleanBinding = Bindings.lessThan(this, other)

fun Int.lessThan(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)
infix fun Int.lt(other: ObservableNumberValue): BooleanBinding = Bindings.lessThan(this, other)

//
fun ObservableNumberValue.greaterThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun ObservableNumberValue.ge(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

fun ObservableNumberValue.greaterThanOrEqual(other: Double): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun ObservableNumberValue.ge(other: Double): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

fun Double.greaterThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun Double.ge(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

fun ObservableNumberValue.greaterThanOrEqual(other: Float): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun ObservableNumberValue.ge(other: Float): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

fun Float.greaterThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun Float.ge(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

fun ObservableNumberValue.greaterThanOrEqual(other: Long): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun ObservableNumberValue.ge(other: Long): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

fun Long.greaterThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun Long.ge(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

fun ObservableNumberValue.greaterThanOrEqual(other: Int): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun ObservableNumberValue.ge(other: Int): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

fun Int.greaterThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)
infix fun Int.ge(other: ObservableNumberValue): BooleanBinding = Bindings.greaterThanOrEqual(this, other)

//
fun ObservableNumberValue.lessThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun ObservableNumberValue.le(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)

fun ObservableNumberValue.lessThanOrEqual(other: Double): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun ObservableNumberValue.le(other: Double): BooleanBinding = Bindings.lessThanOrEqual(this, other)

fun Double.lessThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun Double.le(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)

fun ObservableNumberValue.lessThanOrEqual(other: Float): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun ObservableNumberValue.le(other: Float): BooleanBinding = Bindings.lessThanOrEqual(this, other)

fun Float.lessThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun Float.le(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)

fun ObservableNumberValue.lessThanOrEqual(other: Long): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun ObservableNumberValue.le(other: Long): BooleanBinding = Bindings.lessThanOrEqual(this, other)

fun Long.lessThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun Long.le(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)

fun ObservableNumberValue.lessThanOrEqual(other: Int): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun ObservableNumberValue.le(other: Int): BooleanBinding = Bindings.lessThanOrEqual(this, other)

fun Int.lessThanOrEqual(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)
infix fun Int.le(other: ObservableNumberValue): BooleanBinding = Bindings.lessThanOrEqual(this, other)

//
