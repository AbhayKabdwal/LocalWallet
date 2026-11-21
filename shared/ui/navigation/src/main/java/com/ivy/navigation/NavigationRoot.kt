package com.ivy.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

@SuppressLint("ComposeCompositionLocalUsage")
private val LocalNavigation = compositionLocalOf<Navigation> { error("No LocalNavigation") }

/**
 * The [ViewModelStoreOwner] that screen-scoped ViewModels live in.
 *
 * Public only because [screenScopedViewModel] is inline and has to read it.
 * Prefer that function over touching this directly.
 */
@Suppress("CompositionLocalAllowlist")
@SuppressLint("ComposeCompositionLocalUsage")
val LocalScreenViewModelStoreOwner = compositionLocalOf<ViewModelStoreOwner> {
    error("No LocalScreenViewModelStoreOwner. Is this screen inside a NavigationRoot?")
}

/**
 * A store that belongs to one screen rather than to the Activity.
 *
 * Screen-scoped ViewModels used to be created in - and cleared from - the
 * Activity's own store. Clearing that store took the Activity's ViewModels
 * with them, `RootViewModel` included: it was re-created with its app-lock
 * state back at `null`, which the root composable renders as nothing at all.
 * The visible symptom was the whole app going blank the moment a non-legacy
 * screen caused the root to recompose - changing an appearance setting, for
 * instance, since that repaints from the root by design.
 *
 * The default factory and creation extras are delegated to the host (the
 * Activity), so Hilt still builds these ViewModels and `SavedStateHandle`
 * still resolves; only where they are *stored* changes.
 */
private class ScreenViewModelStoreOwner(
    private val host: ViewModelStoreOwner?,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val hostDefaults: HasDefaultViewModelProviderFactory?
        get() = host as? HasDefaultViewModelProviderFactory

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = hostDefaults?.defaultViewModelProviderFactory
            ?: ViewModelProvider.NewInstanceFactory.instance

    override val defaultViewModelCreationExtras: CreationExtras
        get() = hostDefaults?.defaultViewModelCreationExtras ?: CreationExtras.Empty
}

@Composable
fun NavigationRoot(
    navigation: Navigation,
    navGraph: @Composable (screen: Screen?) -> Unit
) {
    val screen = navigation.currentScreen
    val host = LocalViewModelStoreOwner.current

    // Keyed on the screen: navigating gives the next screen an empty store of
    // its own during composition, and the effect below then clears the one the
    // screen it replaced was using. Clearing on the way out instead would wipe
    // the ViewModels the incoming screen had just created.
    val screenOwner = remember(screen, host) { ScreenViewModelStoreOwner(host) }
    DisposableEffect(screenOwner) {
        onDispose { screenOwner.viewModelStore.clear() }
    }

    CompositionLocalProvider(
        LocalNavigation provides navigation,
        LocalScreenViewModelStoreOwner provides screenOwner,
    ) {
        navGraph(screen)
    }
}

@Composable
fun navigation(): Navigation {
    return LocalNavigation.current
}

/**
 * Provides a [ViewModel] instance scoped the screen's life.
 * When the user navigates away from the screen all screen scoped
 * viewModels are destroyed.
 * Does not apply for legacy screens, which keep using the Activity's store.
 */
@Composable
inline fun <reified T : ViewModel> screenScopedViewModel(
    factory: ViewModelProvider.Factory? = null
): T {
    val viewModelStoreOwner = LocalScreenViewModelStoreOwner.current
    val viewModelProvider = factory?.let {
        ViewModelProvider(viewModelStoreOwner, it)
    } ?: ViewModelProvider(viewModelStoreOwner)
    return viewModelProvider[T::class.java]
}
