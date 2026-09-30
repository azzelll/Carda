package id.carda.app

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.carda.feature.onboarding.AccountSessionViewModel
import id.carda.core.measurement.MeasurementEngine
import id.carda.core.ppg.ResearchDiagnosticValue
import id.carda.core.ppg.ResearchSignalDiagnostics
import id.carda.core.export.PdfSummaryExporter
import id.carda.core.model.LocalProfileRepository
import id.carda.core.model.MeasurementRepository
import id.carda.core.model.MeasurementSummary
import id.carda.feature.measurement.MeasurementRecordingViewModel
import id.carda.feature.measurement.RecordingState
import id.carda.feature.measurement.MeasurementScreen
import id.carda.feature.profile.ProfileScreen
import id.carda.feature.profile.ProfileActionsViewModel
import id.carda.feature.history.HistoryScreen
import id.carda.feature.history.HistoryActionsViewModel
import id.carda.feature.dashboard.DashboardSummary
import id.carda.feature.result.ResultScreen
import id.carda.feature.onboarding.OnboardingRoute
import id.carda.feature.onboarding.EducationScreen
import id.carda.core.model.MeasurementState
import id.carda.core.model.MeasurementActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var profileStore: LocalProfileRepository
    @Inject lateinit var measurementRepository: MeasurementRepository
    @Inject internal lateinit var reminderScheduler: ReminderScheduler
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Results and local profile data must not appear in screenshots or Recents previews.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val debugBuild = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        setContent {
            MaterialTheme {
                Scaffold { padding -> CardaRoot(padding, debugBuild) }
            }
        }
    }

    @Composable
    private fun CardaRoot(padding: PaddingValues, debugBuild: Boolean) {
        var consent by rememberSaveable { mutableStateOf(false) }
        // Active capture and pending camera permissions never survive Activity recreation.
        var measuring by remember { mutableStateOf(false) }
        var activityContext by rememberSaveable { mutableStateOf(MeasurementActivity.UNSPECIFIED) }
        var captureDuration by rememberSaveable { mutableStateOf(id.carda.core.model.CaptureDuration.THIRTY_SECONDS) }
        var denied by rememberSaveable { mutableStateOf(false) }
        var viewingHistory by rememberSaveable { mutableStateOf(false) }
        var viewingProfile by rememberSaveable { mutableStateOf(false) }
        var viewingEducation by rememberSaveable { mutableStateOf(false) }
        var confirmCaptureExit by rememberSaveable { mutableStateOf(false) }
        val accounts: AccountSessionViewModel = viewModel()
        val accountState by accounts.state.collectAsState()
        val activeAccountId = accountState.accountId
        val busy = accountState.busy
        val profileActions: ProfileActionsViewModel = viewModel()
        val profileAction by profileActions.state.collectAsState()
        val historyActions: HistoryActionsViewModel = viewModel()
        val historyAction by historyActions.state.collectAsState()
        val accessAllowed = accountState.accessAllowed
        var captureGeneration by remember { mutableStateOf<Long?>(null) }
        var confirmAccountDelete by rememberSaveable { mutableStateOf(false) }
        var accountDeletePassword by remember { mutableStateOf("") }
        var exportMessage by remember { mutableStateOf("") }
        var reminderMessage by remember { mutableStateOf("") }
        var pendingReminderHour by remember { mutableStateOf<Int?>(null) }
        var reminderOwner by remember { mutableStateOf<String?>(null) }
        var reminderGeneration by remember { mutableStateOf<Long?>(null) }
        var notificationGranted by remember {
            mutableStateOf(Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        }
        var pendingExport: MeasurementSummary? by remember { mutableStateOf(null) }
        var exportOwner by remember { mutableStateOf<String?>(null) }
        var exportGeneration by remember { mutableStateOf<Long?>(null) }
        var lastExportUri: Uri? by remember { mutableStateOf(null) }
        val scope = rememberCoroutineScope()
        val profiles = profileStore
        val reminders = reminderScheduler
        val measurements = measurementRepository
        var navigationInitialized by rememberSaveable { mutableStateOf(false) }
        var navigationAccount by rememberSaveable { mutableStateOf<String?>(null) }
        var navigationGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
        LaunchedEffect(accountState.generation, activeAccountId) {
            val accountChanged = !navigationInitialized || navigationAccount != activeAccountId ||
                navigationGeneration != accountState.generation
            navigationInitialized = true
            navigationAccount = activeAccountId
            navigationGeneration = accountState.generation
            // A capture never resumes automatically after recreation; non-capture routes can.
            measuring = false
            captureGeneration = null
            if (accountChanged) {
                viewingHistory = false
                viewingProfile = false
                viewingEducation = false
                activityContext = MeasurementActivity.UNSPECIFIED
                captureDuration = id.carda.core.model.CaptureDuration.THIRTY_SECONDS
            }
            confirmCaptureExit = false
            confirmAccountDelete = false
            accountDeletePassword = ""
            pendingExport = null
            lastExportUri = null
            exportOwner = null
            exportGeneration = null
            exportMessage = ""
            pendingReminderHour = null
            reminderOwner = null
            reminderGeneration = null
        }
        val consentVersion = if (activeAccountId != null) {
            val accountId = activeAccountId
            remember(accountId) { profiles.observeConsentVersion(accountId) }
                .collectAsState(initial = null).value
        } else null
        val acceptedConsent = if (activeAccountId == null) consent else consentVersion == CONSENT_VERSION
        val reminderHour = if (activeAccountId != null) {
            remember(activeAccountId) { profiles.observeReminderHour(activeAccountId) }
                .collectAsState(initial = null).value
        } else null
        LaunchedEffect(activeAccountId) {
            try {
                if (activeAccountId == null) reminders.cancel()
                else profiles.observeReminderHour(activeAccountId).collect { selectedHour ->
                    if (selectedHour == null) reminders.cancel()
                    else reminders.schedule(activeAccountId, selectedHour)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { reminderMessage = "Jadwal pengingat belum berhasil diperbarui. Coba pilih kembali jamnya." }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            denied = !granted
            measuring = granted && accessAllowed && captureGeneration == accountState.generation &&
                (activeAccountId != null || debugBuild)
        }
        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            notificationGranted = granted
            val requestedHour = pendingReminderHour
            pendingReminderHour = null
            if (granted && requestedHour != null && activeAccountId != null && reminderGeneration != null &&
                accounts.state.value.permits(reminderOwner, reminderGeneration!!)) {
                val accountId = activeAccountId
                profileActions.reminder(accountId, requestedHour)
                reminderMessage = "Pengingat diaktifkan."
            } else reminderMessage = "Izin notifikasi belum aktif; buka pengaturan notifikasi untuk mengaktifkannya."
        }
        val notificationSettings = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            notificationGranted = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            reminderMessage = if (notificationGranted) "Izin notifikasi aktif. Pilih kembali jam pengingat."
                else "Izin notifikasi masih mati; pengingat belum diaktifkan."
        }
        val choosePdfFolder = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { folder ->
            val summary = pendingExport
            val owner = exportOwner
            val revision = exportGeneration
            pendingExport = null
            scope.launch {
                val outcome = savePdfInSelectedFolder(
                    folder = folder,
                    payload = summary,
                    permitted = { revision != null && accounts.state.value.permits(owner, revision) },
                    create = { selectedFolder, acceptedSummary ->
                        withContext(Dispatchers.IO) {
                            val parent = DocumentsContract.buildDocumentUriUsingTree(selectedFolder,
                                DocumentsContract.getTreeDocumentId(selectedFolder))
                            DocumentsContract.createDocument(contentResolver, parent, "application/pdf",
                                "carda-ringkasan-${acceptedSummary.measuredAtEpochMillis}.pdf")
                        }
                    },
                    write = { document, acceptedSummary ->
                        withContext(Dispatchers.IO) {
                            val output = contentResolver.openOutputStream(document, "w")
                                ?: error("Document stream unavailable")
                            output.use { PdfSummaryExporter().write(it, acceptedSummary) }
                        }
                    },
                    delete = { document ->
                        withContext(Dispatchers.IO) {
                            DocumentsContract.deleteDocument(contentResolver, document)
                        }
                    },
                )
                if (outcome is PdfSaveOutcome.Success) lastExportUri = outcome.document
                exportMessage = outcome.userMessage()
            }
        }
        val requestExport: (MeasurementSummary) -> Unit = { summary ->
            if (accessAllowed && activeAccountId != null) {
                exportOwner = activeAccountId
                exportGeneration = accountState.generation
                pendingExport = summary
                lastExportUri = null
                exportMessage = ""
                choosePdfFolder.launch(null)
            }
        }
        val shareExport: () -> Unit = {
            lastExportUri?.takeIf { exportGeneration != null &&
                accounts.state.value.permits(exportOwner, exportGeneration!!) }?.let { uri ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(contentResolver, "Ringkasan Carda", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Bagikan ringkasan Carda"))
            }
            Unit
        }
        BackHandler(enabled = !measuring && (viewingHistory || viewingProfile || viewingEducation)) {
            viewingHistory = false
            viewingProfile = false
            viewingEducation = false
        }

        if (viewingEducation) {
            EducationScreen(onBack = { viewingEducation = false }, modifier = Modifier.padding(padding))
            return
        }

        if (measuring && accessAllowed && captureGeneration == accountState.generation &&
            (activeAccountId != null || debugBuild)) {
            var captureAttempt by rememberSaveable { mutableIntStateOf(0) }
            val engine = remember(captureAttempt, activityContext, captureDuration, activeAccountId, captureGeneration) {
                MeasurementEngine(applicationContext, activityContext, captureDuration,
                    researchMode = debugBuild)
            }
            DisposableEffect(engine) { onDispose { engine.close() } }
            val state by engine.state.collectAsState()
            val recorder: MeasurementRecordingViewModel = viewModel()
            val recording by recorder.state.collectAsState()
            val storageMessage = if (activeAccountId == null) "Pratinjau engineering: hasil tidak disimpan." else when (recording) {
                RecordingState.Idle -> ""
                RecordingState.Saving -> "Menyimpan ringkasan lokal…"
                RecordingState.Saved -> "Ringkasan tersimpan hanya di perangkat ini."
                RecordingState.Failed -> "Ringkasan belum tersimpan. Gunakan tombol coba simpan kembali."
            }
            BackHandler {
                if (state.state == MeasurementState.COMPLETE && storageMessage != "Menyimpan ringkasan lokal…")
                    measuring = false
                else confirmCaptureExit = true
            }
            LaunchedEffect(state.summary?.measuredAtEpochMillis) {
                val summary = state.summary ?: return@LaunchedEffect
                recorder.record(activeAccountId, summary)
            }
            LaunchedEffect(state.deviceProfile, activeAccountId) {
                state.deviceProfile?.let { recorder.recordCompatibility(activeAccountId, it) }
            }
            val summary = state.summary
            if (state.state == MeasurementState.COMPLETE && summary != null) {
                ResultScreen(summary, storageMessage, exportMessage,
                    canShareExport = lastExportUri != null,
                    researchNote = if (debugBuild) state.researchDiagnostics?.toDebugNote() else null,
                    onExport = requestExport,
                    onShareExport = shareExport,
                    onRetrySave = if (recording == RecordingState.Failed)
                        ({ recorder.record(activeAccountId, summary, retry = true) }) else null,
                    onDone = { if (storageMessage != "Menyimpan ringkasan lokal…") measuring = false },
                    modifier = Modifier.padding(padding))
            } else {
                MeasurementScreen(engine, state,
                    onRetry = { captureAttempt++ },
                    onDone = { measuring = false },
                    modifier = Modifier.padding(padding))
            }
            if (confirmCaptureExit) {
                AlertDialog(
                    onDismissRequest = { confirmCaptureExit = false },
                    title = { Text("Keluar dari pengukuran?") },
                    text = { Text("Kamera dan lampu kilat akan dimatikan. Hasil yang belum selesai tidak disimpan.") },
                    confirmButton = {
                        TextButton(onClick = {
                            if (storageMessage != "Menyimpan ringkasan lokal…") {
                                confirmCaptureExit = false
                                measuring = false
                            }
                        }) { Text("Keluar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmCaptureExit = false }) { Text("Lanjutkan") }
                    },
                )
            }
            return
        }

        if (viewingHistory && activeAccountId != null && accessAllowed) {
            val activeAccount = activeAccountId
            val history by remember(activeAccount) { measurements.observeHistory(activeAccount) }
                .collectAsState(initial = emptyList())
            HistoryScreen(history, exportMessage, requestExport,
                canShareExport = lastExportUri != null,
                onShareExport = shareExport,
                onDeleteAll = { historyActions.deleteAll(activeAccount) },
                actionMessage = historyAction.message.takeIf { historyAction.accountId == activeAccount }.orEmpty(),
                isDeleting = historyAction.busy,
                onBack = { viewingHistory = false },
                modifier = Modifier.padding(padding))
            return
        }

        if (viewingProfile && activeAccountId != null && accessAllowed) {
            val activeAccount = activeAccountId
            val profile by remember(activeAccount) { profiles.observe(activeAccount) }
                .collectAsState(initial = id.carda.core.model.LocalProfile(activeAccount))
            val device by remember(activeAccount) { profiles.observeDeviceCompatibility(activeAccount) }
                .collectAsState(initial = null)
            ProfileScreen(profile,
                deviceCompatibility = device,
                reminderHour = reminderHour,
                notificationPermissionGranted = notificationGranted,
                reminderMessage = reminderMessage,
                actionMessage = profileAction.message.takeIf { profileAction.accountId == activeAccount }.orEmpty(),
                isSaving = profileAction.busy,
                onOpenNotificationSettings = {
                    notificationSettings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    })
                },
                onReminderHourChange = { hour ->
                    if (hour == null) {
                        profileActions.reminder(activeAccount, null)
                        reminders.cancel()
                        reminderMessage = "Pengingat dimatikan."
                    } else if (notificationGranted) {
                        profileActions.reminder(activeAccount, hour)
                        reminderMessage = "Pengingat sekitar pukul %02d.00 dipilih.".format(hour)
                    } else {
                        pendingReminderHour = hour
                        reminderOwner = activeAccount
                        reminderGeneration = accountState.generation
                        if (Build.VERSION.SDK_INT >= 33)
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else profileActions.reminder(activeAccount, hour)
                    }
                },
                onSave = profileActions::save,
                onDelete = { profileActions.delete(activeAccount) },
                onBack = { viewingProfile = false },
                modifier = Modifier.padding(padding))
            return
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Carda", style = MaterialTheme.typography.headlineLarge)
            Text("PPG dari kamera ponsel untuk informasi wellness. Carda bukan alat diagnosis atau layanan darurat.")
            Button(enabled = !busy, onClick = { viewingEducation = true }) { Text("Panduan dan batasan") }
            Text("Akun dibutuhkan sebelum penggunaan biasa. Data kesehatan tetap di perangkat dan tidak dipulihkan di perangkat baru.")
            if (activeAccountId == null && !busy && !accountState.cleanupPending) {
                OnboardingRoute(onSignedIn = {
                    accounts.reconcileSignIn()
                })
            } else if (activeAccountId != null) {
                val accountId = activeAccountId
                val recent by remember(accountId) { measurements.observeHistory(accountId) }
                    .collectAsState(initial = emptyList())
                val localProfile by remember(accountId) { profiles.observe(accountId) }
                    .collectAsState(initial = null)
                Text("Sesi lokal aktif untuk akun ${accountId.take(8)}…")
                if (localProfile?.let { it.fullName.isBlank() && it.birthDate == null &&
                        it.sex == id.carda.core.model.Sex.UNSPECIFIED && it.phone.isBlank() } == true) {
                    Text("Profil lokal belum diisi. Detail pada formulir daftar belum tersimpan jika aplikasi dimulai ulang sebelum login. Isi kembali melalui Edit profil lokal; data profil tidak dipulihkan dari server.")
                }
                DashboardSummary(recent)
                Button(enabled = accessAllowed, onClick = { viewingHistory = true }) { Text("Lihat riwayat lokal") }
                Button(enabled = accessAllowed, onClick = { viewingProfile = true }) { Text("Edit profil lokal") }
                Button(onClick = accounts::logout, enabled = !busy) { Text("Keluar") }
                if (confirmAccountDelete) {
                    Text("Hapus akun server dan seluruh profil serta riwayat lokal akun ini? Tindakan ini permanen dan memerlukan internet.")
                    OutlinedTextField(value = accountDeletePassword,
                        onValueChange = { accountDeletePassword = it },
                        label = { Text("Kata sandi untuk konfirmasi") },
                        visualTransformation = PasswordVisualTransformation())
                    Button(enabled = !busy && accounts.canDeleteAccount && accountDeletePassword.isNotBlank(),
                        onClick = {
                            val password = accountDeletePassword
                            accountDeletePassword = ""
                            confirmAccountDelete = false
                            accounts.deleteAccount(password)
                        }) { Text("Ya, hapus akun dan data lokal") }
                    Button(onClick = { confirmAccountDelete = false; accountDeletePassword = "" }) {
                        Text("Batal")
                    }
                } else Button(enabled = !busy && accounts.canDeleteAccount, onClick = { confirmAccountDelete = true }) { Text("Hapus akun daring") }
            }
            if (profileAction.accountId == activeAccountId && profileAction.message.isNotBlank()) Text(profileAction.message)
            if (accountState.message.isNotBlank()) Text(accountState.message)
            if (accountState.cleanupPending) {
                Button(enabled = !busy, onClick = accounts::resumeCleanup) { Text("Ulangi pembersihan data lokal") }
            }
            if (debugBuild && activeAccountId == null) {
                Text("Pratinjau engineering: uji kamera dan algoritma tanpa akun. Hasil tidak disimpan.")
            }
            if (activeAccountId != null || debugBuild) {
                Text("Durasi pengukuran", style = MaterialTheme.typography.titleMedium)
                Text("Pilih 30 atau 60 detik sinyal yang lolos kualitas. Durasi lebih lama bukan jaminan akurasi; metrik yang belum tervalidasi tetap tidak tersedia.")
                id.carda.core.model.CaptureDuration.entries.forEach { option ->
                    OutlinedButton(enabled = accessAllowed, onClick = { captureDuration = option },
                        modifier = Modifier.semantics {
                            role = Role.RadioButton
                            selected = captureDuration == option
                            stateDescription = if (captureDuration == option) "Dipilih" else "Tidak dipilih"
                        }) {
                        Text((if (captureDuration == option) "✓ " else "") + "${option.seconds} detik")
                    }
                }
                Text("Kondisi sebelum ukur", style = MaterialTheme.typography.titleMedium)
                Text("Pilih konteks sesi. Pilihan ini hanya dicatat bersama ringkasan lokal dan tidak mengubah interpretasi hasil.")
                listOf(MeasurementActivity.RESTING, MeasurementActivity.RECENT_ACTIVITY,
                    MeasurementActivity.UNSPECIFIED).forEach { option ->
                    OutlinedButton(enabled = accessAllowed, onClick = { activityContext = option },
                        modifier = Modifier.semantics {
                            role = Role.RadioButton
                            selected = activityContext == option
                            stateDescription = if (activityContext == option) "Dipilih" else "Tidak dipilih"
                        }) {
                        Text((if (activityContext == option) "✓ " else "") + option.label)
                    }
                }
                Checkbox(checked = acceptedConsent, enabled = accessAllowed, onCheckedChange = { checked ->
                    val accountId = activeAccountId
                    if (accountId == null) consent = checked
                    else profileActions.consent(accountId, if (checked) CONSENT_VERSION else null)
                },
                    modifier = Modifier.semantics {
                        contentDescription = "Saya paham batasan dan setuju menggunakan kamera untuk sesi ini"
                    })
                Text("Saya paham batasan dan setuju menggunakan kamera untuk sesi ini.")
                if (denied) Text("Izin kamera ditolak. Berikan izin untuk memulai pengujian.")
                Button(onClick = {
                    captureGeneration = accountState.generation
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        measuring = true
                    else permission.launch(Manifest.permission.CAMERA)
                }, enabled = acceptedConsent && accessAllowed && !profileAction.busy) {
                    Text(if (activeAccountId == null) "Uji pengukuran di perangkat" else "Mulai pengukuran")
                }
            } else {
                Text("Masuk untuk membuka pengukuran.")
            }
        }
    }

    private companion object { const val CONSENT_VERSION = "consent-v1" }
}

private fun ResearchSignalDiagnostics.toDebugNote(): String {
    fun ResearchDiagnosticValue.describe(unit: String): String = when (this) {
        is ResearchDiagnosticValue.Candidate ->
            String.format(Locale.forLanguageTag("id-ID"), "%.3f %s (%s)", value, unit, methodVersion)
        is ResearchDiagnosticValue.Unavailable -> "ditahan: $reasonCode"
    }
    return "KANDIDAT RISET BUILD DEBUG — tidak disimpan, diekspor, atau digunakan sebagai hasil kesehatan. " +
        "Laju napas: ${respiratoryBreathsPerMinute.describe("napas/menit")}. " +
        "Rasio optik merah/hijau: ${redGreenOpticalRatio.describe("tanpa satuan")}; ini bukan persentase SpO₂."
}

/** Only [create] returns a document URI. The folder selected by SAF is never deleted. */
internal sealed interface PdfSaveOutcome<out Document> {
    data object Cancelled : PdfSaveOutcome<Nothing>
    data object StaleBeforeCreate : PdfSaveOutcome<Nothing>
    data object CreationFailed : PdfSaveOutcome<Nothing>
    data class Success<Document>(val document: Document) : PdfSaveOutcome<Document>
    data class StaleAfterCreate(val deleted: Boolean) : PdfSaveOutcome<Nothing>
    data class WriteFailed(val deleted: Boolean) : PdfSaveOutcome<Nothing>
}

/** Select a directory first; create a PDF only while the same account generation still permits it. */
internal suspend fun <Folder : Any, Document : Any, Payload : Any> savePdfInSelectedFolder(
    folder: Folder?,
    payload: Payload?,
    permitted: () -> Boolean,
    create: suspend (Folder, Payload) -> Document?,
    write: suspend (Document, Payload) -> Unit,
    delete: suspend (Document) -> Boolean,
): PdfSaveOutcome<Document> {
    if (folder == null) return PdfSaveOutcome.Cancelled
    fun allowed() = runCatching { permitted() }.getOrDefault(false)
    if (payload == null || !allowed()) return PdfSaveOutcome.StaleBeforeCreate
    val document = try { create(folder, payload) ?: return PdfSaveOutcome.CreationFailed }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { return PdfSaveOutcome.CreationFailed }
    suspend fun removeCreated(): Boolean = withContext(NonCancellable) {
        try { delete(document) } catch (_: Exception) { false }
    }
    try {
        if (!allowed()) return PdfSaveOutcome.StaleAfterCreate(removeCreated())
        write(document, payload)
        if (!allowed()) return PdfSaveOutcome.StaleAfterCreate(removeCreated())
        return PdfSaveOutcome.Success(document)
    } catch (cancelled: CancellationException) {
        removeCreated()
        throw cancelled
    } catch (_: Exception) {
        return PdfSaveOutcome.WriteFailed(removeCreated())
    }
}

internal fun PdfSaveOutcome<*>.userMessage(): String = when (this) {
    PdfSaveOutcome.Cancelled -> "Ekspor dibatalkan; Carda tidak membuat PDF."
    PdfSaveOutcome.StaleBeforeCreate -> "Ringkasan atau sesi akun berubah; Carda tidak membuat PDF."
    PdfSaveOutcome.CreationFailed -> "PDF belum berhasil dibuat. Periksa folder pilihan Anda dan coba lagi."
    is PdfSaveOutcome.Success -> "PDF ringkasan berhasil disimpan di folder pilihan Anda."
    is PdfSaveOutcome.StaleAfterCreate -> if (deleted)
        "Sesi akun berubah; dokumen baru yang dibuat Carda sudah dihapus."
    else "Sesi akun berubah. Dokumen baru mungkin masih ada di folder pilihan Anda; periksa dan hapus secara manual."
    is PdfSaveOutcome.WriteFailed -> if (deleted)
        "PDF belum berhasil disimpan; dokumen baru yang dibuat Carda sudah dihapus."
    else "PDF belum berhasil disimpan. Dokumen baru mungkin masih ada di folder pilihan Anda; periksa dan hapus secara manual."
}
