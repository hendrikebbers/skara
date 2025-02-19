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
package org.openjdk.skara.vcs;

import org.openjdk.skara.vcs.git.GitRepository;
import org.openjdk.skara.vcs.hg.HgRepository;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

public interface Repository extends ReadOnlyRepository {
    Repository init() throws IOException;
    void checkout(Hash h, boolean force) throws IOException;
    void checkout(Branch b, boolean force) throws IOException;
    Hash fetch(URI uri, String refspec) throws IOException;
    void pushAll(URI uri) throws IOException;
    void push(Hash hash, URI uri, String ref, boolean force) throws IOException;
    void push(Branch branch, String remote, boolean setUpstream) throws IOException;
    void clean() throws IOException;
    Repository reinitialize() throws IOException;
    void squash(Hash h) throws IOException;
    void add(Path... files) throws IOException;
    void remove(Path... files) throws IOException;
    void pull() throws IOException;
    void pull(String remote) throws IOException;
    void pull(String remote, String refspec) throws IOException;
    default void addremove(Path... files) throws IOException {
        var exists = new ArrayList<Path>();
        var missing = new ArrayList<Path>();
        for (var file : files) {
            if (Files.exists(file)) {
                exists.add(file);
            } else {
                missing.add(file);
            }
        }

        if (!exists.isEmpty()) {
            add(exists.toArray(new Path[0]));
        }

        if (!missing.isEmpty()) {
            remove(missing.toArray(new Path[0]));
        }
    }
    Hash commit(String message,
                String authorName,
                String authorEmail) throws IOException;
    Hash commit(String message,
                String authorName,
                String authorEmail,
                Instant timestamp) throws IOException;
    Hash commit(String message,
                String authorName,
                String authorEmail,
                String committerName,
                String committerEmail) throws IOException;
    Hash commit(String message,
                String authorName,
                String authorEmail,
                Instant authorDate,
                String committerName,
                String committerEmail,
                Instant committerDate) throws IOException;
    Hash amend(String message,
               String authorName,
               String authorEmail) throws IOException;
    Hash amend(String message,
               String authorName,
               String authorEmail,
               String committerName,
               String committerEmail) throws IOException;
    Tag tag(Hash hash, String tagName, String message, String authorName, String authorEmail) throws IOException;
    Branch branch(Hash hash, String branchName) throws IOException;
    void rebase(Hash hash, String committerName, String committerEmail) throws IOException;
    void merge(Hash hash) throws IOException;
    void merge(Hash hash, String strategy) throws IOException;
    void addRemote(String name, String path) throws IOException;
    void setPaths(String remote, String pullPath, String pushPath) throws IOException;
    void apply(Diff diff, boolean force) throws IOException;
    void copy(Path from, Path to) throws IOException;
    void move(Path from, Path to) throws IOException;
    default void setPaths(String remote, String pullPath) throws IOException {
        setPaths(remote, pullPath, null);
    }

    default void push(Hash hash, URI uri, String ref) throws IOException {
        push(hash, uri, ref, false);
    }

    default ReadOnlyRepository readOnly() {
        return this;
    }

    static Repository init(Path p, VCS vcs) throws IOException {
        switch (vcs) {
            case GIT:
                return new GitRepository(p).init();
            case HG:
                return new HgRepository(p).init();
            default:
                throw new IllegalArgumentException("Invalid enum value: " + vcs);
        }
    }

    static Optional<Repository> get(Path p) throws IOException {
        var r = GitRepository.get(p);
        if (r.isPresent()) {
            return r;
        }
        return HgRepository.get(p);
    }

    static boolean exists(Path p) throws IOException {
        return get(p).isPresent();
    }

    static Repository materialize(Path p, URI remote, String ref) throws IOException {
        var localRepo = remote.getPath().endsWith(".git") ?
            Repository.init(p, VCS.GIT) : Repository.init(p, VCS.HG);
        if (!localRepo.exists()) {
            localRepo.init();
        } else if (!localRepo.isHealthy()) {
            localRepo.reinitialize();
        } else {
            try {
                localRepo.clean();
            } catch (IOException e) {
                localRepo.reinitialize();
            }
        }

        var baseHash = localRepo.fetch(remote, ref);

        try {
            localRepo.checkout(baseHash, true);
        } catch (IOException e) {
            localRepo.reinitialize();
            baseHash = localRepo.fetch(remote, ref);
            localRepo.checkout(baseHash, true);
        }

        return localRepo;
    }

    static Repository clone(URI from) throws IOException {
        var to = Path.of(from.getPath()).getFileName();
        if (to.toString().endsWith(".git")) {
            to = Path.of(to.toString().replace(".git", ""));
        }
        return clone(from, to);
    }

    static Repository clone(URI from, Path to) throws IOException {
        return from.getPath().toString().endsWith(".git") ?
            GitRepository.clone(from, to) : HgRepository.clone(from, to);
    }
}
