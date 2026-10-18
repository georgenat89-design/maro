# Phone apps

Open with **F10**, press **I** to interact, and choose an app. Phone and iPad share the
same apps. Typing in a field pauses movement; leaving the phone releases its video
decoder, audio device and texture.

## Inbox and Requests

Messages from new numbers appear under **Messages → Requests**. Open one to preview
it, then **Accept** to move that conversation to Inbox and reply. **Delete** hides a
conversation and silences further alerts from that number; sending to or accepting
the number restores it. **Block** uses the phone relay's existing block list. A new
conversation can be opened by entering its number, including to unblock it.

Messages to a number you contact yourself go into Inbox. Existing outgoing
conversations remain in Inbox after updating. Phone numbers, history, accepted and
ignored numbers persist per relay in `.minecraft/maro/phone.json`.

Both people must connect to the same phone service under **Connect**. The existing
[relay setup](../tools/phone-relay/README.md) supports local/LAN testing or a configured
HTTPS relay. No public messaging service is provisioned by this update.

## Notifications

**Home → Settings** controls all notifications, request alerts, alert sound and message
previews independently. Turning previews off shows the sender and message type.
Notifications can arrive while the phone is closed. Settings persist between launches.

## Sharing Spotify playlists

**Playlists** keeps manually saved Spotify playlist links with Open/Share actions.
**From Spotify** imports your actual Spotify library after browser sign-in. It requests
only `playlist-read-private` and `playlist-read-collaborative`, then lists the playlists
you own or follow. Save one locally or share its link to a phone number. Sharing a
private playlist does not change its Spotify access permissions.

To connect your own Spotify application:

1. Create/use an app in the Spotify developer dashboard and copy its public client ID.
2. Register `http://127.0.0.1/callback` as its redirect URI. The phone's **Copy redirect
   URI** button copies this value. Spotify permits a dynamically assigned port for a
   registered loopback IP URI.
3. Enter the client ID under **From Spotify → Connect Spotify**, then sign in and
   approve in your browser. Development apps must allow the account being used.
4. Use **Refresh** to load current playlists; **Disconnect** removes the locally stored
   refresh token. Account/API restrictions and rate limits are shown in the phone.

