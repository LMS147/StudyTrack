# Weekly timetable

Open Calendar → Timetable → Add class. Enter a title, optionally link a subject, choose a weekday/start/end time, and add a venue/lecturer. The from/until dates are inclusive; no end date repeats indefinitely. Overnight classes must be split into separate entries. Back-to-back classes are allowed; classes on overlapping weekly occurrences cannot overlap in time.

Tap a class to edit it or delete the entire weekly series. Calendar shows the selected date's classes above tasks and marks class days with a purple dot. Timetable classes do not become tasks, award XP, or alter existing deadlines. No holiday exceptions, one-occurrence overrides or class reminder notifications are included in this first version.

Rows are persisted in Room under the Firebase UID. This version is device-local; it does not claim cloud synchronisation or include the timetable in the existing study-data JSON export. Account switching scopes every query and stale editors cannot save into a different account.

Database version 2 adds timetable_entries through MIGRATION_1_2 and preserves all existing task, subject, progress, session and account tables. There is no destructive migration fallback. Tests cover recurrence bounds, time conflicts, account isolation, CRUD and upgrading a real version-1 database through Room schema validation.
