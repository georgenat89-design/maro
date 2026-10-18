# Name Protect

Enable **Misc → Name Protect** and type your display name. Changes apply immediately while
typing. Select a player from the searchable list or type their account name, then enter their
alias. Clear an alias to restore their name or the automatic alias. No Apply button is needed.

Settings also offer Anonymous Players, a configurable Player Prefix and Keep Friends. Numbered
aliases stay stable for the connection. Explicit player aliases override automatic names and
friend exclusions. Your own name setting has priority. Aliases save locally and do not appear
in public module presets.

The renderer matches whole Minecraft account names, ignoring case, and preserves surrounding
text styles. Chat, tab, name labels and Maro's custom HUD fonts use the replacement. Existing
chat lines refresh immediately; disabling restores their original text. Editor fields keep real
input, selection and cursor positions. Account profiles, outgoing commands, chat signatures,
server identities and original messages remain unchanged. Other mods with independent text
renderers may need their own integration.

Immutable snapshots rebuild on edits or roster changes. String and ordered-text caches are
bounded, with no roster lookup per text draw. A rendered alias is not matched a second time
inside wrapped chat. Name Protect remains active in Streamproof's public render.

Native production tests with Meteor cover live editor changes, self/other aliases, anonymous
number stability, friends/overrides, styles and measured widths, chat refresh/restore, input and
identity preservation, wrapped alias collisions, bounded caches and public-preset exclusion.