The implementation uses [Spotify's Authorization Code with PKCE flow](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow)
and [loopback redirect rules](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri).
It never asks for a Spotify password or client secret. The refresh token is local in
`.minecraft/maro/spotify-library.json`, outside shared module configurations. It is not
sent through the phone relay. Keep this personal file out of shared archives.

## Clips

**Clips** is a looping vertical video feed. Scroll down or press **↓/→** for the next
clip; scroll up or **↑/←** goes back. **Space** and **Pause** toggle playback. Clips start
muted; **Sound** enables an AAC audio track when present. Two original, silent demo
loops are included so the feed works immediately.

Under **Library**, add a direct HTTPS MP4 link, or choose **Open folder** and put MP4
files in `.minecraft/maro/phone-clips/`. **Refresh clips**, then **Watch** to return.
Use H.264 MP4 video at 720p or smaller, up to 64 MB per clip. The feed supports direct
video files rather than web page URLs. Saved links live in `links.json`; downloaded
clips are cached separately, with a 256 MB cache budget. No video is downloaded until
its feed entry is played.

Video decoding, downloads and file scanning run off the render thread. The renderer
takes the newest small frame and reuses one texture. Switching clips cancels previous
work; pausing freezes the playback clock. The JCodec slice pool is retired when a clip
closes so repeatedly scrolling does not accumulate decoder threads.

## TikTok: For You, Entertainment and Gaming

Open **TikTok** from the phone's app grid. **For You** loads
`https://www.tiktok.com/foryou`; **Fun** (labelled **Entertainment** on iPad) loads
`https://www.tiktok.com/tag/funny`; **Gaming** loads `https://www.tiktok.com/tag/gaming`.
The topic tabs are TikTok hashtag pages. They do not filter or rewrite your personal
For You algorithm. Sign in through TikTok's own website if you want personalized
recommendations. TikTok controls sign-in, content availability and any verification.

In-game browsing uses an **installed Microsoft Edge or Google Chrome**. Maro detects
standard Windows, macOS and Linux installation paths. No extra Minecraft mod or
browser-runtime download is required. If no browser is found, the phone shows a
setup link; install a browser and restart Minecraft. **Browser** opens the selected
TikTok page in your regular browser.

Press **I** to interact, then click inside the page to scroll, select videos, or type.
While web typing has focus, movement keys go to the page. **Esc** releases typing;
**F10** closes the phone. **Reload** reloads the current page. **Videos** opens the
existing local/direct-MP4 library; **TikTok FYP** returns from that library.
Closing the phone or leaving TikTok closes its browser and releases the view.

The phone starts a separate headless browser, with normal sandbox and web security,
using a dedicated `.minecraft/maro/tiktok-browser/` profile. It does not read your
regular browser profile. Sign-in cookies stay in that browser-owned folder, outside
shareable module configurations. Sign in separately inside the phone; opening your
regular browser does not transfer that session. Maro does not ask for cookie exports
or API tokens. TikTok controls availability, login compatibility and verification;
a personalized account feed cannot be verified without signing in.

The browser's DevTools connection binds to a random loopback-only port. Browser
downloads are disabled. A bounded frame stream decodes off the render thread and
reuses a texture; old frames are dropped instead of queued. Leaving TikTok stops
its dedicated browser process, and lifecycle operations serialize quick reopenings.
This view has browser/video processing overhead; it does not fetch a private feed
API or translate TikTok pages into direct MP4 links.

TikTok's [Display API](https://developers.tiktok.com/docs/en/display-api-overview)
documents creator videos and profiles. Its [embedded player](https://developers.tiktok.com/docs/en/embed-player)
plays individual posts. The full website is used here to access For You.

## Validation

The focused production-client suite checks two isolated relay identities, request
classification/accept/delete, persisted notification settings, Spotify PKCE and invalid
state rejection, pagination, rate limits, saved sign-in refresh, actual playlist sharing,
and MP4 playback/pause/resume/next/direct-link entry on Phone and iPad. Spotify tests
use an isolated local OAuth/API fixture, without a personal account or real credentials.
Video files and saved test links use temporary libraries. The Windows native runner's
clipboard is unavailable; playlist entry is tested through typing on that runner.

The combined TikTok/phone suite also passed with Meteor on Windows and installed
Edge 155 in 1 minute 24 seconds: real browser H.264 playback, scrolling, click/release,
text entry, movement isolation, topic navigation, reusable frames, local-video
navigation and view cleanup on Phone and iPad. The live TikTok For You page loaded
and was reviewed in a native screenshot. No personal TikTok account, account login
or personalized recommendations were tested. No dedicated test-browser processes
remained after the client exited. macOS and Linux browser detection is implemented
but was not exercised on this Windows host.
The browser surface is clipped to its own GUI layer so live frames cannot obscure
the phone's header controls. The final browser/native rendering check passed with
Meteor in 42 seconds; Phone, iPad and the live TikTok page were visually reviewed.

Validated locally October 11, 2026: all five relay checks passed, and the combined
production build/native phone suite passed with Meteor Client 1.21.11-85 in 1 minute
5 seconds. Native rendering reported no faults. Live Spotify account authorization
was not exercised; its OAuth/API protocol was tested using the local fixture above.

```powershell
python tools/phone-relay/test_relay.py
# Start the relay separately, then:
./gradlew.bat build runProductionClientGameTest -PspotifyPhoneTestOnly=true `
  -PphoneRelayTestUrl=http://127.0.0.1:18765 `
  '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar' --console=plain
```

Add `-PphoneTikTokTestOnly=true` to the phone suite to include browser checks, and
`-PliveTikTokTest=true` to capture a public TikTok page. All browser test profiles are
temporary; the browser checks do not require a personal TikTok account.
