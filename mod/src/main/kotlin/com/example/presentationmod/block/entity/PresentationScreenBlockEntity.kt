package com.example.presentationmod.block.entity

import com.example.presentationmod.network.ModNetwork
import com.example.presentationmod.registry.ModRegistries
import com.example.presentationmod.util.ServerFileUtil
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.io.File

class PresentationScreenBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModRegistries.PRESENTATION_SCREEN_BE.get(), pos, state) {

    var fileName: String? = null
        private set

    var currentSlideIndex: Int = 0
        private set

    fun hasPresentation(): Boolean = fileName != null

    fun loadFromConfig(rawFileName: String): Boolean {
        val server = level?.server ?: return false
        val cleanName = sanitizeFileName(rawFileName) ?: return false
        val root = ServerFileUtil.presentationsRoot(server.serverDirectory)
        val file = File(root, cleanName)
        if (!file.isFile) return false

        fileName = cleanName
        currentSlideIndex = 0
        syncToClients()
        return true
    }

    fun nextSlide() {
        if (!hasPresentation()) return
        currentSlideIndex += 1
        syncToClients()
    }

    fun prevSlide() {
        if (!hasPresentation()) return
        currentSlideIndex = (currentSlideIndex - 1).coerceAtLeast(0)
        syncToClients()
    }

    private fun sanitizeFileName(raw: String): String? {
        val normalized = raw.trim().replace('\\', '/')
        val tail = normalized.substringAfterLast('/')
        if (tail.isBlank()) return null
        if (tail.contains("..")) return null
        return tail
    }

    private fun syncToClients() {
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
        val serverLevel = level as? net.minecraft.server.level.ServerLevel ?: return
        ModNetwork.sendSlideSync(serverLevel, worldPosition, fileName, currentSlideIndex)
    }

    fun applyClientSync(newFileName: String?, newIndex: Int) {
        val lvl = level ?: return
        if (!lvl.isClientSide) return

        fileName = newFileName
        currentSlideIndex = newIndex.coerceAtLeast(0)
        com.example.presentationmod.client.ClientPresentationCache.invalidate()
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putInt("Slide", currentSlideIndex)
        fileName?.let { tag.putString("File", it) }
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        currentSlideIndex = tag.getInt("Slide").coerceAtLeast(0)
        fileName = if (tag.contains("File")) tag.getString("File") else null
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket =
        ClientboundBlockEntityDataPacket.create(this)

    override fun handleUpdateTag(tag: CompoundTag) {
        load(tag)
    }
}