package com.andrespar2.parflix

import android.content.Context
import com.andrespar2.parflix.core.network.NetworkMonitor
import com.andrespar2.parflix.core.network.Providers
import com.andrespar2.parflix.core.security.CredentialStore
import com.andrespar2.parflix.data.local.ParFlixDatabase
import com.andrespar2.parflix.data.preferences.UserPreferences
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.data.repository.DetailsRepository
import com.andrespar2.parflix.data.repository.LibraryRepository
import com.andrespar2.parflix.data.repository.SearchRepository
import com.andrespar2.parflix.data.sync.CatalogSynchronizer
import com.andrespar2.parflix.data.sync.SyncScheduler

/**
 * Contenedor de dependencias (DI manual ligera).
 *
 * Todo se crea una vez por proceso. Las capas se comunican mediante flujos de
 * Room y de preferencias, de modo que la interfaz reacciona sola a los cambios.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val credentialStore = CredentialStore(appContext)

    val networkMonitor = NetworkMonitor(appContext)

    private val providers = Providers(credentialStore)

    val database: ParFlixDatabase = ParFlixDatabase.build(appContext)

    val preferences = UserPreferences(appContext)

    val detailsRepository = DetailsRepository(
        database = database,
        tmdb = providers.tmdb,
        vimeus = providers.vimeus,
    )

    val catalogSynchronizer = CatalogSynchronizer(
        database = database,
        vimeus = providers.vimeus,
        detailsRepository = detailsRepository,
        networkMonitor = networkMonitor,
        preferences = preferences,
    )

    val catalogRepository = CatalogRepository(
        database = database,
        networkMonitor = networkMonitor,
        preferences = preferences,
        synchronizer = catalogSynchronizer,
    )

    val searchRepository = SearchRepository(
        database = database,
        tmdb = providers.tmdb,
    )

    val libraryRepository = LibraryRepository(database)

    val syncScheduler = SyncScheduler(appContext)
}
