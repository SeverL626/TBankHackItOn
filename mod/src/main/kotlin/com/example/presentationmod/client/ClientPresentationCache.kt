package com.example.presentationmod.client

import com.example.presentationmod.PresentationMod
import com.example.presentationmod.util.PresentationLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.fml.loading.FMLPaths
import java.awt.image.BufferedImage
import java.io.File

@OnlyIn(Dist.CLIENT)
object ClientPresentationCache {

    private var slides: List<BufferedImage> = emptyList()
    private var loadedFileName: String? = null

    fun invalidate() {
        slides = emptyList()
        loadedFileName = null
        SlideTextureCache.clear()
    }

    fun preload(fileName: String?) {
        if (fileName == null) return
        ensureLoaded(fileName)
    }

    fun getSlide(fileName: String, index: Int): BufferedImage? {
        ensureLoaded(fileName)
        if (slides.isEmpty()) return null
        return slides[index.mod(slides.size)]
    }

    private fun ensureLoaded(fileName: String) {
        if (loadedFileName == fileName) return
        loadedFileName = fileName

        val file = File(presentationsDir(), fileName)
        slides = if (file.exists()) PresentationLoader.loadPresentation(file) else emptyList()

        if (slides.isEmpty()) {
            Minecraft.getInstance().player?.displayClientMessage(Component.literal("Failed to load slides: $fileName"), true)
            PresentationMod.LOGGER.warn("Failed to load slides from {}", file.absolutePath)
        } else {
            Minecraft.getInstance().player?.displayClientMessage(Component.literal("Loaded slides: ${slides.size}"), true)
        }
    }

    private fun presentationsDir(): File =
        FMLPaths.GAMEDIR.get().resolve("config/presentationmod/presentations").toFile().also {
            if (!it.exists()) it.mkdirs()
        }
}