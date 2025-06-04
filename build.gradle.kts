import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "1.9.22" // Kotlinx Serialization プラグインを追加
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation(compose.material3) // Material 3 の依存関係を追加
    implementation(compose.materialIconsExtended) // Material Icons Extended の依存関係を追加
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Kotlinx Serialization の依存関係を追加
    // implementation("com.darkrockstudios:mpfilepicker:1.2.0") // mpfilepicker を削除
    implementation("io.github.vinceglb:filekit-core:0.10.0-beta03")
    implementation("io.github.vinceglb:filekit-dialogs:0.10.0-beta03")
    implementation("io.github.vinceglb:filekit-dialogs-compose:0.10.0-beta03")
}

compose.desktop {
    application {
        mainClass = "com.example.MainKt" // パッケージ名を追加

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb) // MSI を削除。EXEもインストーラーなので削除検討。
            packageName = "SnippetButton"
            packageVersion = "1.0.0"
            // Windows 向けの jpackage 引数設定は createDistributable タスクに期待するため一旦削除
        }
    }
}
