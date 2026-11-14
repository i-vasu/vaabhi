package com.vaabhi.backup;

import java.util.regex.Pattern;

/** A Postgres major version, read from {@code pg_dump --version} or from {@code SHOW server_version_num}. */
record PgVersion(int major) {

    private static final Pattern CLIENT_VERSION = Pattern.compile("(\\d{1,5})\\.\\d");

    static PgVersion ofClient(String pgDumpVersionLine) {
        var matcher = CLIENT_VERSION.matcher(pgDumpVersionLine == null ? "" : pgDumpVersionLine);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a pg_dump version: " + pgDumpVersionLine);
        }
        return new PgVersion(Integer.parseInt(matcher.group(1)));
    }

    static PgVersion ofServerNum(int serverVersionNum) {
        if (serverVersionNum < 10000) {
            throw new IllegalArgumentException("Not a Postgres server version number: " + serverVersionNum);
        }
        return new PgVersion(serverVersionNum / 10000);
    }
}
