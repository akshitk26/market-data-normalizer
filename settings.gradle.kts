pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "market-data-normalizer"

include(
    "proto",
    "normalizer-core",
    "transport-zmq",
    "feed-sources",
    "ingestion-service",
    "book-verifier",
    "benchmark"
)

project(":normalizer-core").projectDir = file("java/normalizer-core")
project(":transport-zmq").projectDir = file("java/transport-zmq")
project(":feed-sources").projectDir = file("java/feed-sources")
project(":ingestion-service").projectDir = file("java/ingestion-service")
project(":book-verifier").projectDir = file("java/book-verifier")
project(":benchmark").projectDir = file("java/benchmark")
