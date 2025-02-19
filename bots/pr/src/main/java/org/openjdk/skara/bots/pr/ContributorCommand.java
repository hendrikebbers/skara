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
package org.openjdk.skara.bots.pr;

import org.openjdk.skara.email.EmailAddress;
import org.openjdk.skara.host.*;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class ContributorCommand implements CommandHandler {
    private static final Pattern commandPattern = Pattern.compile("^(add|remove)\\s+(.*?\\s+<\\S+>)$");

    @Override
    public void handle(PullRequest pr, CensusInstance censusInstance, Path scratchPath, String args, Comment comment, List<Comment> allComments, PrintWriter reply) {
        if (!comment.author().equals(pr.getAuthor())) {
            reply.println("Only the author (@" + pr.getAuthor().userName() + ") is allowed to issue the `contributor` command.");
            return;
        }

        var matcher = commandPattern.matcher(args);
        if (!matcher.matches()) {
            reply.println("Syntax: `/contributor (add|remove) Full Name <email@address>`");
            return;
        }

        var contributor = EmailAddress.parse(matcher.group(2));
        switch (matcher.group(1)) {
            case "add":
                reply.println(Contributors.addContributorMarker(contributor));
                reply.println("Contributor `" + contributor.toString() + "` successfully added.");
                break;
            case "remove":
                var existing = new HashSet<>(Contributors.contributors(pr.repository().host().getCurrentUserDetails(), allComments));
                if (existing.contains(contributor)) {
                    reply.println(Contributors.removeContributorMarker(contributor));
                    reply.println("Contributor `" + contributor.toString() + "` successfully removed.");
                } else {
                    reply.println("Contributor `" + contributor.toString() + "` was not found.");
                    break;
                }
                break;
        }
    }

    @Override
    public String description() {
        return "adds or removes additional contributors for a PR";
    }
}
