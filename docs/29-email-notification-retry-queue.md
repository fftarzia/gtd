# GTDdo notification email retry queue

GTDdo queues Inbox and other notification emails in PostgreSQL before sending
them through the existing Nodemailer SMTP service (including Resend). No Redis,
Apprise, external worker, or additional Portainer service is needed.

## Delivery behavior

- When email is enabled and the relevant per-event preference is enabled, a
  delivery is stored with a unique source key and the exact email content.
  Recreated task/project due notifications are deduplicated by user, event,
  entity UID and UTC date; other events use the notification's own UID.
- An Inbox capture and its email outbox row are committed in the **same
  PostgreSQL transaction**. SMTP outages do not block a capture.
- The app attempts to send newly committed emails promptly. The existing task
  scheduler retries due emails every minute (including after restarts).
- Retries use delays of 1, 5, 15, 30, 60, 120, and 240 minutes after successive
  failures, for a total of **8 attempts**.
- A delivery stuck in "sending" for 20 minutes is released after a worker
  crash. Rows remain in PostgreSQL across container restarts and redeploys.
- Sent rows are retained for 7 days; permanently failed rows for 30 days.
  Failed rows require manual investigation and are not automatically restarted
  after exhausting the retry budget.
- The Inbox notification body is the *verbatim original note*, including all
  line breaks. No batching, summarization, or automatic reformatting occurs.

Delivery is **at least once**, not mathematically exactly once. If the SMTP
server accepts a message and the container crashes before GTDdo records
success, a subsequent retry can produce a duplicate email. Atomic claims and
unique source keys otherwise prevent concurrent or repeated dispatch.

SMTP secrets are not stored in the outbox. The outbox *does* contain each
email's subject, recipient and body, so database backups should be protected.
Do not log or expose delivery payloads in public diagnostics.

## Operations

The new table is created by the additive migration
backend/migrations/20261123000000-create-notification-email-deliveries.js
when the updated image starts. Existing Inbox data and volumes are not changed.

Check queue status as a database administrator with:

    SELECT status, COUNT(*) FROM notification_email_deliveries
    GROUP BY status ORDER BY status;

Inspect permanently failed deliveries with:

    SELECT id, kind, attempts, last_error, created_at
    FROM notification_email_deliveries
    WHERE status = 'failed' ORDER BY created_at DESC LIMIT 20;

There is no automatic replay of messages that failed under GTDdo 0.2 before
this queue existed. The normal notification test email is still sent directly
and is not queued. For a failover test, disable SMTP temporarily only in a
nonproduction instance, create a test Inbox item, restore SMTP, and verify
that it is eventually delivered.

Do not change the project's start.md without explicit owner approval.
