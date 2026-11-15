# RIO BOARD admin – setup (one time, about 5 minutes)

1. Put these files in the repo (same folders as in the zip), commit.
2. GitHub repo -> Settings -> Pages -> Source: "Deploy from a branch", Branch: main, Folder: /docs -> Save.
   After a minute your site is at https://USER.github.io/REPO/
   - admin page: https://USER.github.io/REPO/admin/
   - app config:  https://USER.github.io/REPO/config.json
3. Open app/src/main/java/com/rio/board/RemoteConfig.kt and replace the CONFIG_URL line with your config link. Commit (this builds the APK).
4. Make a token: GitHub -> Settings -> Developer settings -> Fine-grained tokens -> Generate.
   Repository access: only this repo. Permissions: Contents = Read and write. Copy it.
5. Open the admin page -> "GitHub" -> fill username, repo, branch (main), path (docs/config.json), token -> Save.
6. Add themes / news -> Publish. The keyboard picks it up next time it opens (checks every 3 hours at most).

Notes
- Saving from the admin only changes docs/config.json, so it does NOT start a new APK build.
- App update notice: bump versionCode in app/build.gradle.kts for every APK you release, then put the same number in the admin "App update" tab.
- Anyone can open the admin page but can not save without your token. Keep the token private.
