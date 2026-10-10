group = "app.ysamjo"

patches {
    about {
        name = "ysamjo Morphe Patches"
        description = "Patches für YouTube for Android TV (com.google.android.youtube.tv), " +
            "TizenTube Cobalt (io.gh.reisxd.tizentube.cobalt) und RTL+ (de.rtli.tvnow)."
        source = "https://github.com/ysamjo/morphe-patches"
        author = "ysamjo"
        contact = "https://github.com/ysamjo"
        website = "https://github.com/ysamjo/morphe-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
