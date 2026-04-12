package com.javelin.core.execution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;

/**
 * Offline Instrumenter
 *
 * Pre-instruments compiled .class files using JaCoCo's offline instrumentation API,
 * eliminating the need for a -javaagent flag at test runtime. This avoids conflicts
 * with other Java agents (e.g., Mockito-inline/ByteBuddy) that also register
 * ClassFileTransformers.
 *
 * Strategy:
 *  1. Copy original .class files from targetClassesDir into a temp directory
 *  2. Instrument each .class file in the temp directory in-place
 *  3. Return the temp directory path — callers use it on the classpath instead of the original
 *
 * The original target directory is never modified. No backup/restore is needed.
 *
 * The JaCoCo runtime JAR must be on the forked JVM's classpath (not as -javaagent).
 * JaCoCo's runtime auto-initializes and RT.getAgent() works identically to online mode,
 * so JavelinTestListener requires no changes.
 */
public class OfflineInstrumenter {

    /**
     * Copies all .class files from sourceDir into a fresh temp directory and
     * instruments each one using JaCoCo's offline Instrumenter.
     *
     * @param sourceDir the directory containing the original compiled .class files
     * @return path to the temp directory containing the instrumented .class files
     * @throws IOException if file I/O fails or instrumentation fails
     */
    public Path instrumentIntoTempDir(Path sourceDir) throws IOException {
        Path tempDir = Files.createTempDirectory("javelin-offline-");

        // Register shutdown hook to clean up temp dir
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteRecursively(tempDir);
            } catch (IOException ignored) {
            }
        }));

        Instrumenter instrumenter = new Instrumenter(new OfflineInstrumentationAccessGenerator());

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceDir.relativize(dir);
                Files.createDirectories(tempDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceDir.relativize(file);
                Path dest = tempDir.resolve(relative);

                if (file.getFileName().toString().endsWith(".class")) {
                    try (InputStream in = Files.newInputStream(file)) {
                        String className = relative.toString()
                                .replace(".class", "")
                                .replace(java.io.File.separatorChar, '.')
                                .replace('/', '.');
                        byte[] instrumented = instrumenter.instrument(in, className);
                        Files.write(dest, instrumented);
                    } catch (IOException e) {
                        // If a class cannot be instrumented (e.g., already instrumented,
                        // or a synthetic class), copy it as-is and warn.
                        System.err.println("      WARNING: Could not instrument " + relative + " — copying as-is: " + e.getMessage());
                        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    // Non-.class resources (e.g., service files) — copy as-is
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return tempDir;
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
