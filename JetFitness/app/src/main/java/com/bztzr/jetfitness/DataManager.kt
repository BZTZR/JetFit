package com.bztzr.jetfitness

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

val Context.dataStore by preferencesDataStore(name = "fitness_prefs")

class DataManager(private val context: Context) {

    private val TOTAL_STEPS_KEY = intPreferencesKey("total_steps_today")
    private val LAST_DATE_KEY = stringPreferencesKey("last_date")
    private val HISTORY_LIST_KEY = stringPreferencesKey("history_list")
    private val TODAY_SESSIONS_KEY = stringPreferencesKey("today_sessions")
    private val USER_WEIGHT_KEY = floatPreferencesKey("user_weight")
    private val USER_HEIGHT_KEY = floatPreferencesKey("user_height")

    val totalStepsFlow: Flow<Int> = context.dataStore.data.map { it[TOTAL_STEPS_KEY] ?: 0 }

    val todaySessionsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val s = prefs[TODAY_SESSIONS_KEY] ?: ""
        if (s.isEmpty()) emptyList() else s.split("|").filter { it.isNotEmpty() }
    }

    val historyFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val h = prefs[HISTORY_LIST_KEY] ?: ""
        if (h.isEmpty()) emptyList()
        else h.split("|||").filter { it.isNotEmpty() }
    }

    val userWeightFlow: Flow<Float> = context.dataStore.data.map { it[USER_WEIGHT_KEY] ?: 70f }
    val userHeightFlow: Flow<Float> = context.dataStore.data.map { it[USER_HEIGHT_KEY] ?: 175f }

    val isConfiguredFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[USER_WEIGHT_KEY] != null && prefs[USER_HEIGHT_KEY] != null
    }

    suspend fun initDay() {
        val today = getCurrentDate()
        context.dataStore.edit { prefs ->
            val lastDate = prefs[LAST_DATE_KEY]

            if (lastDate != today) {
                if (lastDate != null) {
                    val totalSteps = prefs[TOTAL_STEPS_KEY] ?: 0

                    if (totalSteps > 0) {
                        val dist = (totalSteps * 0.7) / 1000.0
                        val cals = (totalSteps * 0.04).toInt()

                        val entry = String.format("%s: %d шагов, %.2f км, %d ккал", lastDate, totalSteps, dist, cals)

                        val oldHistory = prefs[HISTORY_LIST_KEY] ?: ""

                        val newHistory = if (oldHistory.isEmpty()) entry else "$oldHistory|||$entry"

                        prefs[HISTORY_LIST_KEY] = newHistory
                    }
                }

                prefs[TOTAL_STEPS_KEY] = 0
                prefs[TODAY_SESSIONS_KEY] = ""
                prefs[LAST_DATE_KEY] = today
            }
        }
    }

    suspend fun finishSession(steps: Int, weight: Float, height: Float) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val stepLengthMeters = (height * 0.415) / 100.0
        val distKm = (steps * stepLengthMeters) / 1000.0
        val cals = (0.5 * weight * distKm).toInt()

        val sessionEntry = String.format("%s - %d шагов (%.2f км, %d ккал)", time, steps, distKm, cals)

        context.dataStore.edit { prefs ->
            val currentTotal = prefs[TOTAL_STEPS_KEY] ?: 0
            prefs[TOTAL_STEPS_KEY] = currentTotal + steps

            val currentSessions = prefs[TODAY_SESSIONS_KEY] ?: ""
            val newSessions = if (currentSessions.isEmpty()) sessionEntry else "$currentSessions|$sessionEntry"
            prefs[TODAY_SESSIONS_KEY] = newSessions
        }
    }

    suspend fun saveUserSettings(weight: Float, height: Float) {
        context.dataStore.edit { prefs ->
            prefs[USER_WEIGHT_KEY] = weight
            prefs[USER_HEIGHT_KEY] = height
        }
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    }
}