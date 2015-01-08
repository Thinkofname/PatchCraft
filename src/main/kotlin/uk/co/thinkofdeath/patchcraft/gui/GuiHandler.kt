package uk.co.thinkofdeath.patchcraft.gui

import minecraft.GuiScreen
import kotlin.platform.platformStatic

object GuiHandler {

    platformStatic fun init(screen: GuiScreen) {
        screen.addButton(99, 10, 10, 60, 20, "Hello")
        screen.addButton(100, 10, 40, 60, 20, "Hello world")

        screen.addButton(101, screen.getScreenWidth() - 70, screen.getScreenHeight() - 30, 60, 20, "Hello world")
    }

    platformStatic fun onClick(screen: GuiScreen, id: Int) {
        println(id)
    }
}

