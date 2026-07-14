package com.dmvp.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val LOCAL_EVIDENCE_STORE_NAME = "dmvp_local_evidence"
private val Context.localEvidenceDataStore by preferencesDataStore(
    name = LOCAL_EVIDENCE_STORE_NAME
)

object LocalEvidenceStore {

    private val KEY_REGISTERED_EVIDENCE_IDS =
        stringSetPreferencesKey("registered_evidence_ids")

    private val KEY_LAST_EVIDENCE_ID =
        stringPreferencesKey("last_evidence_id")

    suspend fun saveEvidenceId(context: Context, evidenceId: String) {
        context.localEvidenceDataStore.edit { prefs ->
            val current = prefs[KEY_REGISTERED_EVIDENCE_IDS] ?: emptySet()
            prefs[KEY_REGISTERED_EVIDENCE_IDS] = current + evidenceId
            prefs[KEY_LAST_EVIDENCE_ID] = evidenceId
        }
    }

    suspend fun getRegisteredEvidenceIds(context: Context): Set<String> {
        return context.localEvidenceDataStore.data
            .map { prefs -> prefs[KEY_REGISTERED_EVIDENCE_IDS] ?: emptySet() }
            .first()
    }

    suspend fun getLastEvidenceId(context: Context): String? {
        return context.localEvidenceDataStore.data
            .map { prefs -> prefs[KEY_LAST_EVIDENCE_ID] }
            .first()
    }
}
