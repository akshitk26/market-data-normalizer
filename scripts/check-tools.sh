#!/usr/bin/env bash
set -euo pipefail

echo "Java:"
java -version

echo
echo "C++ compiler:"
clang++ --version

echo
echo "Optional tools:"
command -v cmake >/dev/null && cmake --version || echo "cmake not installed; native C++ build is optional for the first milestone"
command -v protoc >/dev/null && protoc --version || echo "protoc not installed globally; Gradle downloads protoc for Java generation"
