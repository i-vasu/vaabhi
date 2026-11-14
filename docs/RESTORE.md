# Restoring the books from a backup

Do this once after the backups are set up, then once a year before the audit, and whenever a real recovery is
needed. Each backup is a full `pg_dump` of the database, so yesterday's file holds every entry up to 01:00 IST that
day. Restoring it never touches the live database.

## Restore drill

1. Download yesterday's dump. In the AceCloud console open the bucket, go to `db/<year>/<month>/` and download the
   `vaabhi-...dump` file. Or use any S3 client against the AceCloud endpoint:

   ```bash
   aws s3 cp s3://<bucket>/db/2026/11/vaabhi-2026-11-13T0100.dump ./ --endpoint-url <BACKUP_S3_ENDPOINT>
   ```

   `rclone` works the same way if that is what is installed. `<BACKUP_S3_ENDPOINT>` is the value in
   `/opt/vaabhi/.env`.

2. Start an empty Postgres 17 in Docker:

   ```bash
   docker run --rm -d --name vaabhi-restore -e POSTGRES_PASSWORD=restore -p 5544:5432 postgres:17
   ```

3. Copy the file in and restore it:

   ```bash
   docker cp vaabhi-2026-11-13T0100.dump vaabhi-restore:/tmp/
   docker exec vaabhi-restore pg_restore -U postgres --no-owner --no-privileges -d postgres /tmp/vaabhi-2026-11-13T0100.dump
   ```

4. Compare the trial balance with the live one, at the same date. Open a shell:

   ```bash
   docker exec -it vaabhi-restore psql -U postgres -d postgres
   ```

   Then run this, with a chosen date (a positive `net` is a debit, a negative one a credit):

   ```sql
   SELECT a.code, a.name, a.type,
          SUM(l.debit_rupees) - SUM(l.credit_rupees) AS net
   FROM account a
   JOIN journal_line l ON l.account_id = a.id
   JOIN journal_entry e ON e.id = l.entry_id
   WHERE e.entry_date <= DATE '2026-11-13'
   GROUP BY a.id
   HAVING SUM(l.debit_rupees) <> SUM(l.credit_rupees)
   ORDER BY a.code;

   SELECT SUM(l.debit_rupees) AS total_debits, SUM(l.credit_rupees) AS total_credits
   FROM journal_line l
   JOIN journal_entry e ON e.id = l.entry_id
   WHERE e.entry_date <= DATE '2026-11-13';
   ```

   Admin → **Accounts → Trial balance**, set the same date: the same accounts appear with the same net debit or
   credit, and the totals match. Leave the shell with `\q`.

5. Admin → **Accounts → Backups → Mark restore drill done**, with today's date and a note naming the dump used.

6. Remove the container:

   ```bash
   docker stop vaabhi-restore
   ```

## Real recovery

Ask for help before starting.

1. Create a new Neon branch (or database) for the restored data and restore into it:

   ```bash
   pg_restore --no-owner --no-privileges -d "<new neon url>" ./vaabhi-2026-11-13T0100.dump
   ```

2. Point `DATABASE_URL` in `/opt/vaabhi/.env` at the new branch.
3. Redeploy, then sign in to the admin and check the dashboard and the trial balance.
