// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.tpa;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

@Environment(value=EnvType.CLIENT)
final class CompletionRequests {
    private static final int FIRST_ID = 10000;
    private static final int LAST_ID = 29999;
    private static final long FORGET_MS = 15000L;
    private final Map<Integer, Request> requests = new ConcurrentHashMap<Integer, Request>();
    private int nextId = 10000;
    private Form form = Form.BOTH;

    CompletionRequests() {
    }

    Form form() {
        return this.form;
    }

    void form(Form form) {
        this.form = form;
    }

    int fire(String partial) {
        return this.fire(partial, this.form);
    }

    int fire(String partial, Form using) {
        ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
        if (connection == null) {
            return -1;
        }
        int id = this.nextId++;
        if (this.nextId > 29999) {
            this.nextId = 10000;
        }
        this.requests.put(id, new Request(System.currentTimeMillis()));
        try {
            if (using != Form.BARE) {
                connection.sendPacket((Packet)new RequestCommandCompletionsC2SPacket(id, "/" + partial));
            }
            if (using != Form.SLASH) {
                connection.sendPacket((Packet)new RequestCommandCompletionsC2SPacket(id, partial));
            }
        }
        catch (RuntimeException e) {
            this.requests.remove(id);
            return -1;
        }
        return id;
    }

    List<String> poll(int id) {
        Request request = this.requests.get(id);
        return request == null || request.done ? null : request.names;
    }

    void forget(int id) {
        Request request = this.requests.get(id);
        if (request != null) {
            request.done = true;
        }
    }

    void prune(long now) {
        this.requests.values().removeIf(request -> now - request.at > 15000L);
    }

    void clear() {
        this.requests.clear();
        this.form = Form.BOTH;
    }

    boolean ingest(Packet<?> packet) {
        if (!(packet instanceof CommandSuggestionsS2CPacket)) {
            return false;
        }
        CommandSuggestionsS2CPacket suggestions = (CommandSuggestionsS2CPacket)packet;
        Request request = this.requests.get(suggestions.id());
        if (request == null) {
            return false;
        }
        if (request.names == null) {
            ArrayList<String> names = new ArrayList<String>();
            for (Suggestion suggestion : suggestions.getSuggestions().getList()) {
                String text = suggestion.getText();
                if (text == null || text.isBlank()) continue;
                names.add(text.trim());
            }
            request.names = List.copyOf(names);
        }
        return true;
    }

    @Environment(value=EnvType.CLIENT)
    static enum Form {
        BOTH,
        SLASH,
        BARE;

    }

    @Environment(value=EnvType.CLIENT)
    private static final class Request {
        final long at;
        volatile List<String> names;
        volatile boolean done;

        Request(long at) {
            this.at = at;
        }
    }
}
