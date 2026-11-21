package com.ivy.importdata.csvimport.flow.instructions

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivy.ui.R

@Composable
fun IvyWalletSteps(
    onUploadClick: () -> Unit
) {
    Spacer(Modifier.height(12.dp))

    StepTitle(
        number = 1,
        title = stringResource(R.string.export_data),
        description = """
            In Ivy Wallet: Settings > "Backup Data" for a full backup (accounts,
            categories, colours and icons), or Settings > "Export CSV" for just
            your transactions. Both work here.
        """.trimIndent()
    )

    Spacer(Modifier.height(12.dp))

    VideoArticleRow(
        videoUrl = null,
        articleUrl = null
    )

    Spacer(Modifier.height(24.dp))

    UploadFileStep(
        stepNumber = 2,
        text = "Upload the backup (ZIP/JSON) or CSV file",
        btnTitle = "Choose file",
        onUploadClick = onUploadClick
    )
}
