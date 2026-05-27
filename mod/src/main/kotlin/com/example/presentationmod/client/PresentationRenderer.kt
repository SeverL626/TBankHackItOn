package com.example.presentationmod.client

import com.example.presentationmod.block.entity.PresentationScreenBlockEntity
import com.example.presentationmod.util.PresentationScreenHelper
import java.awt.image.BufferedImage

object PresentationRenderer {

    fun getSlide(be: PresentationScreenBlockEntity): BufferedImage? {
        val level = be.level ?: return null
        val controller = PresentationScreenHelper.controllerEntity(level, be.blockPos) ?: return null
        val file = controller.fileName ?: return null
        return ClientPresentationCache.getSlide(file, controller.currentSlideIndex)
    }
}
