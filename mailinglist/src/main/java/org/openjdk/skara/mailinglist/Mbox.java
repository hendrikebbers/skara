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
package org.openjdk.skara.mailinglist;

import org.openjdk.skara.email.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Mbox {
    private final static Logger log = Logger.getLogger("org.openjdk.skara.mailinglist");

    private final static Pattern mboxMessagePattern = Pattern.compile(
            "^\\R^(From (?:.(?!^\\R^From ))*)", Pattern.MULTILINE | Pattern.DOTALL);
    private final static DateTimeFormatter ctimeFormat = DateTimeFormatter.ofPattern(
            "EEE LLL dd HH:mm:ss yyyy", Locale.US);
    private final static Pattern fromStringEncodePattern = Pattern.compile("^(>*From )", Pattern.MULTILINE);
    private final static Pattern fromStringDecodePattern = Pattern.compile("^>(>*From )", Pattern.MULTILINE);
    private final static Pattern encodeQuotedPrintablePattern = Pattern.compile("([^\\x00-\\x7f]+)");
    private final static Pattern decodedQuotedPrintablePattern = Pattern.compile("=\\?utf-8\\?b\\?(.*?)\\?=");

    private static List<Email> splitMbox(String mbox) {
        var messages = mboxMessagePattern.matcher(mbox).results()
                                         .map(match -> match.group(1))
                                         .collect(Collectors.toList());
        return messages.stream()
                       .filter(message -> message.length() > 0)
                       .map(Mbox::decodeFromStrings)
                       .map(Mbox::decodeQuotedPrintable)
                       .map(Email::parse)
                       .collect(Collectors.toList());
    }

    private static String encodeFromStrings(String body) {
        var fromStringMatcher = fromStringEncodePattern.matcher(body);
        return fromStringMatcher.replaceAll(">$1");
    }

    private static String decodeFromStrings(String body) {
        var fromStringMatcher = fromStringDecodePattern.matcher(body);
        return fromStringMatcher.replaceAll("$1");
    }

    private static String encodeQuotedPrintable(String raw) {
        var quoteMatcher = encodeQuotedPrintablePattern.matcher(raw);
        return quoteMatcher.replaceAll(mo -> "=?utf-8?b?" + Base64.getEncoder().encodeToString(String.valueOf(mo.group(1)).getBytes(StandardCharsets.UTF_8)) + "?=");
    }

    private static String decodeQuotedPrintable(String raw) {
        var quotedMatcher = decodedQuotedPrintablePattern.matcher(raw);
        return quotedMatcher.replaceAll(mo -> new String(Base64.getDecoder().decode(mo.group(1)), StandardCharsets.UTF_8));
    }

    public static List<Conversation> parseMbox(String mbox) {
        var emails = splitMbox(mbox);
        var idToMail = emails.stream().collect(Collectors.toMap(Email::id, Function.identity(), (a, b) -> a));
        var idToConversation = idToMail.values().stream()
                                       .filter(email -> !email.hasHeader("In-Reply-To"))
                                       .collect(Collectors.toMap(Email::id, Conversation::new));

        for (var email : emails) {
            if (email.hasHeader("In-Reply-To")) {
                var inReplyTo = EmailAddress.parse(email.headerValue("In-Reply-To"));
                if (!idToMail.containsKey(inReplyTo)) {
                    log.info("Can't find parent: " + inReplyTo + " - discarding");
                } else {
                    var parent = idToMail.get(inReplyTo);
                    if (!idToConversation.containsKey(inReplyTo)) {
                        log.info("Can't find conversation: " + inReplyTo + " - discarding");
                    } else {
                        var conversation = idToConversation.get(inReplyTo);
                        conversation.addReply(parent, email);
                        idToConversation.put(email.id(), conversation);
                    }
                }
            }
        }

        return idToConversation.values().stream()
                               .distinct()
                               .collect(Collectors.toList());
    }

    public static String fromMail(Email mail) {
        var mboxString = new StringWriter();
        var mboxMail = new PrintWriter(mboxString);

        mboxMail.println();
        mboxMail.println("From " + mail.sender().address() + "  " + mail.date().format(ctimeFormat));
        mboxMail.println("From: " + mail.author().toObfuscatedString());
        if (!mail.author().equals(mail.sender())) {
            mboxMail.println("Sender: " + mail.sender().toObfuscatedString());
        }
        if (!mail.recipients().isEmpty()) {
            mboxMail.println("To: " + mail.recipients().stream()
                                          .map(EmailAddress::toString)
                                          .collect(Collectors.joining(", ")));
        }
        mboxMail.println("Date: " + mail.date().format(DateTimeFormatter.RFC_1123_DATE_TIME));
        mboxMail.println("Subject: " + mail.subject());
        mboxMail.println("Message-Id: " + mail.id());
        mail.headers().forEach(header -> mboxMail.println(header + ": " + mail.headerValue(header)));
        mboxMail.println();
        mboxMail.println(encodeFromStrings(mail.body()));

        return encodeQuotedPrintable(mboxString.toString());
    }
}
