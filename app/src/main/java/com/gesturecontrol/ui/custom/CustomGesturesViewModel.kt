package com.gesturecontrol.ui.custom

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.data.CustomGestureEntity
import com.gesturecontrol.data.CustomGestureRepository
import com.gesturecontrol.data.GLOBAL_PROFILE
import com.gesturecontrol.data.InstalledAppsRepository
import com.gesturecontrol.data.SelectedAppsRepository
import com.gesturecontrol.gestures.ml.LandmarkNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class ProfileItem(val key: String, val label: String, val count: Int)

/** Result messages of profile operations, shown as snackbars. */
enum class ProfileMessage { EXPORTED, IMPORTED, IMPORT_FAILED, IMPORT_WRONG_VERSION, DUPLICATED, RESET }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomGesturesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CustomGestureRepository,
    selectedApps: SelectedAppsRepository,
    private val installedApps: InstalledAppsRepository,
) : ViewModel() {

    val selectedProfile = MutableStateFlow(GLOBAL_PROFILE)

    private val _messages = MutableSharedFlow<ProfileMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<ProfileMessage> = _messages

    val profiles: StateFlow<List<ProfileItem>> =
        combine(selectedApps.selectedPackages, repository.counts) { selected, counts ->
            listOf(ProfileItem(GLOBAL_PROFILE, "", counts[GLOBAL_PROFILE] ?: 0)) +
                (selected + counts.keys.filter { it != GLOBAL_PROFILE })
                    .distinct()
                    .map { ProfileItem(it, installedApps.labelOf(it), counts[it] ?: 0) }
                    .sortedBy { it.label.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val gestures: StateFlow<List<CustomGestureEntity>> = selectedProfile
        .flatMapLatest { repository.gestures(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun appLabel(packageName: String) = installedApps.labelOf(packageName)

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    fun reset() = viewModelScope.launch {
        repository.reset(selectedProfile.value)
        _messages.emit(ProfileMessage.RESET)
    }

    fun duplicateTo(target: String) = viewModelScope.launch {
        repository.duplicate(selectedProfile.value, target)
        _messages.emit(ProfileMessage.DUPLICATED)
    }

    fun export(uri: Uri) = viewModelScope.launch {
        val gestures = repository.export(selectedProfile.value)
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("norm", LandmarkNormalizer.VERSION)
            .put("profile", selectedProfile.value)
            .put("gestures", JSONArray(gestures.map { g ->
                JSONObject()
                    .put("name", g.name)
                    .put("samples", g.samples)
                    .put("radius", g.radius.toDouble())
                    .put("sensitivity", g.sensitivity.toDouble())
                    .put("cooldownMs", g.cooldownMs)
                    .put("action", g.action)
            }))
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json.toString(2)) }
        }
        _messages.emit(ProfileMessage.EXPORTED)
    }

    /** Imports into the selected profile (gestures are added, nothing is replaced). */
    fun import(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }
            val json = JSONObject(text)
            require(json.getString("format") == FORMAT)
            if (json.getString("norm") != LandmarkNormalizer.VERSION) return@runCatching ProfileMessage.IMPORT_WRONG_VERSION
            val array = json.getJSONArray("gestures")
            val now = System.currentTimeMillis()
            val entities = List(array.length()) { i ->
                val g = array.getJSONObject(i)
                CustomGestureEntity(
                    profile = selectedProfile.value,
                    name = g.getString("name"),
                    samples = g.getString("samples"),
                    radius = g.getDouble("radius").toFloat(),
                    sensitivity = g.getDouble("sensitivity").toFloat(),
                    cooldownMs = g.getLong("cooldownMs"),
                    action = g.getString("action"),
                    createdAt = now + i,
                )
            }
            repository.import(selectedProfile.value, entities)
            ProfileMessage.IMPORTED
        }
        _messages.emit(result.getOrDefault(ProfileMessage.IMPORT_FAILED))
    }

    companion object {
        const val FORMAT = "gesturecontrol-gestures"
    }
}
