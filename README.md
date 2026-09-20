# Rent-desk
App to manage tenant and payment transactions.

## Google Drive backup setup

Rent Desk accepts Drive access only for `rentdesk111@gmail.com`. Restorable JSON
snapshots are stored in that account's hidden Drive `appDataFolder`. Readable ZIP
exports are stored in the visible `RentDesk Exports` folder and contain CSV and
JSON files.

Before using the feature:

1. Create or select a project in Google Cloud Console.
2. Enable the Google Drive API.
3. Configure the OAuth consent screen and add `rentdesk111@gmail.com` as a test
   user while the app remains in testing mode.
4. Create an Android OAuth client for package `com.get.detail.rentdesk`.
5. Add the SHA-1 fingerprint for every signing certificate used to install the
   app. Debug and release builds normally use different fingerprints.
6. On each device, open Settings > Google Drive backup and select **Connect
   Google Drive**, then choose `rentdesk111@gmail.com`.

The app requests only `drive.appdata` and `drive.file`. It does not store Google
passwords or OAuth access tokens. Each device must have Google Play services and
must authorize the same account.

Backups are shared snapshots rather than live synchronization. If another device
has produced a newer snapshot, Rent Desk warns before creating a newer version.
