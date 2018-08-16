/*
 * Copyright (c) 2018. Robert Jaros
 */

package com.example

import com.example.App.addTask
import com.example.App.removeTask
import pl.treksoft.kvision.core.Component
import pl.treksoft.kvision.core.CssSize
import pl.treksoft.kvision.core.UNIT
import pl.treksoft.kvision.core.Widget
import pl.treksoft.kvision.utils.px
import pl.treksoft.kvision.window.Window
import kotlin.js.Math.random

open class DesktopWindow(caption: String, width: Int, height: Int) :
    Window(caption, width.px, height.px, closeButton = true) {

    override var top: CssSize? = null
        set(value) {
            if (value?.first ?: 0 > 50 && value?.second == UNIT.px) {
                field = value
            }
        }

    val task: Component

    init {
        left = ((random() * 800).toInt()).px
        top = (51 + (random() * 100).toInt()).px
        task = addTask(caption, this)
    }

    override fun hide(): Widget {
        super.hide()
        removeTask(task)
        this.dispose()
        return this
    }
}
