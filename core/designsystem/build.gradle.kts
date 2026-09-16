plugins {
  id("stratum.android.library")
  id("stratum.android.compose")
}

android { namespace = "com.stratum.core.designsystem" }

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.material.icons.core)
}
