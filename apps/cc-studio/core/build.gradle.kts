plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}

tasks.register<JavaExec>("generateGojoPlugin") {
    dependsOn(tasks.named("testClasses"))
    mainClass.set("com.motionsalt.ccstudio.core.CcPluginTest")
    classpath = sourceSets["test"].runtimeClasspath
    args(layout.buildDirectory.dir("gojo-plugin/tree").get().asFile.absolutePath)
}

tasks.register<JavaExec>("generateFinishingFixtures") {
    dependsOn(tasks.named("testClasses"))
    mainClass.set("com.motionsalt.ccstudio.core.CcFinishingFixtures")
    classpath = sourceSets["test"].runtimeClasspath
    args(layout.buildDirectory.dir("cc-fixtures").get().asFile.absolutePath)
}
