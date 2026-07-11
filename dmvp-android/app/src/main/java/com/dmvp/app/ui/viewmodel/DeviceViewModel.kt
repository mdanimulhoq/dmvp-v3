/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/DeviceViewModel.kt
 *
 * ViewModel for the Device Management screen.
 */

package com.dmvp.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.*
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.data.repository.Result
import com.dmvp.app.security.DeviceKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DeviceViewModel"

enum class DeviceOperation {
    NONE, VIEWING, ROTATING, REVOKING, RECOVERING, LISTING
}

data class DeviceUiState(
    val isLoading: Boolean = false,
    val currentDeviceKeyId: String? = null,
    val currentPublicKey: String? = null,
    val currentTrustTier: String? = null,
    val isHardwareBacked: Boolean = false,
    val attestationAvailable: Boolean = false,
    val attestationSummary: AttestationSummary? = null,
    val deviceList: List<DeviceKey> = emptyList(),
    val deviceListTotal: Int = 0,
    val selectedDeviceKey: DeviceKey? = null,
    val operation: DeviceOperation = DeviceOperation.NONE,
    val newDeviceKeyId: String = "",
    val newPublicKey: String = "",
    val rotationResult: DeviceKey? = null,
    val revocationResult: DeviceKey? = null,
    val recoveryResult: DeviceKey? = null,
    val recoveryOldDeviceKeyId: String = "",
    val recoveryQuorum: String = "",
    val error: String? = null,
    val errorCode: String? = null,
    val successMessage: String? = null,
    val showConfirmDialog: Boolean = false,
    val progress: Float = 0f
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        loadDeviceInfo()
    }

    fun loadDeviceInfo() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null, errorCode = null) }

                val deviceKeyId = repository.getDeviceKeyId() // placeholder
                val publicKey = repository.getPublicKey()
                val trustTier = repository.getCachedTrustTier()
                val isHardwareBacked = DeviceKeyManager.isHardwareBacked()
                val attestationSummary = DeviceKeyManager.getAttestationSummary()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentDeviceKeyId = deviceKeyId,
                        currentPublicKey = publicKey,
                        currentTrustTier = trustTier,
                        isHardwareBacked = isHardwareBacked,
                        attestationAvailable = attestationSummary.isNotEmpty(),
                        attestationSummary = if (attestationSummary.isNotEmpty()) {
                            AttestationSummary(
                                valid = attestationSummary["valid"] as? Boolean ?: false,
                                hardwareBacked = attestationSummary["hardware_backed"] as? Boolean ?: false,
                                platform = attestationSummary["platform"] as? String ?: "unknown",
                                appIntegrity = attestationSummary["appIntegrity"] as? Boolean ?: false,
                                rooted = attestationSummary["rooted"] as? Boolean ?: false,
                                extra = attestationSummary.filterKeys { it !in listOf("valid", "hardware_backed", "platform", "appIntegrity", "rooted") }
                            )
                        } else null
                    )
                }
                loadDeviceList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load device info", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load device info",
                        errorCode = "LOAD_ERROR"
                    )
                }
            }
        }
    }

    fun loadDeviceList() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val result = repository.listDeviceKeys(page = 1, limit = 100)
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                deviceList = result.data.items,
                                deviceListTotal = result.data.total,
                                error = null,
                                errorCode = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Failed to load device list",
                                errorCode = result.errorCode ?: "LIST_ERROR"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load device list", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load device list",
                        errorCode = "LIST_ERROR"
                    )
                }
            }
        }
    }

    fun selectDeviceKey(deviceKeyId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val result = repository.getDeviceKeyInfo(deviceKeyId)
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                selectedDeviceKey = result.data,
                                operation = DeviceOperation.VIEWING,
                                error = null,
                                errorCode = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Failed to get device key info",
                                errorCode = result.errorCode ?: "GET_ERROR"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get device key info", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to get device key info",
                        errorCode = "GET_ERROR"
                    )
                }
            }
        }
    }

    fun startRotation() {
        val currentId = uiState.value.currentDeviceKeyId
        if (currentId == null) {
            _uiState.update {
                it.copy(error = "No current device key to rotate", errorCode = "INVALID_STATE")
            }
            return
        }
        _uiState.update {
            it.copy(
                operation = DeviceOperation.ROTATING,
                newDeviceKeyId = "device_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}",
                error = null,
                errorCode = null,
                successMessage = null,
                rotationResult = null
            )
        }
    }

    fun setNewDeviceKeyId(id: String) {
        _uiState.update { it.copy(newDeviceKeyId = id) }
    }

    fun executeRotation() {
        val state = uiState.value
        val oldKeyId = state.currentDeviceKeyId
        val newKeyId = state.newDeviceKeyId
        if (oldKeyId == null || newKeyId.isEmpty()) {
            _uiState.update {
                it.copy(error = "Missing device key information", errorCode = "VALIDATION_ERROR")
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        progress = 0.1f,
                        error = null,
                        errorCode = null,
                        showConfirmDialog = false
                    )
                }
                val newPublicKey = DeviceKeyManager.getPublicKey()
                if (newPublicKey == null) {
                    throw Exception("Failed to get public key for new device")
                }
                val attestationSummary = DeviceKeyManager.getAttestationSummary()
                val attestation = AttestationSummary(
                    valid = attestationSummary["valid"] as? Boolean ?: true,
                    hardwareBacked = attestationSummary["hardware_backed"] as? Boolean ?: false,
                    platform = "android",
                    appIntegrity = true,
                    rooted = false,
                    extra = attestationSummary.filterKeys { it !in listOf("valid", "hardware_backed", "platform", "appIntegrity", "rooted") }
                )
                _uiState.update { it.copy(progress = 0.4f) }

                val result = repository.rotateDeviceKey(
                    newDeviceKeyId = newKeyId,
                    newPublicKey = newPublicKey,
                    attestationSummary = attestation
                )
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                rotationResult = result.data,
                                currentDeviceKeyId = result.data.deviceKeyId,
                                currentTrustTier = result.data.trustTier.name,
                                operation = DeviceOperation.NONE,
                                progress = 1f,
                                successMessage = "Device key rotated successfully to $newKeyId",
                                error = null,
                                errorCode = null
                            )
                        }
                        loadDeviceInfo()
                        Log.i(TAG, "Device rotated successfully: $newKeyId")
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Rotation failed",
                                errorCode = result.errorCode ?: "ROTATION_FAILED",
                                operation = DeviceOperation.ROTATING
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rotation failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Rotation failed",
                        errorCode = "ROTATION_ERROR",
                        operation = DeviceOperation.ROTATING
                    )
                }
            }
        }
    }

    fun startRevocation() {
        val currentId = uiState.value.currentDeviceKeyId
        if (currentId == null) {
            _uiState.update {
                it.copy(error = "No current device key to revoke", errorCode = "INVALID_STATE")
            }
            return
        }
        _uiState.update {
            it.copy(
                operation = DeviceOperation.REVOKING,
                showConfirmDialog = true,
                error = null,
                errorCode = null,
                successMessage = null,
                revocationResult = null
            )
        }
    }

    fun executeRevocation() {
        val deviceKeyId = uiState.value.currentDeviceKeyId ?: return
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        errorCode = null,
                        showConfirmDialog = false
                    )
                }
                val result = repository.revokeDeviceKey(deviceKeyId)
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                revocationResult = result.data,
                                operation = DeviceOperation.NONE,
                                successMessage = "Device key revoked successfully",
                                error = null,
                                errorCode = null
                            )
                        }
                        if (deviceKeyId == uiState.value.currentDeviceKeyId) {
                            _uiState.update {
                                it.copy(
                                    currentDeviceKeyId = null,
                                    currentPublicKey = null,
                                    currentTrustTier = null
                                )
                            }
                        }
                        loadDeviceList()
                        Log.i(TAG, "Device revoked successfully: $deviceKeyId")
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Revocation failed",
                                errorCode = result.errorCode ?: "REVOCATION_FAILED",
                                operation = DeviceOperation.REVOKING
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Revocation failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Revocation failed",
                        errorCode = "REVOCATION_ERROR",
                        operation = DeviceOperation.REVOKING
                    )
                }
            }
        }
    }

    fun startRecovery() {
        _uiState.update {
            it.copy(
                operation = DeviceOperation.RECOVERING,
                recoveryOldDeviceKeyId = uiState.value.currentDeviceKeyId ?: "",
                recoveryQuorum = "",
                error = null,
                errorCode = null,
                successMessage = null,
                recoveryResult = null
            )
        }
    }

    fun setRecoveryOldDeviceKeyId(id: String) {
        _uiState.update { it.copy(recoveryOldDeviceKeyId = id) }
    }

    fun setRecoveryQuorum(quorum: String) {
        _uiState.update { it.copy(recoveryQuorum = quorum) }
    }

    fun executeRecovery() {
        val state = uiState.value
        val oldKeyId = state.recoveryOldDeviceKeyId
        val quorum = state.recoveryQuorum
        if (oldKeyId.isEmpty()) {
            _uiState.update {
                it.copy(error = "Old device key ID is required", errorCode = "VALIDATION_ERROR")
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        progress = 0.1f,
                        error = null,
                        errorCode = null
                    )
                }
                val newKeyId = "device_recovered_${System.currentTimeMillis()}"
                val newPublicKey = DeviceKeyManager.getPublicKey()
                if (newPublicKey == null) {
                    throw Exception("Failed to get public key for recovered device")
                }
                val attestationSummary = DeviceKeyManager.getAttestationSummary()
                val attestation = AttestationSummary(
                    valid = attestationSummary["valid"] as? Boolean ?: true,
                    hardwareBacked = attestationSummary["hardware_backed"] as? Boolean ?: false,
                    platform = "android",
                    appIntegrity = true,
                    rooted = false,
                    extra = attestationSummary.filterKeys { it !in listOf("valid", "hardware_backed", "platform", "appIntegrity", "rooted") }
                )
                _uiState.update { it.copy(progress = 0.4f) }

                val result = repository.recoverDeviceLineage(
                    oldDeviceKeyId = oldKeyId,
                    newDeviceKeyId = newKeyId,
                    newPublicKey = newPublicKey,
                    attestationSummary = attestation,
                    recoveryQuorum = quorum.ifEmpty { null }
                )
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                recoveryResult = result.data,
                                currentDeviceKeyId = result.data.deviceKeyId,
                                currentTrustTier = result.data.trustTier.name,
                                operation = DeviceOperation.NONE,
                                progress = 1f,
                                successMessage = "Device lineage recovered successfully to $newKeyId",
                                error = null,
                                errorCode = null
                            )
                        }
                        loadDeviceInfo()
                        Log.i(TAG, "Device recovered successfully: $newKeyId")
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Recovery failed",
                                errorCode = result.errorCode ?: "RECOVERY_FAILED",
                                operation = DeviceOperation.RECOVERING
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recovery failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Recovery failed",
                        errorCode = "RECOVERY_ERROR",
                        operation = DeviceOperation.RECOVERING
                    )
                }
            }
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun showConfirmation() {
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun resetOperation() {
        _uiState.update {
            it.copy(
                operation = DeviceOperation.NONE,
                showConfirmDialog = false,
                rotationResult = null,
                revocationResult = null,
                recoveryResult = null,
                error = null,
                errorCode = null,
                successMessage = null
            )
        }
    }

    fun refresh() {
        loadDeviceInfo()
    }
}

private fun DMVPRepository.getDeviceKeyId(): String? = null
private fun DMVPRepository.getPublicKey(): String? = null

fun DeviceUiState.isCurrentDeviceKey(keyId: String): Boolean = currentDeviceKeyId == keyId
fun DeviceUiState.getTrustTierDisplay(): String = currentTrustTier ?: "Unknown"
fun DeviceUiState.getTrustTierColor(): Int = when (currentTrustTier) {
    "TIER_A" -> 0xFF00E676.toInt()
    "TIER_B" -> 0xFFFFD740.toInt()
    "TIER_C" -> 0xFFFF6D00.toInt()
    "TIER_D" -> 0xFFE53935.toInt()
    else -> 0xFFFFFFFF.toInt()
}
fun DeviceUiState.getOperationInProgress(): Boolean = operation != DeviceOperation.NONE && isLoading
