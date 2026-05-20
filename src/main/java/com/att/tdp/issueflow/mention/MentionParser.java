package com.att.tdp.issueflow.mention;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MentionParser {

    private static final Pattern MENTION = Pattern.compile("\\B@([A-Za-z0-9_]{3,50})\\b");

    private MentionParser() {}

    /** Returns lowercase usernames in encounter order; duplicates are deduplicated. */
    public static Set<String> extractUsernames(String content) {
        Set<String> usernames = new LinkedHashSet<>();
        Matcher m = MENTION.matcher(content);
        while (m.find()) {
            usernames.add(m.group(1).toLowerCase());
        }
        return usernames;
    }
}
