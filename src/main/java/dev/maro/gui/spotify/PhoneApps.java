package dev.maro.gui.spotify;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.widget.TextField;
import dev.maro.phone.PhoneNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.net.URI;

/** Apps share the same animated device and input lifecycle as the original player. */
final class PhoneApps {
    enum App { MUSIC, HOME, MESSAGES, PLAYLISTS, CONNECTION, SETTINGS, SPOTIFY, TIKTOK, CLIPS }
    private static final int TEXT = 0xFFF5F7F6, DIM = 0xFFA3ADA7, GREEN = 0xFF1ED760;
    private record Hit(float x, float y, float w, float h, Runnable action) { }
    private record WrapKey(String value, float width, float size, boolean customFont, String font) { }
    private final PhoneNetwork network;
    private final dev.maro.module.impl.misc.SpotifyPhone owner;
    private boolean settingsChanged;
    private final dev.maro.phone.SpotifyLibrary spotifyLibrary;
    private final List<Hit> hits = new ArrayList<>();
    private final java.util.LinkedHashMap<WrapKey, List<String>> wrapped = new java.util.LinkedHashMap<>(32, .75f, true);
    private final TextField recipient = new TextField("10-digit phone number", 20).filter(c -> Character.isDigit(c) || c == ' ' || c == '-');
    private final TextField compose = new TextField("Write a message…", 1000);
    private final TextField playlistName = new TextField("Playlist name", 60);
    private final TextField playlistLink = new TextField("Paste Spotify playlist link", 256);
    private final TextField relay = new TextField("http://127.0.0.1:8765", 180);
    private TextField focused;
    private App app = App.MUSIC;
    private String peer = "", error = "";
    private PhoneNetwork.Playlist sharing;
    private PhoneNetwork.Message detail;
    private int scroll;
    private float contentWidth, contentHeight;
    private boolean relaySet;
    private boolean requests;
    private final TextField spotifyClient = new TextField("Spotify app client ID", 32);
    private boolean spotifyClientSet;
    private final PhoneClipsApp clips;
    private final PhoneTikTokApp tikTok;
    private final PhoneMessageVideo messageVideo=new PhoneMessageVideo();
    private java.nio.file.Path videoDraft;
    private String videoSendId="";
    private boolean choosingVideo,sendingVideo,closed;

