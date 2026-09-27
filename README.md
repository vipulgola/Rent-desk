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

Automatic backup can be enabled from Settings after Drive is connected. Local
address, property, tenant, and transaction changes are grouped for 45 seconds,
then WorkManager runs when a network connection is available. Before uploading,
it downloads the newest Drive snapshot and merges matching records using each
record's modification timestamp. Google authorization still requires the user
to reconnect when consent can no longer be renewed silently.

Each property also stores its monthly rent, electricity price per unit, latest
meter reading, and outstanding balance. When recording a payment, the user
selects a dd/mm/yyyy payment date and current reading, then sees electricity cost,
previous balance, rent, and the total to collect before entering the amount
received. Saving updates the property reading and balance together with the
new transaction. These values are included in JSON backups and CSV exports.

When calculating rent, electricity usage below 10 units (current reading minus previous
reading) has a minimum charge of Rs 100, including zero usage. At 10 units or
more, the usual per-unit rate applies. Monthly totals and balance recalculations
use the same rule.
