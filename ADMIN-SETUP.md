# RIO BOARD admin – setup

## A. Admin web (one time)
1. Put all files from the zip in the repo (same folders), commit.
2. Repo -> Settings -> Pages -> Deploy from a branch -> main -> /docs -> Save.
   - admin page: https://USER.github.io/REPO/admin/
   - app config:  https://USER.github.io/REPO/config.json
3. In app/src/main/java/com/rio/board/RemoteConfig.kt set CONFIG_URL to the config link above.
4. Token: GitHub -> Settings -> Developer settings -> Fine-grained tokens. Only this repo, Contents = Read and write.
5. Admin page -> GitHub button -> username, repo, branch main, path docs/config.json, token -> Save.

## B. Same signing key every build (needed so the app can update itself)
Without this every GitHub build gets a new random key and Android refuses to update over the installed app.
1. Repo -> Settings -> Secrets and variables -> Actions -> New repository secret.
2. Secret 1: name KS_BASE64, value = the long text in RIO-signing-secrets.txt (section 1).
3. Secret 2: name KS_PASSWORD, value = the password in RIO-signing-secrets.txt (section 2).
4. Do NOT commit RIO-signing-secrets.txt to the repo. Keep it somewhere safe: if you lose it you can never update the same app again.
5. Push any change (or Actions -> Build RIO BOARD APK -> Run workflow). The build number becomes the version code.
6. ONE TIME ONLY: the APK you have installed now was signed with a different key. Uninstall RIO BOARD once (back up what you need)
   and install the new APK. From then on every update installs over the old one and keeps your settings.

## C. Sending an update to users
1. Push code. Actions builds and publishes a release with RIO-BOARD.apk.
2. Admin -> App update -> "Fill from my latest release" -> write what is new -> Publish.
3. The keyboard shows an update card in the News panel. Tap -> download -> Android installer -> Install.
   (Android always asks the user to press Install; an app can not install silently.)

## Notes
- Saving from the admin only changes docs/, so it does NOT start a new APK build.
- Built-in themes and backgrounds can be removed from the app in the admin (they are only hidden, not deleted from the APK).
- Anyone can open the admin page but can not save without your token.
