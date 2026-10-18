# Build 698 integration

Integrated the source changes from the actual `build-698` release, commit
`f953907daaec50b3f5235edb0a62424d5705af9e`, using common ancestor
`e03b1919f5332ae04f1fc18730436df67b894eb6`. Source:
[georgenat89-design/maro build 698](https://github.com/georgenat89-design/maro/releases/tag/build-698).
The supplied release JAR SHA256 is
`87190b5fcb0dc1f413e543f866d02fb3d3d409758425629dcf413cdc2b5e64bc`.

The six-file release delta adds Spotify Playlists with Larp Aux, Phonk, House Music and
Summer Music, built-in links, individual hotkeys and app/browser destinations. Its native
checks are retained. The release also corrects the Nathan module count and restores the
initial game mode after Geeked checks. Adjacent README additions were combined.

This working-tree integration preserves the existing local features and the current update:
live Name Protect, per-module public-config page icons and menu, main-screen Streamproof,
full compass-dial bounds, blue Staff Watch defaults and the loading-screen Discord invite.
No release classes were copied over locally modified classes. A complete pre-merge source
snapshot was saved before integration; no commit or push was performed.

The combined production client is validated with Meteor using the per-module config API
checks, build 698's playlist checks and the live privacy/compass/loading/Streamproof checks.
Playlist target checks do not launch Spotify or a browser.
