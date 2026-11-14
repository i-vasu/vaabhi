package com.vaabhi.backup;

import java.io.IOException;
import java.nio.file.Path;

/** Writes a dump of the books database to {@code into}. The integration tests replace the real one with a stub. */
interface DatabaseDumper {

    void dump(Path into) throws IOException;

    /** The {@code pg_dump} major version, for the check against the server. */
    int clientMajor();
}
