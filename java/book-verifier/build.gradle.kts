plugins {
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":proto"))
    implementation(project(":normalizer-core"))
    implementation(project(":transport-zmq"))
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("com.akshit.marketdata.book.BookVerifierApp")
}
