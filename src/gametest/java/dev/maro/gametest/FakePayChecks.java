package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.FakePay;
import dev.maro.setting.Setting;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Fake Pay: the line shows the name and amount you set, in short or full, as received or sent, a
 * bad amount shows nothing, and pressing its key shows it once and switches the module back off.
 */
final class FakePayChecks {
    private FakePayChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void set(FakePay module, String name, String value) {
        Setting<?> s = module.getSettings().stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
        if (s instanceof TextSetting t) t.set(value);
        else ((dev.maro.setting.ModeSetting) s).set(value);
    }

    static void run(ClientGameTestContext context) {
        FakePay module = ModuleManager.get(FakePay.class);
        require(module != null, "Fake Pay was not registered");
        try {
            context.runOnClient(c -> module.getSettings().forEach(Setting::reset));
            context.runOnClient(c -> {
                set(module, "Player", "Notch");
                set(module, "Amount", "1.5m");
                module.send();
                require(module.last().equals("Notch paid you $1.5m."), "Received line wrong: " + module.last());
                set(module, "Amount Style", "Full");
                module.send();
                require(module.last().equals("Notch paid you $1,500,000."), "Full amount wrong: " + module.last());
                set(module, "Type", "Sent");
                set(module, "Amount", "$3,500");
                module.send();
                require(module.last().equals("You paid Notch $3,500."), "Sent line wrong: " + module.last());
                set(module, "Amount Style", "Short");
                set(module, "Amount", "244k");
                set(module, "Type", "Received");
                module.send();
                require(module.last().equals("Notch paid you $244k."), "Short amount wrong: " + module.last());
                String before = module.last();
                set(module, "Amount", "lots");
                module.send();
                require(module.last().equals(before), "A bad amount should show nothing");
                module.getSettings().forEach(Setting::reset);
                set(module, "Player", "Notch");
                set(module, "Amount", "250k");
            });
            context.takeScreenshot("maro-fake-pay");
            // The key: on, shows once on the next tick, then off.
            context.runOnClient(c -> {
                set(module, "Amount", "2m");
                module.setEnabled(true);
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                require(!module.isEnabled(), "Fake Pay should switch itself off after showing");
                require(module.last().equals("Notch paid you $2m."), "The key should show the line: " + module.last());
            });
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
            });
        }
    }
}
