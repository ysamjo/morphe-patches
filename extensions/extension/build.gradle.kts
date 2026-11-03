extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.ysamjo.extension"
}

// Eine testbare JS-Quelle wird als Java-Konstante in die Dex-Extension eingebettet.
val generatedChannelSpeed = layout.buildDirectory.dir("generated/sources/channelSpeed")
val channelSpeedSource = layout.projectDirectory.file("src/main/js/channel-speed.js")
val generateChannelSpeedPayload by tasks.registering {
    inputs.file(channelSpeedSource)
    outputs.dir(generatedChannelSpeed)
    doLast {
        val target = generatedChannelSpeed.get().file("app/ysamjo/extension/youtubetv/ChannelSpeedPayload.java").asFile
        target.parentFile.mkdirs()
        val literal = channelSpeedSource.asFile.readLines().joinToString("\n            + ") { line ->
            "\"" + line.replace("\\", "\\\\").replace("\"", "\\\"").replace("\t", "\\t") + "\\n\""
        }
        target.writeText("package app.ysamjo.extension.youtubetv;\n" +
            "final class ChannelSpeedPayload {\n    static final String SCRIPT = $literal;\n" +
            "    private ChannelSpeedPayload() {}\n}\n")
    }
}
android.sourceSets.getByName("main").java.srcDir(generatedChannelSpeed)
tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    dependsOn(generateChannelSpeedPayload)
}
