# FastPlace for Horizon 26.3

Native Rust source mod for Horizon 26.3. It uses the same plugin-message protocol as the Fabric 26.2 client mod and the LifestealCore Paper plugin:

- Client to server: empty `fastplace:hello` custom payload after login.
- Server to client: `fastplace:state` custom payload containing one byte (`0` or `1`).
- The local fast-place state is reset on login/disconnect and is enabled only by the server response.
- When enabled, Horizon's client use delay is reset each game tick.

Paper remains on 26.2. ViaVersion must translate the Horizon 26.3 connection to the Paper 26.2 server. The server plugin still decides whether FastPlace is enabled; the client does not expose a local toggle.

## Package

Run `python3 package.py`. This creates `dist/fastplace-1.0.0.hnmod`.

A native `.hnmod` is a source bundle, not a Fabric JAR or a standalone executable. Horizon's native-mod system compiles it against the exact pinned Horizon and horizon-mixins revisions. Native mods are distributed through the Horizon native-mod registry after review; this package is prepared for that process.

The pinned source-mixin target and API are version-specific. Updating to another Horizon release requires checking the target source and regenerating the compatibility pin. This is not a universal all-version binary.
