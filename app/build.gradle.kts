
plugins {
    id("offlinesmarthome.kotlin-application-conventions")
}

dependencies {
    implementation(gradleApi())
    implementation("org.apache.commons:commons-text")
    implementation("ai.picovoice:picovoice-java:2.1.0")
    implementation(project(":RhinoKontext"))
}



application {
    // Define the main class for the application.
    mainClass.set("offlinesmarthome.app.AppKt")
}
