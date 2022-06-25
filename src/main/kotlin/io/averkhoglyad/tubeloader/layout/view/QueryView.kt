package io.averkhoglyad.tubeloader.layout.view

import io.averkhoglyad.tubeloader.util.CallbackFn
import io.averkhoglyad.tubeloader.util.fontawesome
import io.averkhoglyad.tubeloader.util.noCallback
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.input.KeyCode
import org.controlsfx.glyphfont.FontAwesome
import tornadofx.*

class QueryView : View() {

    private val searchField = SimpleStringProperty()

    private val searchingProperty = SimpleBooleanProperty()
    var searching by searchingProperty

    private var onSearchFn: CallbackFn<String> = noCallback

    override val root = vbox {
        hbox(5.0) {
            paddingTop = 10.0
            paddingLeft = 5.0
            paddingRight = 5.0
            textfield(searchField) {
                promptText = "Enter URL or ID"
                disableWhen(searchingProperty)
                prefWidthProperty().bind(this@hbox.widthProperty() - 40.0)
                setOnKeyPressed {
                    if (it.code == KeyCode.ENTER) {
                        search()
                    }
                }
            }
            button {
                graphic = fontawesome(FontAwesome.Glyph.SEARCH)
                disableWhen(searchField.isEmpty)
                prefWidth = 25.0
                action {
                    search()
                }
            }
        }
        progressbar(-1.0) {
            visibleWhen { searchingProperty }
            useMaxWidth = true
            prefHeight = 10.0
        }
    }

    private fun search() {
        fire(QueryEvent(searchField.get()))
        onSearchFn(searchField.get())
        searchField.set("")
    }

    fun onSearch(fn: CallbackFn<String>) {
        onSearchFn = fn
    }
}

data class QueryEvent(val query: String) : FXEvent()
