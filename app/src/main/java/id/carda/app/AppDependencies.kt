package id.carda.app

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.carda.core.auth.AuthClient
import id.carda.core.auth.AuthEndpointPolicy
import id.carda.core.auth.EncryptedSessionStore
import id.carda.core.auth.IdentityEnvironment
import id.carda.core.auth.AccountDeletionCoordinator
import id.carda.core.auth.PendingDeletionStore
import id.carda.core.auth.FilePendingDeletionStore
import id.carda.core.auth.SessionStorage
import id.carda.core.model.LocalAccountCleaner
import id.carda.core.data.CardaDatabase
import id.carda.core.data.AccountLocalDataCleaner
import id.carda.core.data.GuardedMeasurementRepository
import id.carda.core.data.GuardedProfileRepository
import id.carda.core.model.AccountDataGate
import id.carda.core.data.ProfilePreferencesStore
import id.carda.core.data.RoomMeasurementRepository
import id.carda.core.model.MeasurementRepository
import id.carda.core.model.LocalProfileRepository
import javax.inject.Singleton

/** Application owns construction; features work with injected domain/platform contracts. */
@Module
@InstallIn(SingletonComponent::class)
internal object AppDependencies {
    @Provides @Singleton fun sessions(@ApplicationContext context: Context) = EncryptedSessionStore(context)
    @Provides fun sessionStorage(sessions: EncryptedSessionStore): SessionStorage = sessions
    @Provides @Singleton fun accountDataGate() = AccountDataGate()
    @Provides @Singleton fun cleaner(database: CardaDatabase, profiles: ProfilePreferencesStore,
        gate: AccountDataGate, reminders: ReminderScheduler): LocalAccountCleaner =
        AccountLocalDataCleaner(RoomMeasurementRepository(database.measurements()), profiles, gate, reminders::cancel)
    @Provides @Singleton fun profiles(@ApplicationContext context: Context) = ProfilePreferencesStore.open(context)
    @Provides fun localProfiles(profiles: ProfilePreferencesStore, gate: AccountDataGate): LocalProfileRepository =
        GuardedProfileRepository(profiles, gate)
    @Provides @Singleton fun database(@ApplicationContext context: Context) = CardaDatabase.open(context)
    @Provides @Singleton fun measurements(database: CardaDatabase, gate: AccountDataGate): MeasurementRepository =
        GuardedMeasurementRepository(RoomMeasurementRepository(database.measurements()), gate)
    @Provides @Singleton fun reminders(@ApplicationContext context: Context) = ReminderScheduler(context)
    @Provides @Singleton fun pendingDeletion(@ApplicationContext context: Context): PendingDeletionStore =
        FilePendingDeletionStore(context)
    @Provides @Singleton fun deletion(pending: PendingDeletionStore) = AccountDeletionCoordinator(pending)
    @Provides @Singleton fun identity(sessions: EncryptedSessionStore): IdentityEnvironment {
        val allowed = AuthEndpointPolicy.isAllowed(BuildConfig.AUTH_BASE_URL, BuildConfig.DEBUG)
        return IdentityEnvironment(if (allowed) AuthClient(BuildConfig.AUTH_BASE_URL, sessions, BuildConfig.DEBUG) else null)
    }
}
