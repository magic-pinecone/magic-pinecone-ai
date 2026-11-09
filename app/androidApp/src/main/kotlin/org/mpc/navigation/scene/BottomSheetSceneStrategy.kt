/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file has been modified for Magic Pinecone.
 */

package org.mpc.navigation.scene

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Adapted from the official Android Navigation 3 Bottom Sheet recipe:
 * https://developer.android.com/guide/navigation/navigation-3/recipes/bottomsheet
 *
 * The upstream recipe currently includes `private val onBack: () -> Unit,` in this data class,
 * while the general Scenes guidance says: "Generally, avoid including callbacks (like onBack) in
 * the implementation of these methods." Keep this callback for dismissal behavior, but exclude it
 * from scene identity, matching the built-in DialogScene implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val lifecycleOwner = rememberLifecycleOwner()
        ModalBottomSheet(
            onDismissRequest = onBack,
            properties = modalBottomSheetProperties,
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                entry.Content()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BottomSheetScene<*>

        return key == other.key &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            entry == other.entry &&
            modalBottomSheetProperties == other.modalBottomSheetProperties
    }

    override fun hashCode(): Int = key.hashCode() * 31 +
        previousEntries.hashCode() * 31 +
        overlaidEntries.hashCode() * 31 +
        entry.hashCode() * 31 +
        modalBottomSheetProperties.hashCode() * 31
}

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val properties = lastEntry.metadata[BottomSheetKey] ?: return null

        @Suppress("UNCHECKED_CAST")
        return BottomSheetScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            modalBottomSheetProperties = properties,
            onBack = onBack,
        )
    }

    companion object {
        fun bottomSheet(
            properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
        ) = metadata {
            put(BottomSheetKey, properties)
        }

        private object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>
    }
}
