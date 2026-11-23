# GTDdo SMTP notifications

GTDdo already contains a Nodemailer-based SMTP client. This fork enables it
for the per-event **Email** notification channel in Profile > Notification
Preferences. No separate Apprise instance or additional Docker service is
required.

## Docker / Portainer setup

Add these values to the existing **app** service environment. Replace the
examples with your mail provider's settings. Keep credentials as private
Portainer stack variables or another appropriate secrets mechanism; **never
commit SMTP passwords to Git**.

```yaml
environment:
  ENABLE_EMAIL: "true"
  EMAIL_SMTP_HOST: "${GTD_SMTP_HOST}"
  EMAIL_SMTP_PORT: "${GTD_SMTP_PORT}"
  EMAIL_SMTP_SECURE: "${GTD_SMTP_SECURE}"
  EMAIL_SMTP_USERNAME: "${GTD_SMTP_USERNAME}"
  EMAIL_SMTP_PASSWORD: "${GTD_SMTP_PASSWORD}"
  EMAIL_FROM_ADDRESS: "${GTD_EMAIL_FROM_ADDRESS}"
  EMAIL_FROM_NAME: "GTDdo"
```

Common SMTP settings:
- Port **465** uses implicit TLS (`EMAIL_SMTP_SECURE=true`).
- Port **587** normally uses STARTTLS (`EMAIL_SMTP_SECURE=false`).
  The SMTP server should advertise STARTTLS and support a secure connection.
- Use an app-specific SMTP credential with permission to send from the
  configured address. Do not reuse the login password of an unrelated account.

Keep your existing database, volumes, cookies, proxy and Cloudflare settings
unchanged. After redeployment, the Email column becomes available. Use its
**Test** button to check actual delivery.

## Notification preferences

- **Webapp:** notifications displayed within GTDdo's interface (bell icon).
- **Email:** sends an individual email for each event where the user enables
  Email (due/overdue tasks and projects, comments, assignments, etc.).
- **Nuova voce Inbox / New Inbox item:** separate opt-in row. Every newly
  captured Inbox item sends **one plain-text email with the complete original
  content, including line breaks**, to the account's email address.
  This is **not** a daily or weekly summary.
- Inbox notifications apply to entries created through the normal Inbox
  service, including the web UI, REST API and the MCP `add_to_inbox` tool.
  Editing an existing Inbox entry does not send another email.
- All Email preferences are **off by default**.
- Sending failures are logged and do not roll back the Inbox capture.
  Inbox email delivery currently does **not** have a persistent retry queue.
  If the SMTP provider is unavailable, a delivery can be missed.

The email sender is configured globally on the Docker app container. Each
recipient is taken from the email address of their GTDdo user account.
SMTP credentials are not displayed in the profile or returned by the
notification-status endpoint.

## Verification

1. Configure SMTP via Portainer and redeploy only the GTDdo stack.
2. Open Profile > Notification Preferences. Confirm the Email switches are
   enabled and the channel's **Test** message arrives.
3. Enable **Email** for **New Inbox item**.
4. Add a multi-line Inbox entry and confirm the individual email contains its
   original text verbatim.
5. Repeat via Claude MCP if desired.

No changes to `start.md` should be made without the infrastructure owner's
explicit approval.
