# Arena Native KV Store

A high-performance, zero-allocation key-value store implementation using Java's Foreign Function & Memory (FFM) API with off-heap memory storage.

## Project Setup

### Build Configuration

This project uses **Gradle 8.7** with Java 21 toolchain and the following configuration:

#### Key Features:
- **Java 21 Toolchain**: Configured to use Java 21 for native features like FFM API
- **FFM API Support**: Enabled with `--enable-preview` and `--enable-native-access=ALL-UNNAMED` flags
- **Zero-Allocation Design**: Off-heap memory storage for hot paths
- **JMH Benchmarking**: Integrated JMH (Java Microbenchmark Harness) for performance testing

### Dependencies

#### Testing
- **JUnit 5 (Jupiter)** 5.10.0 - Modern testing framework
- **AssertJ** 3.24.1 - Fluent assertions

#### Core
- **OpenHFT Zero-Allocation-Hashing** 0.16 - Zero-allocation hash functions

#### Benchmarking
- **JMH** 1.37 - Microbenchmarking framework

### Building the Project

```bash
# Run tests
./gradlew test

# Run JMH benchmarks
./gradlew jmh

# Build the project
./gradlew build

# Clean build artifacts
./gradlew clean
```

### Compiler Flags

The build is configured with the following JVM flags for FFM API:

**Compilation:**
- `--enable-preview` - Enables preview features (FFM API is a preview feature in Java 21)
- `-Xlint:preview` - Shows warnings about preview features

**Runtime (Test & JMH):**
- `--enable-preview` - Enables preview features (FFM API is preview in Java 21)
- `--enable-native-access=ALL-UNNAMED` - Allows unsafe native access for FFM API

### Key Non-Functional Constraints

- **Zero Allocations (Hot Paths)**: `get()` and `put()` must allocate 0 bytes on JVM heap
- **Atomic Bump Allocator**: Thread-safe off-heap memory allocation via `AtomicLong`
- **Deterministic GC**: No measurable GC pauses with 10M entries
- **Thread Safety**: Striped locks for concurrent access
- **Memory Safety**: FFM API only (no `Unsafe`)
- **Collision Resolution**: Linear probing + byte equality checks
