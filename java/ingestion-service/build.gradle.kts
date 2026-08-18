plugins {
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":normalizer-core"))
    implementation(project(":feed-sources"))
    implementation(project(":transport-zmq"))
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("com.akshit.marketdata.ingestion.IngestionServiceApp")
}

tasks.register<JavaExec>("captureCoinbaseLevel2") {
    group = "market data"
    description = "Capture public Coinbase level2_batch WebSocket messages and verify they parse into protobuf events."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.CoinbaseCaptureApp")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("processCoinbaseLevel2File") {
    group = "market data"
    description = "Parse a Coinbase level2 JSONL capture into normalized protobuf events and print processing counts."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.CoinbaseProcessFileApp")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("captureGeminiFixExamples") {
    group = "market data"
    description = "Download Gemini's official FIX market-data examples and normalize them into protobuf events."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.GeminiFixCaptureApp")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("processGeminiFixFile") {
    group = "market data"
    description = "Parse a Gemini FIX JSONL capture into normalized protobuf events and print processing counts."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.GeminiFixProcessFileApp")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("captureNasdaqItchWindow") {
    group = "market data"
    description = "Download a random real message window from a public Nasdaq TotalView-ITCH 5.0 sample file."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.NasdaqItchCaptureApp")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("processNasdaqItchFile") {
    group = "market data"
    description = "Process a locally captured length-prefixed Nasdaq TotalView-ITCH binary window."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.NasdaqItchProcessFileApp")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("captureGeminiFixLive") {
    group = "market data"
    description = "Connect to a provisioned Gemini FIX market-data session using environment configuration."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.GeminiFixLiveCaptureApp")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runLocalFixBridge") {
    group = "market data"
    description = "Serve real Coinbase public WebSocket data through a localhost Gemini-shaped FIX session."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.LocalFixBridgeApp")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("benchmarkLocalFixPipeline") {
    group = "market data"
    description = "Measure single-worker Coinbase JSON to local FIX to normalized-event throughput."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.akshit.marketdata.ingestion.LocalFixPipelineBenchmarkApp")
    workingDir = rootProject.projectDir
}
