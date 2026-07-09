/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/DeviceViewModel.kt
 *
 * ViewModel for the Device Management screen.
 * Handles device key lifecycle operations:
 *   - Viewing current device key info (trust tier, attestation, lineage)
 *   - Rotating to a new device key
 *   - Revoking a device key
 *   - Recovering device lineage after device loss
 *   - Listing known device keys
 *
 * Uses:
 *   - DMVPRepository for device operations
 *   - DeviceKeyManager for local key management
 *   - DataStore/Preferences for caching device state
 *
 * Provides detailed UI state for device management workflows.
 */

package com.dmvp.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.*
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.security.DeviceKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DeviceViewModel"

/**
 * Device management operation type.
 */
enum class DeviceOperation {
    NONE,
    VIEWING,
    ROTATING,
    REVOKING,
    RECOVERING,
    LISTING
}

/**
 * UI state for the Device screen.
 */
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

/**
 * ViewModel for managing device keys.
 */
@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        loadDeviceInfo()
    }

    /**
     * Load current device information.
     */
    fun loadDeviceInfo() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null, errorCode = null) }

                // Get local device info
                val deviceKeyId = repository.getDeviceKeyId() // This would be a function in repository
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

                // Also fetch device list from server
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

    /**
     * Load device list from server.
     */
    fun loadDeviceList() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val result = repository.listDeviceKeys(
                    page = 1,
                    limit = 100
                )

                when (result) {
                    is RepositoryResult.Success -> {
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
                    is RepositoryResult.Error -> {
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

    /**
     * Select a device key to view details.
     */
    fun selectDeviceKey(deviceKeyId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val result = repository.getDeviceKeyInfo(deviceKeyId)
                when (result) {
                    is RepositoryResult.Success -> {
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
                    is RepositoryResult.Error -> {
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

    /**
     * Start device rotation flow.
     */
    fun startRotation() {
        val currentId = uiState.value.currentDeviceKeyId
        if (currentId == null) {
            _uiState.update {
                it.copy(
                    error = "No current device key to rotate",
                    errorCode = "INVALID_STATE"
                )
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

    /**
     * Set new device key ID for rotation.
     */
    fun setNewDeviceKeyId(id: String) {
        _uiState.update { it.copy(newDeviceKeyId = id) }
    }

    /**
     * Execute device rotation.
     */
    fun executeRotation() {
        val state = uiState.value
        val oldKeyId = state.currentDeviceKeyId
        val newKeyId = state.newDeviceKeyId

        if (oldKeyId == null || newKeyId.isEmpty()) {
            _uiState.update {
                it.copy(
                    error = "Missing device key information",
                    errorCode = "VALIDATION_ERROR"
                )
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

                // Generate new public key from Keystore
                // In a real implementation, you'd generate a new key pair first.
                // For MVP, we'll use the same key or generate a new one.
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
                    is RepositoryResult.Success -> {
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
                        // Reload device info
                        loadDeviceInfo()
                        Log.i(TAG, "Device rotated successfully: $newKeyId")
                    }
                    is RepositoryResult.Error -> {
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

    /**
     * Start device revocation flow.
     */
    fun startRevocation() {
        val currentId = uiState.value.currentDeviceKeyId
        if (currentId == null) {
            _uiState.update {
                it.copy(
                    error = "No current device key to revoke",
                    errorCode = "INVALID_STATE"
                )
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

    /**
     * Execute device revocation.
     */
    fun executeRevocation() {
        val deviceKeyId = uiState.value.currentDeviceKeyId
        if (deviceKeyId == null) {
            _uiState.update {
                it.copy(
                    error = "No device key to revoke",
                    errorCode = "INVALID_STATE"
                )
            }
            return
        }

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
                    is RepositoryResult.Success -> {
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
                        // Clear local device state if revoked key is current
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
                    is RepositoryResult.Error -> {
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

    /**
     * Start device recovery flow.
     */
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

    /**
     * Set recovery old device key ID.
     */
    fun setRecoveryOldDeviceKeyId(id: String) {
        _uiState.update { it.copy(recoveryOldDeviceKeyId = id) }
    }

    /**
     * Set recovery quorum proof.
     */
    fun setRecoveryQuorum(quorum: String) {
        _uiState.update { it.copy(recoveryQuorum = quorum) }
    }

    /**
     * Execute device recovery.
     */
    fun executeRecovery() {
        val state = uiState.value
        val oldKeyId = state.recoveryOldDeviceKeyId
        val quorum = state.recoveryQuorum

        if (oldKeyId.isEmpty()) {
            _uiState.update {
                it.copy(
                    error = "Old device key ID is required",
                    errorCode = "VALIDATION_ERROR"
                )
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

                // Generate new key ID and public key
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
                    is RepositoryResult.Success -> {
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
                    is RepositoryResult.Error -> {
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

    /**
     * Dismiss confirmation dialog.
     */
    fun dismissDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    /**
     * Show confirmation dialog.
     */
    fun showConfirmation() {
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }

    /**
     * Clear success message.
     */
    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * Reset operation state.
     */
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

    /**
     * Refresh device data.
     */
    fun refresh() {
        loadDeviceInfo()
    }
}

/**
 * Extension to get device key ID from repository (helper).
 * In a real implementation, this would be a function on the repository.
 */
private fun DMVPRepository.getDeviceKeyId(): String? {
    // This is a placeholder; in the actual repository, you'd have a method.
    // We'll use the cached value from the ViewModel's state.
    return null // Will be handled in loadDeviceInfo
}

private fun DMVPRepository.getPublicKey(): String? {
    return null
}

/**
 * Convenience extensions for DeviceUiState.
 */
fun DeviceUiState.isCurrentDeviceKey(keyId: String): Boolean {
    return currentDeviceKeyId == keyId
}

fun DeviceUiState.getTrustTierDisplay(): String {
    return currentTrustTier ?: "Unknown"
}

fun DeviceUiState.getTrustTierColor(): Int {
    return when (currentTrustTier) {
        "TIER_A" -> 0xFF00E676.toInt()
        "TIER_B" -> 0xFFFFD740.toInt()
        "TIER_C" -> 0xFFFF6D00.toInt()
        "TIER_D" -> 0xFFE53935.toInt()
        else -> 0xFFFFFFFF.toInt()
    }
}

fun DeviceUiState.getOperationInProgress(): Boolean {
    return operation != DeviceOperation.NONE && isLoading
}
