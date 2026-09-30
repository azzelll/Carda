import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "id.carda.core.ml"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Opt-in research assets are test-only, generated under build/, never release assets.
abstract class ResearchAssetsTask : DefaultTask() {
    @get:InputDirectory @get:Optional
    abstract val sourceDirectory: DirectoryProperty
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun prepare() {
        if (!sourceDirectory.isPresent) {
            files.delete { delete(outputDirectory) }
            outputDirectory.get().asFile.mkdirs()
            return
        }
        files.sync {
            into(outputDirectory)
            from(sourceDirectory) { include("research-model.tflite", "manifest.json", "input-*.bin", "output-*.bin") }
        }
    }
}
val researchSource = providers.gradleProperty("cardaResearchAssets")
val researchAssets = layout.buildDirectory.dir("generated/researchAndroidTestAssets")
val prepareResearchAssets by tasks.registering(ResearchAssetsTask::class) {
    outputDirectory.set(researchAssets)
    if (researchSource.isPresent) {
        val source = file(researchSource.get()).canonicalFile
        require(source.isDirectory && !source.toPath().startsWith(rootDir.canonicalFile.toPath())) {
            "cardaResearchAssets must be an existing directory outside this repository"
        }
        sourceDirectory.set(source)
    }
}
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(prepareResearchAssets, ResearchAssetsTask::outputDirectory)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
    implementation(libs.litert)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
