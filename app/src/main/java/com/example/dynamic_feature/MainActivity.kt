package com.example.dynamic_feature

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dynamic_feature.ui.theme.DynamicfeatureTheme
import com.google.android.play.core.ktx.moduleNames
import com.google.android.play.core.ktx.requestInstall
import com.google.android.play.core.ktx.requestProgressFlow
import com.google.android.play.core.ktx.status
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

const val TAG = "MainActivity"

sealed interface FeatureState<out T, out E> {
    data object None : FeatureState<Nothing, Nothing>
    data object Loading : FeatureState<Nothing, Nothing>
    data class Success<T>(val data: T) : FeatureState<T, Nothing>
    data class Error<E>(val err: E) : FeatureState<Nothing, E>
}

class MainActivity : ComponentActivity() {
    private val assetModuleName = "assets"
    private lateinit var splitInstallManager: SplitInstallManager
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val featureState by lazy {
        splitInstallManager.requestProgressFlow()
            .filter { state ->
                state.moduleNames.contains(assetModuleName)
            }
            .map { state ->
                Log.d(TAG, "status updated: ${state.status}")
                when (state.status) {
                    SplitInstallSessionStatus.PENDING,
                    SplitInstallSessionStatus.DOWNLOADING,
                    SplitInstallSessionStatus.DOWNLOADED,
                    SplitInstallSessionStatus.INSTALLING -> FeatureState.Loading

                    SplitInstallSessionStatus.INSTALLED -> {
                        SplitCompat.installActivity(this)
                        FeatureState.Success(readAssetText("asset.txt"))
                    }

                    SplitInstallSessionStatus.FAILED -> {
                        Log.w(TAG, "install failed: ${state.errorCode()}")
                        FeatureState.Error(state.errorCode())
                    }

                    else -> FeatureState.None
                }
            }.catch {
                Log.e(TAG, "error: $it")
            }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = getCurrentState()
            )
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.installActivity(this)
    }

    private fun getCurrentState(): FeatureState<String, Nothing> {
        if (splitInstallManager.installedModules.contains(assetModuleName)) {
            return FeatureState.Success(readAssetText("asset.txt"))
        }
        return FeatureState.None
    }

    private fun readAssetText(fileName: String): String {
        val inputStream = assets.open(fileName)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        return bufferedReader.use { it.readText() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        splitInstallManager = SplitInstallManagerFactory.create(this)
        Log.d(TAG, "installed modules: ${splitInstallManager.installedModules}")

        enableEdgeToEdge()
        setContent {
            DynamicfeatureTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        DynamicFeatureScreen(
                            state = featureState.collectAsStateWithLifecycle().value,
                            onClickGetFeature = {
                                coroutineScope.launch {
                                    splitInstallManager.requestInstall(listOf(assetModuleName))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicFeatureScreen(
    state: FeatureState<String, Int>,
    onClickGetFeature: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    )
    {
        Text(
            text = when (state) {
                FeatureState.None -> "None"
                FeatureState.Loading -> "Loading..."
                is FeatureState.Success -> "Success!\n${state.data}"
                is FeatureState.Error -> {
                    val reason = when (state.err) {
                        // SplitInstallErrorCode.NO_ERROR ->
                        SplitInstallErrorCode.ACCESS_DENIED -> "ACCESS_DENIED"
                        SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED -> "ACTIVE_SESSIONS_LIMIT_EXCEEDED"
                        SplitInstallErrorCode.API_NOT_AVAILABLE -> "API_NOT_AVAILABLE"
                        SplitInstallErrorCode.APP_NOT_OWNED -> "APP_NOT_OWNED"
                        SplitInstallErrorCode.NETWORK_ERROR -> "NETWORK_ERROR"
                        SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION -> "INCOMPATIBLE_WITH_EXISTING_SESSION"
                        SplitInstallErrorCode.INTERNAL_ERROR -> "INTERNAL_ERROR"
                        SplitInstallErrorCode.INVALID_REQUEST -> "INVALID_REQUEST"
                        SplitInstallErrorCode.MODULE_UNAVAILABLE -> "MODULE_UNAVAILABLE"
                        SplitInstallErrorCode.INSUFFICIENT_STORAGE -> "INSUFFICIENT_STORAGE"
                        else -> "UNKNOWN"
                    }
                    "error: $reason"
                }
            }
        )
        ElevatedButton(
            onClick = onClickGetFeature,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(text = "Get Feature Module")
        }
    }
}