import com.android.builder.internal.packaging.IncrementalPackager
import com.android.tools.build.apkzlib.sign.SigningExtension
import com.android.tools.build.apkzlib.sign.SigningOptions
import com.android.tools.build.apkzlib.zfile.ZFiles
import com.android.tools.build.apkzlib.zip.AlignmentRules
import com.android.tools.build.apkzlib.zip.ZFileOptions
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.cert.X509Certificate

plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.vvb2060.callrecording"
    defaultConfig {
        versionCode = 3
        versionName = "1.2"
        externalNativeBuild {
            ndkBuild {
                abiFilters += listOf("arm64-v8a")
                abiFilters += listOf("armeabi-v7a", "x86", "x86_64")
                arguments += "-j${Runtime.getRuntime().availableProcessors()}"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs["debug"]
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
    packaging {
        resources {
            excludes += "**"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    compileOnly("androidx.annotation", "annotation", "1.3.0")
    compileOnly("de.robv.android.xposed", "api", "82")
}

val optimizeReleaseRes by tasks.registering(Exec::class) {
    val aapt2 = Paths.get(
        project.android.sdkDirectory.path,
        "build-tools", project.android.buildToolsVersion, "aapt2"
    )
    val zip = Paths.get(
        project.buildDir.path, "intermediates",
        "optimized_processed_res", "release", "optimizeReleaseResources",
        "resources-release-optimize.ap_"
    )
    val optimized = zip.resolveSibling("optimized")
    commandLine(
        aapt2, "optimize", "--collapse-resource-names",
        "--enable-sparse-encoding", "-o", optimized, zip
    )

    doLast {
        Files.delete(zip)
        Files.move(optimized, zip)
    }
}

val delMetadata by tasks.registering {
    val sign = android.signingConfigs["debug"]
    val minSdk = android.defaultConfig.minSdk!!
    val files = tasks.named("packageRelease").get().outputs.files
    doLast {
        val options = ZFileOptions().apply {
            alignmentRule = AlignmentRules.constantForSuffix(".so", 4096)
            noTimestamps = true
            autoSortFiles = true
        }
        val apk = files.asFileTree.filter { it.name.endsWith(".apk") }.singleFile
        ZFiles.apk(apk, options).use { zFile ->
            val keyStore = KeyStore.getInstance(sign.storeType ?: KeyStore.getDefaultType())
            FileInputStream(sign.storeFile!!).use {
                keyStore.load(it, sign.storePassword!!.toCharArray())
            }
            val protParam = KeyStore.PasswordProtection(sign.keyPassword!!.toCharArray())
            val entry = keyStore.getEntry(sign.keyAlias!!, protParam)
            val privateKey = entry as KeyStore.PrivateKeyEntry
            val signingOptions = SigningOptions.builder()
                .setMinSdkVersion(minSdk)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setKey(privateKey.privateKey)
                .setCertificates(privateKey.certificate as X509Certificate)
                .setValidation(SigningOptions.Validation.ASSUME_INVALID)
                .build()
            SigningExtension(signingOptions).register(zFile)
            zFile.get(IncrementalPackager.APP_METADATA_ENTRY_PATH)?.delete()
        }
    }
}

tasks.configureEach {
    if (name == "optimizeReleaseResources") {
        finalizedBy(optimizeReleaseRes)
    }
    if (name == "packageRelease") {
        finalizedBy(delMetadata)
    }
}
