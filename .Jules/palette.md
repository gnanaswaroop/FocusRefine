## 2024-05-24 - Added Content Description to Icon Buttons
**Learning:** Found multiple IconButtons in the codebase that use raw Text nodes instead of proper Icons or lacked explicit semantics contentDescription for screen readers. In Compose, standard Text within an IconButton isn't always announced optimally as an action, so explicitly adding contentDescription is crucial for accessibility.
**Action:** Always add semantic contentDescriptions to IconButtons, especially those utilizing Text for icons (e.g. ⛶, ✕, 🗑) or lacking an inner Icon with its own content description.
## 2024-10-26 - Add confirmation dialog for "Clear Inbox"
**Learning:** Destructive actions like clearing the entire notification inbox must require explicit user confirmation to prevent accidental data loss.
**Action:** Always add an `AlertDialog` confirmation for permanent or destructive actions like clearing user history.
