package com.bztzr.jetfitness

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class TrackerViewModel(private val dataManager: DataManager) : ViewModel() {

    var isTracking by mutableStateOf(false)
        private set

    var sessionSteps by mutableStateOf(0)
        private set

    var initialSensorValue by mutableStateOf(0)
        private set

    val sessionDistance: Double
        get() = (sessionSteps * 0.7) / 1000.0

    val sessionCalories: Int
        get() = (sessionSteps * 0.04).toInt()

    fun startTracking() {
        isTracking = true
        initialSensorValue = 0
        sessionSteps = 0
    }

    fun updateSessionSteps(currentSensorValue: Int) {
        if (!isTracking) return

        if (initialSensorValue == 0 && currentSensorValue > 0) {
            initialSensorValue = currentSensorValue
        }

        if (initialSensorValue > 0) {
            sessionSteps = currentSensorValue - initialSensorValue
        }
    }

    fun stopTracking(weight: Float, height: Float) {
        if (!isTracking) return

        isTracking = false

        if (sessionSteps > 0) {
            viewModelScope.launch {
                dataManager.finishSession(sessionSteps, weight, height)
            }
        }
    }
}