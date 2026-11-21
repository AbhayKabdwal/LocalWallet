plugins {
    id("ivy.feature")
    id("ivy.room")
}

android {
    namespace = "com.ivy.smsimport"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.data.model)
    implementation(projects.shared.domain)
    implementation(projects.shared.smsParsing)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    implementation(projects.temp.oldDesign)

    implementation(libs.datastore)
    implementation(libs.androidx.work)
    implementation(libs.hilt.navigation.compose)

    testImplementation(projects.shared.ui.testing)
}
