# Revision flashcards

Open Dashboard → Revision flashcards. Create a subject first, then add cards with a question, answer and subject. Filter by subject, tap a card to edit or delete it, and choose Review due cards. Reveal each answer before rating recall.

Review uses the device's local calendar day. Again leaves the card due today; Hard schedules it after half the previous interval with a minimum of one day; Easy starts at three days and doubles the previous interval up to 180 days. A card appears at most once within one review session, so close and reopen review to study an Again card again today. New cards are due immediately. Editing question, answer or subject preserves the latest review progress. If a linked subject is deleted, the card remains available under All subjects, labelled Subject unavailable; assign it to an existing subject when editing.

Cards and progress are stored locally in Room per Firebase UID. They are not synchronised to a backend or included in the existing JSON export. Uninstalling the app removes them. This version does not include review notifications, bulk import or image attachments.

Database migration 2→3 adds the flashcards table and indexes without removing tasks, timetable or other data; 1→2→3 is also supported. Tests cover review intervals, card CRUD and due-date checks, multi-account isolation, stale editor writes, and upgrading a version-2 database through Room's schema validation.
