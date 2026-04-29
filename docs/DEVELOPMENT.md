# Development

## Build Commands

```bash
# Build the distribution (creates build/install/javelin/)
./gradlew installDist

# Build a fat JAR (single executable JAR with all dependencies)
./gradlew fatJar

# Run tests
./gradlew test

# Clean build
./gradlew clean build

# Build distribution archives (zip + tar)
./gradlew distZip distTar

# Build Chocolatey package
./gradlew chocopack

# Update Homebrew formula version and SHA
./gradlew updateHomebrew

# Update Scoop manifest version and SHA
./gradlew updateScoop
```
