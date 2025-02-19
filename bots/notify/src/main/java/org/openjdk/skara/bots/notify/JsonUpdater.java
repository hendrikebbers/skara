/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.skara.bots.notify;

import org.openjdk.skara.host.HostedRepository;
import org.openjdk.skara.json.*;
import org.openjdk.skara.vcs.Commit;
import org.openjdk.skara.vcs.openjdk.*;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class JsonUpdater implements UpdateConsumer {
    private final Path path;
    private final String version;
    private final String defaultBuild;

    JsonUpdater(Path path, String version, String defaultBuild) {
        this.path = path;
        this.version = version;
        this.defaultBuild = defaultBuild;
    }

    private JSONObject commitToChanges(HostedRepository repository, Commit commit, String build) {
        var ret = JSON.object();
        ret.put("url",  repository.getWebUrl(commit.hash()).toString()); //FIXME
        ret.put("version", version);
        ret.put("build", build);

        var parsedMessage = CommitMessageParsers.v1.parse(commit);
        var issueIds = JSON.array();
        for (var issue : parsedMessage.issues()) {
            issueIds.add(JSON.of(issue.id()));
        }
        ret.put("issue", issueIds);
        ret.put("user", commit.author().name());
        ret.put("date", commit.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss +0000")));

        return ret;
    }

    private JSONObject issuesToChanges(HostedRepository repository, List<Issue> issues, String build) {
        var ret = JSON.object();
        ret.put("version", version);
        ret.put("build", build);

        var issueIds = JSON.array();
        for (var issue : issues) {
            issueIds.add(JSON.of(issue.id()));
        }

        ret.put("issue", issueIds);

        return ret;
    }

    @Override
    public void handleCommits(HostedRepository repository, List<Commit> commits) {
        try (var writer = new JsonUpdateWriter(path, repository.getName())) {
            for (var commit : commits) {
                var json = commitToChanges(repository, commit, defaultBuild);
                writer.write(json);
            }
        }
    }

    @Override
    public void handleTagCommits(HostedRepository repository, List<Commit> commits, OpenJDKTag tag) {
        var build = String.format("b%02d", tag.buildNum());
        try (var writer = new JsonUpdateWriter(path, repository.getName())) {
            var issues = new ArrayList<Issue>();
            for (var commit : commits) {
                var parsedMessage = CommitMessageParsers.v1.parse(commit);
                issues.addAll(parsedMessage.issues());
            }
            var json = issuesToChanges(repository, issues, build);
            writer.write(json);
        }
    }
}
