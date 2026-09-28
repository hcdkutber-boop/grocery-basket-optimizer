# Development signing key

The Android spike uses a repository-pinned **development-only** signing key so successive
test APKs can be installed as upgrades instead of receiving a new GitHub Actions debug
certificate on every run.

Certificate SHA-256:

`08:31:5C:0E:4C:7E:C0:A0:FD:60:45:65:04:01:81:25:A4:9A:27:06:CA:69:9A:D5:A3:81:24:9B:84:98:19:32`

This private key is intentionally public and MUST NEVER be used for a production or store
release. Before any production distribution, replace it with a private release key stored
outside the repository.
