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
package org.openjdk.skara.bots.mlbridge;

import org.openjdk.skara.bot.*;
import org.openjdk.skara.email.EmailAddress;
import org.openjdk.skara.host.HostedRepository;
import org.openjdk.skara.jcheck.JCheckConfiguration;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MailingListBridgeBot implements Bot {
    private final EmailAddress emailAddress;
    private final HostedRepository codeRepo;
    private final HostedRepository archiveRepo;
    private final EmailAddress listAddress;
    private final Set<String> ignoredUsers;
    private final URI listArchive;
    private final String smtpServer;
    private final WebrevStorage webrevStorage;

    MailingListBridgeBot(EmailAddress from, HostedRepository repo, HostedRepository archive, EmailAddress list,
                         Set<String> ignoredUsers, URI listArchive, String smtpServer,
                         HostedRepository webrevStorageRepository, String webrevStorageRef,
                         Path webrevStorageBase, URI webrevStorageBaseUri) {
        emailAddress = from;
        codeRepo = repo;
        archiveRepo = archive;
        listAddress = list;
        this.ignoredUsers = ignoredUsers;
        this.listArchive = listArchive;
        this.smtpServer = smtpServer;

        this.webrevStorage = new WebrevStorage(webrevStorageRepository, webrevStorageRef, webrevStorageBase,
                                               webrevStorageBaseUri, from);
    }

    JCheckConfiguration configuration() {
        var confFile = codeRepo.getFileContents(".jcheck/conf", "master");
        return JCheckConfiguration.parse(confFile.lines().collect(Collectors.toList()));
    }

    HostedRepository codeRepo() {
        return codeRepo;
    }

    HostedRepository archiveRepo() {
        return archiveRepo;
    }

    EmailAddress emailAddress() {
        return emailAddress;
    }

    EmailAddress listAddress() {
        return listAddress;
    }

    Set<String> ignoredUsers() {
        return ignoredUsers;
    }

    URI listArchive() {
        return listArchive;
    }

    String smtpServer() {
        return smtpServer;
    }

    WebrevStorage webrevStorage() {
        return webrevStorage;
    }

    @Override
    public List<WorkItem> getPeriodicItems() {
        List<WorkItem> ret = new LinkedList<>();

        for (var pr : codeRepo.getPullRequests()) {
            ret.add(new ArchiveWorkItem(pr, this));
        }

        return ret;
    }
}
