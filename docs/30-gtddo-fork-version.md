# GTDdo fork version

The upstream tududi application version and the GTDdo fork release number are
separate values:

- `package.json` -> `version`: original tududi version, currently `v2.1.1`.
- `package.json` -> `forkVersion`: local GTDdo release number, initially `0.3`.

The backend exposes both through `GET /api/version` (also
`GET /api/v1/version`), as `version` and `forkVersion`. The About page shows
the upstream version first, with `Fork <forkVersion>` directly underneath.

## Each GTDdo Docker release

1. Update only the `forkVersion` field in the root `package.json` to match
   the **new GTDdo release number** (for example `0.4`).
2. Keep the upstream `version` unchanged unless the underlying upstream
   tududi code has actually been upgraded.
3. Run tests and build a new Docker image tagged `gtddo:<forkVersion>`.
4. Deploy the new image through Portainer, retaining the existing application
   settings and persistent PostgreSQL and uploads volumes.

The displayed fork number reflects the **code packaged in the running
container**, not the currently selected image tag in Portainer. Renaming an
image tag without rebuilding or updating `package.json` cannot change the
About page. Code-only commits do not bump the fork release number until they
are packaged as a new release.

Changes to the infrastructure's `start.md` require explicit owner approval.
