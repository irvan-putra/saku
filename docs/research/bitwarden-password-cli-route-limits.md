# Bitwarden: what the official pages say about software adding logins to a vault

Status: Bitwarden help pages and terms re-read on 2026-11-16. Documentation reading only; nothing was tested against a live account. This covers the routes listed below, not every possible kind of automation.

## Routes examined
- Public API: the page says it "does not allow for management of individual vault items" and points to a separate Vault Management API for that. Its authentication example is for organization management. Source: https://bitwarden.com/help/public-api/ The Vault Management API itself was not examined here.
- Command-line tool (CLI) with a personal API key: the personal API key page says the key is suitable for automated workflows and for accounts whose two-step login method the CLI does not support. The CLI page says that to use many commands you must then decrypt your data with `unlock`, and that "a master password will be required to `unlock` the client." The personal API key page says the key is not a substitute for the master password. The CLI page names exceptions: members of an organization using Key Connector, and users in an organization using trusted devices (who cannot access data through the CLI). Sources: https://bitwarden.com/help/cli/ and https://bitwarden.com/help/personal-api-key/
- Key rotation: rotating the personal API key "will invalidate your previous key and all active sessions using that key." Same page.
- Secrets Manager machine accounts: documented as access tokens scoped to assigned projects. The Secrets Manager overview lists a web app, CLI and SDK as its clients and does not list a phone app or autofill. Whether Secrets Manager items can be used by a phone's autofill was not tested. Sources: https://bitwarden.com/help/machine-accounts/ and https://bitwarden.com/help/secrets-manager-overview/

## Account terms
Bitwarden's terms say a human must create an account, that "Accounts registered by 'bots' or other automated methods are not permitted," and that one person or entity may not have more than one free account. Source: https://bitwarden.com/terms/ The terms do not say whether a person may direct software to use their own account. This note takes no position on that.

## What this does and does not show
For the password-manager CLI route examined above, the pages say a master password is needed to unlock before items can be created or edited, apart from the listed organization exceptions. This note found no documented write-only credential for that route. It does not rule out other routes, other Bitwarden features, or changes after this date. Other approaches (a separate local database, a companion app) were not evaluated.
