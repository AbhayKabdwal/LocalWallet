package com.ivy.smsimport.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.ivy.navigation.SmsReviewScreen
import com.ivy.navigation.navigation
import com.ivy.smsimport.data.SmsImportDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class SmsImportBannerViewModel @Inject constructor(
    dao: SmsImportDao,
) : ViewModel() {
    val pendingCount: Flow<Int> = dao.pendingCount()
}

/**
 * Tells the user that messages are waiting, without hijacking app start.
 *
 * The scan itself runs as background work when the app opens; this is how the
 * result surfaces. Pushing a modal review queue in front of someone who only
 * wanted to check a balance is the main way this kind of feature becomes
 * annoying, so it stays a dismissible prompt they tap when ready.
 */
@Composable
fun SmsImportBanner(
    modifier: Modifier = Modifier,
) {
    // Previews and Paparazzi screenshot tests render the Home screen with no
    // Activity behind it, so there is no ViewModelStore to hold this one.
    // Taking the owner explicitly - rather than letting `hiltViewModel()`
    // assert on it in a default argument - lets an incidental banner render
    // nothing in that case instead of bringing the whole screen down.
    val owner = LocalViewModelStoreOwner.current ?: return
    val viewModel: SmsImportBannerViewModel = hiltViewModel(owner)
    val count by viewModel.pendingCount.collectAsState(initial = 0)
    var dismissed by remember { mutableStateOf(false) }
    val nav = navigation()

    if (count <= 0 || dismissed) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (count == 1) {
                        "1 transaction found in your messages"
                    } else {
                        "$count transactions found in your messages"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { nav.navigateTo(SmsReviewScreen) }) {
                        Text("Review")
                    }
                    TextButton(onClick = { dismissed = true }) {
                        Text("Later")
                    }
                }
            }
        }
    }
}
