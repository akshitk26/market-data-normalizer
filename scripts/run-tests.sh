#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home GRADLE_USER_HOME=.gradle-home ./gradlew --no-daemon test
