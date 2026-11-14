/**
 * Backup: every night a dump of the books database and the new bill attachments go to an Object-Locked S3 bucket
 * in Mumbai. Runs are recorded for the admin, the year-end checklist and the auditor pack. The live database stays
 * on Neon.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Backup",
        allowedDependencies = {"shared :: storage", "shared :: whatsapp"})
package com.vaabhi.backup;
