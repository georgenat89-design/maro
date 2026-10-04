// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Anubis by 4ldenz, recovered from the user-provided Anubis Client Beta 0.9.8.jar.
// Modified for Maro / Yarn 1.21.11 on 2026-10-04; see THIRD_PARTY.md.
package dev.maro.anubis.util.staff;

import dev.maro.anubis.gui.DonutGoliathMapData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(value=EnvType.CLIENT)
public final class ShardTracker {
    private static final int OFF_MAP = Integer.MIN_VALUE;
    private ClientWorld level;
    private ClientPlayerEntity self;
    private String brand;
    private int goliath = Integer.MIN_VALUE;

    public boolean moved(MinecraftClient mc) {
        ClientWorld nowLevel = mc.world;
        ClientPlayerEntity nowSelf = mc.player;
        if (nowLevel == null || nowSelf == null) {
            this.clear();
            return false;
        }
        ClientPlayNetworkHandler connection = mc.getNetworkHandler();
        String nowBrand = connection == null ? null : connection.getBrand();
        int nowGoliath = ShardTracker.goliath(nowLevel, nowSelf);
        boolean moved = this.level != null && (nowLevel != this.level || nowSelf != this.self && !this.self.isDead() || nowGoliath != this.goliath || nowBrand != null && this.brand != null && !nowBrand.equals(this.brand));
        this.level = nowLevel;
        this.self = nowSelf;
        this.brand = nowBrand;
        this.goliath = nowGoliath;
        return moved;
    }

    public void clear() {
        this.level = null;
        this.self = null;
        this.brand = null;
        this.goliath = Integer.MIN_VALUE;
    }

    private static int goliath(ClientWorld level, ClientPlayerEntity self) {
        if (level.getRegistryKey() != World.OVERWORLD) {
            return Integer.MIN_VALUE;
        }
        return DonutGoliathMapData.goliathAt(self.getX(), self.getZ());
    }
}

