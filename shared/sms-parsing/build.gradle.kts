plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.sms"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.model)
}