    PhoneApps(PhoneNetwork network,dev.maro.phone.SpotifyLibrary spotifyLibrary,java.nio.file.Path clipsFolder,dev.maro.module.impl.misc.SpotifyPhone owner) {
        this.network = network; this.owner = owner;
        this.tikTok = new PhoneTikTokApp(owner);
        tikTok.onLocalVideos(() -> open(App.CLIPS));
        this.spotifyLibrary = spotifyLibrary;
        this.clips = new PhoneClipsApp(clipsFolder);
        clips.navigation(this::home,()->open(App.TIKTOK));
        compose.onEnter(this::send);
        recipient.onEnter(() -> { if (PhoneNetwork.validNumber(recipient.getText())) openConversation(PhoneNetwork.number(recipient.getText())); });
    }
    boolean settingsChanged() { return settingsChanged; }
    boolean music() { return app == App.MUSIC; }
    boolean videos() { return app == App.TIKTOK || app == App.CLIPS; }
    boolean typing() { return focused != null || app == App.CLIPS && clips.typing() || app == App.TIKTOK && tikTok.typing(); }
    App app() { return app; }
    void home() { open(App.HOME); }
    private void open(App next) { messageVideo.stop();videoDraft=null;videoSendId="";if (app == App.CLIPS && next != App.CLIPS) clips.stop(); if (app == App.TIKTOK && next != App.TIKTOK) tikTok.stop(); app = next; focused = null; scroll = 0; error = ""; sharing = null; detail = null; if (next == App.CLIPS) clips.open(); if (next == App.TIKTOK) tikTok.open(); }
    void close() { closed=true;messageVideo.close();clips.close(); tikTok.close(); }
    private void openConversation(String number) { open(App.MESSAGES); peer = number; recipient.setText(number); network.read(number); }
    private void act(Runnable action) {
        try { action.run(); error = ""; }
        catch (IllegalArgumentException ex) { error = ex.getMessage() == null ? "Check the entered value" : ex.getMessage(); }
    }
    void render(DrawContext ctx, float width, float height, float mx, float my) {
        hits.clear(); contentWidth = width; contentHeight = height;
        var state = network.snapshot();
        if (!relaySet) { relay.setText(state.endpoint()); relaySet = true; }
        if (music()) return;
        if (app == App.TIKTOK) { tikTok.render(ctx,width,height,mx,my); return; }
        float left = 20, right = width - 20, w = right - left;
        String title = switch(app) { case HOME -> "Maro Phone"; case MESSAGES -> "Messages"; case PLAYLISTS -> "Playlists"; case SETTINGS -> "Settings"; case SPOTIFY -> "Spotify Library"; case TIKTOK -> "TikTok"; case CLIPS -> "Local Videos"; default -> "Connection"; };
        text(ctx, title, 24, 52, TEXT, true, 1.15f);
        if (app != App.HOME && app != App.CLIPS) button(ctx, left, 81, 40, 19, "Back", () -> {
            if (detail != null) { messageVideo.stop();detail = null; scroll = 0; }
            else if (app == App.MESSAGES && !peer.isEmpty()) { peer = ""; scroll = 0; }
            else home();
        }, mx, my, false);
        switch (app) {
            case HOME -> {
                text(ctx, state.number().isEmpty() ? "Your phone, in game" : format(state.number()), left, 81, DIM, false, .9f);
                float tileWidth = (w - 12) / 2;
                // Keep two separate rows above the footer, including the shorter iPad viewport.
                float tileHeight = Math.min(72, (height - 64 - 14 - 116 - 12) / 2);
                float second = 116 + tileHeight + 12;
                tile(ctx, left, 116, tileWidth, tileHeight, "Spotify", "Your music", GREEN, () -> open(App.MUSIC), mx, my);
                long unread = state.visibleMessages().stream().filter(PhoneNetwork.Message::unread).count();
                tile(ctx, left + tileWidth + 12, 116, tileWidth, tileHeight, "Messages", unread == 0 ? "Inbox + requests" : unread + " unread", 0xFF63A5FF, () -> { peer = ""; requests=false; open(App.MESSAGES); }, mx, my);
                tile(ctx, left, second, tileWidth, tileHeight, "Playlists", state.playlists().size() + " saved", 0xFFC592FF, () -> open(App.PLAYLISTS), mx, my);
                tile(ctx, left + tileWidth + 12, second, tileWidth, tileHeight, "Videos", "TikTok feeds", 0xFF58E5E1, () -> open(App.TIKTOK), mx, my);
                button(ctx,left,height-64,tileWidth,22,"Settings",()->open(App.SETTINGS),mx,my,false);
                button(ctx,left+tileWidth+12,height-64,tileWidth,22,state.online()?"Online":"Connect",()->open(App.CONNECTION),mx,my,false);
            }
            case CONNECTION -> {
                text(ctx, "Your number", left, 115, DIM, false, .8f);
                text(ctx, state.number().isEmpty() ? "Connect to get a number" : format(state.number()), left, 133, TEXT, true, 1f);
                button(ctx, left, 155, w, 23, "Copy my number", () -> { if (!state.number().isEmpty()) MinecraftClient.getInstance().keyboard.setClipboard(state.number()); }, mx, my, false);
                text(ctx, "Test relay address", left, 193, DIM, false, .8f);
                field(ctx, relay, left, 208, w, mx, my);
                button(ctx, left, 241, (w - 8) / 2, 24, "Connect", () -> act(() -> network.connect(relay.getText())), mx, my, true);
                button(ctx, left + (w + 8) / 2, 241, (w - 8) / 2, 24, "Disconnect", network::disconnect, mx, my, false);
                text(ctx, state.status(), left, 283, state.online() ? GREEN : DIM, false, .75f);
                if (height > 390) {
                    text(ctx, "Numbers are saved on this relay.", left, height - 61, DIM, false, .72f);
                    text(ctx, "Friends use the same relay address.", left, height - 48, DIM, false, .72f);
                }
            }
            case PLAYLISTS -> {
                button(ctx,left+47,81,w-47,19,"From Spotify",()->open(App.SPOTIFY),mx,my,true);
                field(ctx, playlistName, left, 111, w, mx, my);
                field(ctx, playlistLink, left, 143, w, mx, my);
                button(ctx, left, 175, w, 24, "Save playlist", () -> act(() -> { network.savePlaylist(playlistName.getText(), playlistLink.getText()); playlistName.clear(); playlistLink.clear(); focused = null; }), mx, my, true);
                var list = state.playlists();
                int visible = Math.max(1, (int) ((height - 245) / 62)); scroll = Math.clamp(scroll, 0, Math.max(0, list.size() - visible));
                if (list.isEmpty()) text(ctx, "Copy a playlist link from Spotify.", left, 222, DIM, false, .78f);
                for (int i = scroll; i < Math.min(list.size(), scroll + visible); i++) {
                    var p = list.get(i); float y = 214 + (i - scroll) * 62;
                    Render2D.roundRect(ctx, left, y, w, 55, 8, 0xFF21292A);
                    text(ctx, p.title(), left + 8, y + 7, TEXT, true, .85f);
                    button(ctx, left + 6, y + 27, 42, 21, "Open", () -> openPlaylist(p.url()), mx, my, false);
                    button(ctx, left + 52, y + 27, 46, 21, "Share", () -> { open(App.MESSAGES); sharing = p; peer = ""; recipient.clear(); }, mx, my, true);
                    button(ctx, right - 32, y + 27, 26, 21, "×", () -> network.removePlaylist(p.id()), mx, my, false);
                }
            }
            case MESSAGES -> messages(ctx, state, left, w, height, mx, my);
            case SETTINGS -> {
                var p=state.preferences();
                button(ctx,left,116,w,28,"Notifications: "+(p.notifications()?"On":"Off"),()->network.preferences(new PhoneNetwork.Preferences(!p.notifications(),p.requests(),p.sound(),p.previews())),mx,my,p.notifications());
                button(ctx,left,154,w,28,"Request alerts: "+(p.requests()?"On":"Off"),()->network.preferences(new PhoneNetwork.Preferences(p.notifications(),!p.requests(),p.sound(),p.previews())),mx,my,p.requests());
                button(ctx,left,192,w,28,"Alert sound: "+(p.sound()?"On":"Off"),()->network.preferences(new PhoneNetwork.Preferences(p.notifications(),p.requests(),!p.sound(),p.previews())),mx,my,p.sound());
                button(ctx,left,230,w,28,"Message previews: "+(p.previews()?"On":"Off"),()->network.preferences(new PhoneNetwork.Preferences(p.notifications(),p.requests(),p.sound(),!p.previews())),mx,my,p.previews());
                button(ctx,left,268,w,28,"Interact with game: "+(owner.gameInteraction.get()?"On":"Off"),()->{
                    owner.gameInteraction.set(!owner.gameInteraction.get());settingsChanged=true;
                },mx,my,owner.gameInteraction.get());
                if(height>390) {text(ctx,"Alt or Interact returns to phone controls.",left,313,DIM,false,.72f);text(ctx,"New numbers go to Requests.",left,331,DIM,false,.72f);}
            }
            case SPOTIFY -> spotify(ctx,left,w,height,mx,my);
            case TIKTOK -> { }
            case CLIPS -> {
                clips.render(ctx,width,height,mx,my);
            }
            default -> { }
        }
        if (!error.isEmpty()) text(ctx, error, left, height - 31, 0xFFFFA0A0, false, .72f);
    }
    private void spotify(DrawContext ctx,float x,float w,float height,float mx,float my) {
        var library=spotifyLibrary==null?dev.maro.phone.SpotifyLibrary.get():spotifyLibrary;var s=library.snapshot();
        if(!spotifyClientSet && focused!=spotifyClient && spotifyClient.getText().isEmpty()){spotifyClient.setText(s.clientId());spotifyClientSet=!s.clientId().isEmpty();}
        if(!s.connected()) {
            text(ctx,"Spotify app client ID",x,112,DIM,false,.78f);
            field(ctx,spotifyClient,x,129,w,mx,my);
            button(ctx,x,164,w,25,s.busy()?"Waiting for sign-in…":"Connect Spotify",()->act(()->library.connect(spotifyClient.getText().trim())),mx,my,true);
            text(ctx,"Sign in securely in your browser.",x,203,DIM,false,.74f);
            text(ctx,"Your app needs this redirect URI:",x,221,DIM,false,.7f);
            button(ctx,x,239,w,23,"Copy redirect URI",()->MinecraftClient.getInstance().keyboard.setClipboard("http://127.0.0.1/callback"),mx,my,false);
        } else {
            button(ctx,x,111,(w-6)/2,23,"Refresh",()->act(library::refresh),mx,my,true);
            button(ctx,x+(w+6)/2,111,(w-6)/2,23,"Disconnect",library::disconnect,mx,my,false);
            int visible=Math.max(1,(int)((height-200)/58));scroll=Math.clamp(scroll,0,Math.max(0,s.playlists().size()-visible));
            for(int i=scroll;i<Math.min(s.playlists().size(),scroll+visible);i++) {
                var p=s.playlists().get(i);float y=164+(i-scroll)*58;
                Render2D.roundRect(ctx,x,y,w,52,8,0xFF21292A);text(ctx,p.title(),x+7,y+7,TEXT,true,.85f);
                button(ctx,x+6,y+27,48,19,"Save",()->network.savePlaylist(p.title(),p.url()),mx,my,false);
                button(ctx,x+60,y+27,w-66,19,"Share",()->{open(App.MESSAGES);sharing=p;peer="";recipient.clear();},mx,my,true);
            }
        }
        text(ctx,s.status(),x,height-39,DIM,false,.7f);
    }
    private void messages(DrawContext ctx, PhoneNetwork.Snapshot state, float x, float w, float height, float mx, float my) {
        if (detail != null) {
            text(ctx, format(detail.sender()), x + 46, 86, TEXT, true, .82f);
            if(detail.kind().equals("video")){
                messageVideo.render(ctx,x,111,w,height-195);
                button(ctx,x,height-73,(w-6)/2,23,messageVideo.paused()?"Play":"Pause",messageVideo::pause,mx,my,false);
                button(ctx,x+(w+6)/2,height-73,(w-6)/2,23,messageVideo.muted()?"Sound on":"Mute",messageVideo::mute,mx,my,false);
                text(ctx,detail.text(),x,height-39,DIM,false,.7f);return;
            }
            var lines = wrap(detail.text(), w - 8, .9f);
            int visible = Math.max(1, (int) ((height - 188) / 14));
            scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - visible));
            for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) text(ctx, lines.get(i), x + 4, 119 + (i - scroll) * 14, TEXT, false, .9f);
            if (detail.status().startsWith("Failed")) {
                text(ctx, detail.status(), x, height - 67, 0xFFFFA0A0, false, .73f);
                button(ctx, x, height - 48, w, 23, "Retry message", () -> { network.retry(detail.clientId()); detail = null; scroll = 0; }, mx, my, true);
            }
            return;
        }
        if (sharing != null) {
            text(ctx, "Share playlist", x, 116, DIM, false, .8f);
            text(ctx, sharing.title(), x, 135, TEXT, true, 1f);
            field(ctx, recipient, x, 165, w, mx, my);
            button(ctx, x, 201, w, 26, "Send playlist", () -> act(() -> { network.share(recipient.getText(), sharing); openConversation(PhoneNetwork.number(recipient.getText())); }), mx, my, true);
            return;
        }
        if (peer.isEmpty()) {
            var visibleMessages=state.visibleMessages();
            long requestCount=visibleMessages.stream().map(m->m.peer(state.number())).distinct().filter(state::request).count();
            button(ctx,x,109,(w-6)/2,21,"Inbox",()->{requests=false;scroll=0;},mx,my,!requests);
            button(ctx,x+(w+6)/2,109,(w-6)/2,21,"Requests "+requestCount,()->{requests=true;scroll=0;},mx,my,requests);
            field(ctx, recipient, x, 139, w - 47, mx, my);
            button(ctx, x + w - 42, 139, 42, 25, "New", () -> act(() -> { if (!PhoneNetwork.validNumber(recipient.getText())) throw new IllegalArgumentException("Enter a 10-digit phone number"); openConversation(PhoneNetwork.number(recipient.getText())); }), mx, my, true);
            var peers = new java.util.LinkedHashMap<String, PhoneNetwork.Message>();
            for (int i = visibleMessages.size() - 1; i >= 0; i--) { var m = visibleMessages.get(i); if(state.request(m.peer(state.number()))==requests) peers.putIfAbsent(m.peer(state.number()), m); }
            var entries = new ArrayList<>(peers.entrySet());
            int visible = Math.max(1, (int) ((height - 218) / 51)); scroll = Math.clamp(scroll, 0, Math.max(0, entries.size() - visible));
            if (entries.isEmpty()) text(ctx, requests?"No message requests.":"Start a chat using a friend's number.", x, 191, DIM, false, .76f);
            for (int i = scroll; i < Math.min(entries.size(), scroll + visible); i++) {
                var e = entries.get(i); float y = 179 + (i - scroll) * 51;
                Render2D.roundRect(ctx, x, y, w, 44, 7, 0xFF20292B);
                text(ctx, format(e.getKey()), x + 8, y + 7, TEXT, true, .9f);
                text(ctx, (e.getValue().kind().equals("playlist")?"♫ ":e.getValue().kind().equals("video")?"Video · ":"")+e.getValue().text(), x + 8, y + 25, DIM, false, .75f);
                if (state.messages().stream().anyMatch(m -> m.unread() && m.peer(state.number()).equals(e.getKey()))) Render2D.circle(ctx, x + w - 10, y + 12, 3, GREEN);
                hits.add(new Hit(x, y, w, 44, () -> openConversation(e.getKey())));
            }
            return;
        }
        text(ctx, format(peer), x + 46, 86, TEXT, true, .82f);
        boolean blocked = state.blocked().contains(peer);
        boolean request=state.request(peer) && state.messages().stream().anyMatch(m->m.sender().equals(peer));
        if(request) {
            button(ctx,x,108,(w-12)/3,20,"Accept",()->network.accept(peer),mx,my,true);
            button(ctx,x+(w+6)/3,108,(w-12)/3,20,"Delete",()->{network.ignore(peer);peer="";},mx,my,false);
            button(ctx,x+2*(w+6)/3,108,(w-12)/3,20,"Block",()->act(()->network.block(peer,true)),mx,my,false);
        } else
        button(ctx, x, 108, 56, 20, blocked ? "Unblock" : "Block", () -> act(() -> network.block(peer, !blocked)), mx, my, false);
        var thread = state.messages().stream().filter(m -> m.peer(state.number()).equals(peer)).toList();
        int visible = Math.max(1, (int) ((height - (videoDraft!=null?252:226)) / 64)); scroll = Math.clamp(scroll, 0, Math.max(0, thread.size() - visible));
        int last = thread.size() - scroll, first = Math.max(0, last - visible);
        for (int i = first; i < last; i++) {
            var m = thread.get(i); float y = 138 + (i - first) * 64;
            boolean own = m.sender().equals(state.number());
            Render2D.roundRect(ctx, x + (own ? 9 : 0), y, w - 9, 58, 9, own ? 0xFF224438 : 0xFF252E34);
            var lines = wrap((m.kind().equals("playlist") ? "♫ " : "") + m.text(), w - 28, .8f);
            for (int line = 0; line < Math.min(2, lines.size()); line++) text(ctx, lines.get(line), x + 8 + (own ? 9 : 0), y + 7 + line * 12, TEXT, false, .8f);
            if(m.kind().equals("video")){
                button(ctx,x+7,y+30,76,20,"Play video",()->act(()->{detail=m;focused=null;scroll=0;messageVideo.open(network,m);}),mx,my,false);
                text(ctx,own?m.status():"Received",x+89,y+42,DIM,false,.63f);
            } else if (m.kind().equals("playlist")) {
                button(ctx, x + 7, y + 30, 46, 20, "Open", () -> openPlaylist(m.url()), mx, my, false);
                button(ctx, x + 57, y + 30, 46, 20, "Save", () -> act(() -> network.savePlaylist(m.text(), m.url())), mx, my, false);
            } else {
                text(ctx, (own ? m.status() : "Received") + (lines.size() > 2 ? " · Tap to read" : ""), x + 8 + (own ? 9 : 0), y + 42, DIM, false, .67f);
                hits.add(new Hit(x, y, w, 58, () -> { detail = m; focused = null; scroll = 0; }));
            }
        }
        if(!request && !blocked) {
            if(videoDraft!=null)button(ctx,x,height-105,w,21,(sendingVideo?"Sending… ":"Video: ")+videoDraft.getFileName()+(sendingVideo?"":" · Remove"),()->{if(!sendingVideo){videoDraft=null;videoSendId="";}},mx,my,false);
            field(ctx, compose, x, height - 77, w - 88, mx, my);
            button(ctx,x+w-83,height-77,40,25,choosingVideo?"…":"Video",this::chooseVideo,mx,my,false);
            button(ctx, x + w - 38, height - 77, 38, 25, sendingVideo?"…":"Send", this::send, mx, my, true);
        }
        text(ctx, request?"Accept this request to reply.":blocked ? "Number blocked" : "Enter to send · Scroll for older messages", x, height - 44, DIM, false, .66f);
        network.read(peer);
    }
    private void chooseVideo(){
        if(choosingVideo||sendingVideo)return;choosingVideo=true;String contact=peer;focused=null;
        java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
                return org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Choose an MP4 video (32 MB maximum)","",stack.pointers(stack.UTF8("*.mp4")),"H.264 MP4 videos",false);
            }
        }).whenComplete((file,failure)->MinecraftClient.getInstance().execute(()->{
            choosingVideo=false;if(closed||app!=App.MESSAGES||!peer.equals(contact))return;
            if(failure!=null){error="Could not open the video picker";return;}
            if(file!=null){videoDraft=java.nio.file.Path.of(file);videoSendId=java.util.UUID.randomUUID().toString();error="";}
        }));
    }
    private void send() { act(() -> {
        if(sendingVideo)return;
        if(videoDraft==null){network.send(peer, compose.getText()); compose.clear(); scroll = 0;return;}
        var chosen=videoDraft;String contact=peer;if(videoSendId.isEmpty())videoSendId=java.util.UUID.randomUUID().toString();
        var upload=network.sendVideo(contact,chosen,videoSendId);sendingVideo=true;
        upload.whenComplete((unused,failure)->MinecraftClient.getInstance().execute(()->{
            sendingVideo=false;if(closed||app!=App.MESSAGES||!peer.equals(contact)||!chosen.equals(videoDraft))return;
            if(failure==null){videoDraft=null;videoSendId="";scroll=0;error="";}
            else{Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();error=cause.getMessage()==null?"Video not sent. Tap Send to retry.":cause.getMessage()+" · Tap Send to retry";}
        }));
    }); }
    private List<String> wrap(String value, float width, float size) {
        var key = new WrapKey(value, width, size, dev.maro.config.ClientSettings.customFont.get(), dev.maro.config.ClientSettings.fontFace.get());
        var cached = wrapped.get(key);
        if (cached != null) return cached;
        var result = new ArrayList<String>();
        for (String paragraph : value.split("\\n", -1)) {
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                int end = 1;
                while (end < remaining.length() && Fonts.width(remaining.substring(0, end + 1), false, size) <= width) end++;
                if (end < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                result.add(remaining.substring(0, end)); remaining = remaining.substring(end).stripLeading();
            }
            if (paragraph.isEmpty()) result.add("");
        }
        var lines = List.copyOf(result); wrapped.put(key, lines);
        if (wrapped.size() > 32) wrapped.remove(wrapped.keySet().iterator().next());
        return lines;
    }
    private static void openPlaylist(String url) {
        // Only an explicit click opens a verified Spotify playlist URL.
        Util.getOperatingSystem().open(URI.create(PhoneNetwork.playlistUrl(url)));
    }
    private void field(DrawContext ctx, TextField field, float x, float y, float w, float mx, float my) {
        Render2D.roundRect(ctx, x, y, w, 25, 6, 0xFF20292B);
        Render2D.roundOutline(ctx, x, y, w, 25, 6, 1, focused == field ? GREEN : 0xFF3B4648);
        String value = field.getText().isEmpty() ? field == recipient ? "Phone number" : field == compose ? "Message…" : field == playlistName ? "Playlist name" : field == playlistLink ? "Spotify playlist link" : field == spotifyClient ? "Spotify app client ID" : "Relay URL" : field.getText();
        // Keep the end of a long input visible while typing.
        if (focused == field && !field.getText().isEmpty()) while (value.length() > 1 && Fonts.width(value, false, .8f) > w - 12) value = value.substring(1);
        text(ctx, value + (focused == field && System.currentTimeMillis() % 1000 < 500 ? "|" : ""), x + 6, y + 8, field.getText().isEmpty() ? DIM : TEXT, false, .8f, w - 12);
        hits.add(new Hit(x, y, w, 25, () -> focused = field));
    }
    private void tile(DrawContext ctx, float x, float y, float w, float h, String title, String sub, int color, Runnable action, float mx, float my) {
        Render2D.roundRect(ctx, x, y, w, h, 12, hit(mx, my, x, y, w, h) ? 0xFF303D3B : 0xFF20292B);
        Render2D.roundRect(ctx, x + 10, y + 10, 24, 24, 7, color);
        if (title.equals("Spotify")) PhoneIcons.spotify(ctx, x + 22, y + 22);
        else {
            String icon = title.equals("Messages") ? "···" : title.equals("Playlists") ? "♫" : "⌁";
            Fonts.drawCentered(ctx, icon, x + 22, y + 22, 0xFF12201A, true, 1f);
        }
        text(ctx, title, x + 10, y + h - 29, TEXT, true, .9f, w - 20);
        text(ctx, sub, x + 10, y + h - 14, DIM, false, .8f, w - 20);
        hits.add(new Hit(x, y, w, h, action));
    }
    private void button(DrawContext ctx, float x, float y, float w, float h, String label, Runnable action, float mx, float my, boolean accent) {
        Render2D.roundRect(ctx, x, y, w, h, 6, accent ? hit(mx, my, x, y, w, h) ? 0xFF59E58B : GREEN : hit(mx, my, x, y, w, h) ? 0xFF3A4541 : 0xFF293331);
        Fonts.drawCentered(ctx, Fonts.trim(label,w-8,true,.72f), x + w / 2, y + h / 2, accent ? 0xFF0B2013 : TEXT, true, .72f);
        hits.add(new Hit(x, y, w, h, action));
    }
    private void text(DrawContext ctx, String value, float x, float y, int color, boolean bold, float size) { text(ctx, value, x, y, color, bold, size, Math.max(30, contentWidth - 24 - x)); }
    private static void text(DrawContext ctx, String value, float x, float y, int color, boolean bold, float size, float width) { Fonts.draw(ctx, Fonts.trim(value.replace('\n', ' '), width, bold, size), x, y, color, bold, size); }
    static String format(String number) { return number.length() == 10 ? number.substring(0, 3) + " " + number.substring(3, 6) + " " + number.substring(6) : number; }
    private static boolean hit(float mx, float my, float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    boolean click(float x, float y) {
        if(app==App.CLIPS && clips.click(x,y))return true;
        if(app==App.TIKTOK && tikTok.click(x,y))return true;
        focused = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            var h = hits.get(i);
            if (hit(x, y, h.x, h.y, h.w, h.h)) {
                try { h.action.run(); } catch (IllegalArgumentException ex) { error = ex.getMessage() == null ? "Check the entered value" : ex.getMessage(); }
                return true;
            }
        }
        return !music();
    }
    boolean key(KeyInput input) {
        if(app==App.CLIPS && clips.key(input))return true;
        if(app==App.TIKTOK && tikTok.key(input))return true;
        if (focused == null) return false;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { focused = null; return true; }
        focused.keyPressed(input.key(), input.modifiers()); return true;
    }
    boolean character(int codepoint) { if(app==App.CLIPS && clips.character(codepoint))return true; if(app==App.TIKTOK && tikTok.character(codepoint))return true; if (focused == null) return false; if (codepoint <= Character.MAX_VALUE) focused.charTyped((char) codepoint); return true; }
    boolean release(float x,float y) { return app==App.TIKTOK && tikTok.release(x,y); }
    boolean drag(float x,float y) { return app==App.TIKTOK && tikTok.drag(x,y); }
    boolean keyReleased(KeyInput input) { return app==App.TIKTOK && tikTok.keyReleased(input); }
    void scroll(float x,float y,double amount) { if(app==App.TIKTOK){tikTok.scroll(x,y,amount);return;} if(app==App.CLIPS){clips.scroll(amount);return;} if (Double.isFinite(amount)) scroll = Math.max(0, scroll + (int) Math.signum(amount) * (app == App.MESSAGES && !peer.isEmpty() && detail == null ? 1 : -1)); }
}
