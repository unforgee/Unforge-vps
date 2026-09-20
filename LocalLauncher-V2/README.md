# UnForge Launcher V2

This is a copied second launcher version. The original `LocalLauncher` is left unchanged.

Run `Launch-UnforgeLauncherV2.bat` to open the launcher window.

- `Start Server` starts the local client-config service on port `8088` when needed, then builds and starts the current UnForge server with `:server:app:run` on port `43594`.
- `Rebuild + Restart Server` verifies and stops the current local UnForge server, reruns the server build tasks with `--rerun-tasks`, and starts it again from the newest source and local data.
- `Start Client` builds the current r239 shaded client with `:client:shadowJar`, then starts it against `127.0.0.1:8088`.
- `Open Project Folder` opens the current project root.

The buttons use the existing scripts under `tools\standalone`. They do not deploy, sync, or restart any remote service.
