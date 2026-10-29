plugins {
    id("com.android.application") version "8.12.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}

// This repo lives under ~/Documents, which iCloud Drive syncs. iCloud makes "file 2" conflict
// copies inside build/ while Gradle is writing, which breaks resource merging.
// iCloud skips folders whose names end in .nosync, so build output goes there instead.
allprojects {
    layout.buildDirectory.set(file("build.nosync"))
}
