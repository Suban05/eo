/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.eolang.maven;

import com.jcabi.log.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.cactoos.Func;
import org.cactoos.iterable.Mapped;
import org.cactoos.list.ListOf;
import org.cactoos.text.Joined;
import org.eolang.maven.util.Walk;

/**
 * Find all required runtime dependencies, download
 * them from Maven Central, unpack and place to the {@code target/eo}
 * directory.
 *
 * <p>The motivation for this mojo is simple: Maven doesn't have
 * a mechanism for adding .JAR files to transpile/test classpath in
 * runtime.</p>
 *
 * @since 0.1
 */
@Mojo(
    name = "resolve",
    defaultPhase = LifecyclePhase.PROCESS_SOURCES,
    threadSafe = true
)
public final class ResolveMojo extends SafeMojo {

    /**
     * The directory where to resolve to.
     */
    public static final String DIR = "5-resolve";

    /**
     * Transitive dependency extractor. It's a strategy pattern for extracting transitive
     * dependencies for a particular artifact.
     *
     * @checkstyle MemberNameCheck (7 lines)
     */
    @SuppressWarnings({"PMD.ImmutableField", "PMD.LongVariable"})
    private Func<Dependency, Iterable<Dependency>> transitiveStrategy =
        dependency -> new DcsDepgraph(
            this.project,
            this.session,
            this.manager,
            this.targetDir.toPath()
                .resolve(ResolveMojo.DIR)
                .resolve("dependencies-info"),
            dependency
        );

    @Override
    public void exec() throws IOException {
        final Collection<Dependency> deps = this.deps();
        final Path target = this.targetDir.toPath().resolve(ResolveMojo.DIR);
        for (final Dependency dep : deps) {
            String classifier = dep.getClassifier();
            if (classifier == null || classifier.isEmpty()) {
                classifier = "-";
            }
            final Path dest = this.cleanPlace(
                target
                    .resolve(dep.getGroupId())
                    .resolve(dep.getArtifactId())
                    .resolve(classifier),
                dep.getVersion()
            );
            if (Files.exists(dest)) {
                Logger.debug(
                    this, "Dependency %s already resolved and exists in %[file]s",
                    new Coordinates(dep), dest
                );
                continue;
            }
            this.central.accept(dep, dest);
            final int files = new Walk(dest).size();
            if (files == 0) {
                Logger.warn(
                    this, "No new files after unpacking of %s!",
                    new Coordinates(dep)
                );
            } else {
                Logger.info(
                    this, "Found %d new file(s) after unpacking of %s",
                    files, new Coordinates(dep)
                );
            }
        }
        if (deps.isEmpty()) {
            Logger.debug(this, "No new dependencies unpacked");
        } else {
            Logger.info(
                this,
                "New %d dependenc(ies) unpacked to %[file]s: %s",
                deps.size(), target,
                new Joined(
                    ", ",
                    new Mapped<>(
                        dep -> String.format(
                            "%s:%s:%s", dep.getGroupId(), dep.getArtifactId(),
                            dep.getVersion()
                        ),
                        deps
                    )
                )
            );
        }
    }

    /**
     * Checks if dependency is runtime.
     * @param dep Dependency
     * @return True if runtime.
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static boolean isRuntime(final Dependency dep) {
        return "org.eolang".equals(dep.getGroupId())
            && "eo-runtime".equals(dep.getArtifactId());
    }

    /**
     * Returns directory where the files should be unpacked,
     * making sure there are no old files around.
     *
     * <p>Say, on the first run of "resolve" binary files from some JAR
     * get unpacked. Then, the user changes the version of the JAR
     * in the "pom.xml" (or in some .eo file) and runs the build again.
     * We don't want the old binary files to stay in the "5-resolve"
     * directory, because they are outdated. We want them to be removed
     * and only the new files from the right version of the JAR to be there.
     * This method does exactly this: it removes old files and doesn't
     * touches new ones.</p>
     *
     * @param dir The dir
     * @param version Version of dependency
     * @return Full path
     * @throws IOException If fails
     */
    private Path cleanPlace(final Path dir, final String version) throws IOException {
        final File[] subs = dir.toFile().listFiles();
        if (subs != null) {
            for (final File sub : subs) {
                final String base = sub.getName();
                if (base.equals(version)) {
                    continue;
                }
                final Path bad = dir.resolve(base);
                try (Stream<Path> walk = Files.walk(bad)) {
                    walk
                        .map(Path::toFile)
                        .sorted(Comparator.reverseOrder())
                        .forEach(File::delete);
                }
                Logger.info(
                    this,
                    "Directory %[file]s deleted because it contained wrong version files (not %s)",
                    bad, version
                );
            }
        }
        return dir.resolve(version);
    }

    /**
     * Find all deps for all Tojos.
     *
     * @return List of them
     */
    private Collection<Dependency> deps() {
        Iterable<Dependency> deps = new DcsDefault(
            this.scopedTojos(),
            this.discoverSelf,
            this.skipZeroVersions
        );
        if (this.withRuntimeDependency) {
            final Optional<Dependency> runtime = this.runtimeDependencyFromPom();
            if (runtime.isPresent()) {
                deps = new DcsWithRuntime(deps, runtime.get());
                Logger.info(
                    this,
                    "Runtime dependency added from pom with version: %s",
                    runtime.get().getVersion()
                );
            } else {
                deps = new DcsWithRuntime(deps);
            }
        } else {
            deps = new DcsWithoutRuntime(deps);
        }
        if (!this.ignoreVersionConflicts) {
            deps = new DcsUniquelyVersioned(deps);
        }
        if (!this.ignoreTransitive) {
            deps = new DcsEachWithoutTransitive(deps, this.transitiveStrategy);
        }
        return new ListOf<>(deps)
            .stream()
            .map(ResolveMojo.Wrap::new)
            .sorted()
            .distinct()
            .map(ResolveMojo.Wrap::dep)
            .collect(Collectors.toList());
    }

    /**
     * Runtime dependency from pom.xml.
     *
     * @return Dependency if found.
     */
    private Optional<Dependency> runtimeDependencyFromPom() {
        final Optional<Dependency> res;
        if (this.project == null) {
            res = Optional.empty();
        } else {
            res = this.project
                .getDependencies()
                .stream()
                .filter(ResolveMojo::isRuntime)
                .findFirst();
        }
        return res;
    }

    /**
     * Wrapper for comparing.
     *
     * @since 0.1
     */
    private static final class Wrap implements Comparable<ResolveMojo.Wrap> {
        /**
         * Dependency.
         */
        private final Dependency dependency;

        /**
         * Ctor.
         *
         * @param dep Dependency
         */
        Wrap(final Dependency dep) {
            this.dependency = dep;
        }

        /**
         * Return it.
         *
         * @return The dep
         */
        public Dependency dep() {
            return this.dependency;
        }

        @Override
        public int compareTo(final ResolveMojo.Wrap wrap) {
            return new Coordinates(this.dependency).compareTo(
                new Coordinates(wrap.dependency)
            );
        }

        @Override
        public boolean equals(final Object wrap) {
            return new Coordinates(this.dependency).equals(
                new Coordinates(
                    ResolveMojo.Wrap.class.cast(wrap).dependency
                )
            );
        }

        @Override
        public int hashCode() {
            return new Coordinates(this.dependency).hashCode();
        }
    }
}
