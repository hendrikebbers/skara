/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.webrev;

import org.openjdk.skara.vcs.*;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;

public class Webrev {
    private static final String ANCNAV_HTML = "navigation.html";
    private static final String ANCNAV_JS = "navigation.js";

    private static final String ICON = "nanoduke.ico";
    private static final String CSS = "style.css";

    public static class RequiredBuilder {
        private final ReadOnlyRepository repository;

        RequiredBuilder(ReadOnlyRepository repository) {
            this.repository = repository;
        }

        public Builder output(Path path) {
            return new Builder(repository, path);
        }
    }

    public static class Builder {
        private final ReadOnlyRepository repository;
        private final Path output;
        private String title = "webrev";
        private String username;
        private String upstream;
        private String pullRequest;
        private String branch;
        private String issue;
        private String version;

        Builder(ReadOnlyRepository repository, Path output) {
            this.repository = repository;
            this.output = output;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder upstream(String upstream) {
            this.upstream = upstream;
            return this;
        }

        public Builder pullRequest(String pullRequest) {
            this.pullRequest = pullRequest;
            return this;
        }

        public Builder branch(String branch) {
            this.branch = branch;
            return this;
        }

        public Builder issue(String issue) {
            this.issue = issue;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public void generate(Hash tailEnd) throws IOException {
            generate(tailEnd, null);
        }

        public void generate(Hash tailEnd, Hash head) throws IOException {
            if (!Files.exists(output)) {
                Files.createDirectory(output);
            }

            copyResource(ANCNAV_HTML);
            copyResource(ANCNAV_JS);
            copyResource(CSS);
            copyResource(ICON);

            var diff = head == null ? repository.diff(tailEnd) : repository.diff(tailEnd, head);
            var patchFile = output.resolve(Path.of(title).getFileName().toString() + ".patch");
            var patches = diff.patches();

            var modified = new ArrayList<Integer>();
            for (var i = 0; i < patches.size(); i++) {
                var patch = patches.get(i);
                if (patch.status().isModified() || patch.status().isRenamed() || patch.status().isCopied()) {
                    modified.add(i);
                }
            }

            var navigations = new LinkedList<Navigation>();
            for (var i = 0; i < modified.size(); i++) {
                Path prev = null;
                Path next = null;
                if (i != 0) {
                    prev = patches.get(modified.get(i - 1)).target().path().get();
                }
                if (i != modified.size() - 1) {
                    next = patches.get(modified.get(i + 1)).target().path().get();
                }
                navigations.addLast(new Navigation(prev, next));
            }

            var fileViews = new ArrayList<FileView>();
            for (var patch : patches) {
                var status = patch.status();
                if (status.isModified() || status.isRenamed() || status.isCopied()) {
                    var nav = navigations.removeFirst();
                    fileViews.add(new ModifiedFileView(repository, tailEnd, head, patch, output, nav));
                } else if (status.isAdded()) {
                    fileViews.add(new AddedFileView(repository, head, patch, output));
                } else if (status.isDeleted()) {
                    fileViews.add(new RemovedFileView(repository, tailEnd, patch, output));
                }
            }

            var total = fileViews.stream().map(FileView::stats).mapToInt(WebrevStats::total).sum();
            var stats = new WebrevStats(diff.added(), diff.removed(), diff.modified(), total);

            try (var w = Files.newBufferedWriter(output.resolve("index.html"))) {
                var index = new IndexView(fileViews,
                                          title,
                                          username,
                                          upstream,
                                          branch,
                                          pullRequest,
                                          issue,
                                          version,
                                          tailEnd,
                                          output.relativize(patchFile),
                                          stats);
                index.render(w);

            }

            try (var totalPatch = FileChannel.open(patchFile, CREATE, WRITE)) {
                for (var patch : patches) {
                    var originalPath = patch.status().isDeleted() ? patch.source().path() : patch.target().path();
                    var patchPath = output.resolve(originalPath.get().toString() + ".patch");

                    try (var patchFragment = FileChannel.open(patchPath, READ)) {
                        var size = patchFragment.size();
                        var n = 0;
                        while (n < size) {
                            n += patchFragment.transferTo(n, size, totalPatch);
                        }
                    }
                }
            }
        }

        private void copyResource(String name) throws IOException {
            var stream = this.getClass().getResourceAsStream("/" + name);
            if (stream == null) {
                var classPath = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
                var extPath = classPath.getParent().resolve("resources").resolve(name);
                stream = new FileInputStream(extPath.toFile());
            }
            Files.copy(stream, output.resolve(name));
        }
    }

    public static RequiredBuilder repository(ReadOnlyRepository repository) {
        return new RequiredBuilder(repository);
    }

    static String relativeTo(Path from, Path to) {
        var relative = from.relativize(to);
        return relative.subpath(1, relative.getNameCount()).toString();
    }

    static String relativeToCSS(Path out, Path file) {
        return relativeTo(file, out.resolve(CSS));
    }

    static String relativeToIndex(Path out, Path file) {
        return relativeTo(out.resolve("index.html"), file);
    }

    static String relativeToAncnavHTML(Path out, Path file) {
        return relativeTo(file, out.resolve(ANCNAV_HTML));
    }

    static String relativeToAncnavJS(Path out, Path file) {
        return relativeTo(file, out.resolve(ANCNAV_JS));
    }
}
