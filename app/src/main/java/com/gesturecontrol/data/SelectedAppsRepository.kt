package com.gesturecontrol.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectedAppsRepository @Inject constructor(private val dao: SelectedAppDao) {

    val selectedPackages: Flow<Set<String>> = dao.observePackages().map { it.toSet() }

    suspend fun setSelected(packageName: String, selected: Boolean) {
        if (selected) dao.insert(SelectedApp(packageName)) else dao.delete(packageName)
    }
}
