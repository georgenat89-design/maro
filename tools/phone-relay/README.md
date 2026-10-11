# Maro Phone test relay

Python 3.11 or newer, with no extra packages. The relay assigns each client installation
a unique 10-digit number and stores messages in SQLite. It works independently of the
Minecraft server, so the two players do not need to be in the same world.

Start it from the repository root:

```powershell
python tools/phone-relay/relay.py --port 8765 --database phone-relay.sqlite3
```

In game, press **F10**, then **I** (Interact). Choose **Connect**, enter
`http://127.0.0.1:8765`, and click **Connect**. Each test client gets a number;
use **Copy my number** to give it to the other tester. Both clients must use the
same relay. The Interact key is configurable in Spotify Phone's settings.
Click the phone's **Interact** button or press **I** to switch between Spotify,
Messages, Playlists, and Connection. Press **I** again to return to mouse look;
holding **Alt** also gives temporary access to phone controls.

For two computers on the same LAN, start the relay with `--host 0.0.0.0` and enter
the relay computer's LAN address, such as `http://192.168.1.20:8765`, on both phones.
Only private LAN addresses and loopback support HTTP. HTTPS domains can be used
later by placing the relay behind a reverse proxy; public hosting is not required
for this test version.

**Playlists:** copy a playlist's link from Spotify, enter a name and link in the
Playlists app, and save it. Click **Share**, enter a phone number, and send. The
recipient can **Open** it in Spotify or **Save** it to their phone. Spotify playlist
URIs and normal share links are accepted. This saves and shares links; it does not
read the user's Spotify account or require a Spotify API login.

**Messages:** enter a friend's number and click **New**. Type a message and press
**Enter** or click **Send**. Movement pauses while typing. Messages queue while the
relay is offline, and resume on reconnect. Rejected messages show their reason and
can be clicked to retry. Use **Block** inside a conversation to prevent new messages
from that number. A successful send means the relay accepted it; it does not claim
the recipient has read it.

Numbers, authentication identities, recent history, blocked numbers and saved playlists
live in `.minecraft/maro/phone.json`, outside the normal shared module configs.
Keep this file to retain the number; it contains the device's private login secret.
The relay database must also be retained. Numbers are scoped to the relay address.
The relay holds message bodies for 30 days; each device keeps up to 500 recent
messages, 50 queued sends and 64 playlists. This is a local/LAN test service, with
relay-visible messages and transport encryption when using HTTPS.

Run the relay checks:

```powershell
python tools/phone-relay/test_relay.py
```

With the relay running, drive the real game and two independent client identities:

```powershell
./gradlew.bat runProductionClientGameTest -PspotifyPhoneTestOnly=true -PphoneRelayTestUrl=http://127.0.0.1:8765 -Ploader_version=0.19.5
```

The relay option is opt-in. Ordinary CI tests use isolated local phone data and do
not send messages or require a network service.
