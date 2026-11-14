package com.vaabhi.backup;

/** Whether the backup bucket locks objects against deletion, and how strictly. */
public enum BackupLockMode {
    /** No Object Lock; the provider may not offer it (AceCloud default). */
    NONE,
    GOVERNANCE,
    COMPLIANCE
}
