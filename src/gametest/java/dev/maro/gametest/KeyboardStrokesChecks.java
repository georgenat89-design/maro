package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.hud.KeyboardStrokesScreen;
import dev.maro.gui.hud.KeyboardArrangeScreen;
import dev.maro.gui.hud.KeyboardLayout;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.render.ScreenGuiSurface;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import dev.maro.setting.ButtonSetting;
import dev.maro.module.impl.visuals.KeyboardStrokes;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import dev.maro.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Keyboard Strokes: the classic seven keys by default, any keybind can be added or taken off from
 * the chooser, and the chooser draws.
 */
final class KeyboardStrokesChecks {
    private KeyboardStrokesChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context) {
        KeyboardStrokes module = ModuleManager.get(KeyboardStrokes.class);
        require(module != null, "Keyboard Strokes was not registered");
        require(ModuleManager.getByName("Keystrokes") == null, "The old Keystrokes module should be removed");
        String layout = ClientSettings.menuStyle.get();
        boolean floating = ClientSettings.screenGui.get();
        boolean spatial = ClientSettings.spatialMenu.get();
        try {
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset); module.resetLayout();
                module.setEnabled(true);
                ClientSettings.spatialMenu.set(false);
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                require(module.cells().size() == 7, "The classic layout should be seven keys: " + module.cells().size());
                require(module.hudWidth() > 0 && module.hudHeight() > 0, "Keyboard Strokes should have a size");
                KeyBinding sneak = c.options.sneakKey;
                require(!module.isShown(sneak), "Sneak should start hidden");
                module.toggleKey(sneak);
                require(module.isShown(sneak) && module.cells().size() == 8, "Choosing sneak should add a key");
                module.toggleKey(sneak);
                require(!module.isShown(sneak) && module.cells().size() == 7, "Choosing sneak again should take it off");
            });
            context.takeScreenshot("maro-keyboard-strokes");
            // Held keys light up: hold forward and jump, and the screenshot should show W and SPACE lit.
            context.getInput().holdKey(o -> o.forwardKey);
            context.getInput().holdKey(o -> o.jumpKey);
            try {
                context.waitTicks(4);
                require(context.computeOnClient(c -> c.options.forwardKey.isPressed()), "Forward should read as held");
                context.takeScreenshot("maro-keyboard-strokes-held");
            } finally {
                context.getInput().releaseKey(o -> o.forwardKey);
                context.getInput().releaseKey(o -> o.jumpKey);
            }
            physicalLayout(context, module);
            arrangeLayout(context, module);
            for (String style : new String[] {"Panels", "Window"}) {
                for (boolean screenGui : new boolean[] {false}) {
                    context.runOnClient(c -> {
                        ClientSettings.menuStyle.set(style);
                        ClientSettings.screenGui.set(screenGui);
                        c.setScreen(new ClickGuiScreen());
                        var gui = (ClickGuiScreen) c.currentScreen;
                        gui.mouseMoved(gui.width / 2d, gui.height / 2d);
                        gui.getSearch().setText("Keyboard Strokes");
                    });
                    context.waitTicks(12);
                    context.runOnClient(c -> {
                        var gui = (ClickGuiScreen) c.currentScreen;
                        if (style.equals("Panels")) {
                            var row = gui.panelPlace(module);
                            require(row != null, "Keyboard Strokes missing from Panels search");
                            var point = gui.screenPose().project(row[0], row[1]);
                            var click = new Click(point[0], point[1], new MouseInput(1, 0));
                            gui.mouseClicked(click, false); gui.mouseReleased(click);
                        } else gui.openModuleSettings(module);
                        require(c.currentScreen instanceof KeyboardStrokesScreen, "Right-click should open the key chooser");
                    });
                    context.waitTicks(8);
                    require(!context.computeOnClient(c -> ScreenGuiSurface.hasResources()), "Opening chooser leaked the menu surface");
                    context.takeScreenshot("maro-keyboard-strokes-menu-" + style.toLowerCase() + "-" + screenGui);
                    // Click the physical keycap bound to the first action.
                    context.runOnClient(c -> {
                            var chooser = (KeyboardStrokesScreen) c.currentScreen;
                            var binding = module.allBindings()[0];
                            boolean wasShown = module.isShown(binding);
                            double[] point = chooser.keyPoint(KeyboardStrokes.code(binding));
                            chooser.mouseClicked(new Click(point[0], point[1], new MouseInput(0, 0)), false);
                            require(module.isShown(binding) != wasShown, "Physical keycap click did not change its action");
                            module.resetKeys();
                            chooser.close();
                            require(c.currentScreen instanceof ClickGuiScreen, "Chooser should return to its parent menu");
                            c.setScreen(null);
                    });
                    System.out.println("[keyboard-strokes-proof] " + style + " floating=" + screenGui + " chooser/click/return passed");
                }
            }
            int oldScale = context.computeOnClient(c -> c.options.getGuiScale().getValue());
            try {
                for (int scale : new int[]{2, 3, 4}) {
                    context.runOnClient(c -> { c.options.getGuiScale().setValue(scale); c.onResolutionChanged(); c.setScreen(new KeyboardStrokesScreen(null, module)); });
                    context.waitTicks(6);
                    context.runOnClient(c -> {
                        var chooser = (KeyboardStrokesScreen)c.currentScreen;
                        require(chooser.keyCount() == 107, "The 104-key keyboard and three mouse buttons are incomplete");
                        for (int code : new int[]{GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_KP_5}) {
                            boolean before = module.isPhysicalShown(code);
                            double[] point = chooser.keyPoint(code);
                            chooser.mouseClicked(new Click(point[0], point[1], new MouseInput(0, 0)), false);
                            require(module.isPhysicalShown(code) != before, "Scaled keycap click missed: " + code + " / " + scale);
                        }
                    });
                    context.waitTicks(3);
                    context.takeScreenshot("maro-keyboard-layout-scale-" + scale);
                    context.runOnClient(c -> { module.resetKeys(); c.setScreen(null); });
                }
            } finally { context.runOnClient(c -> { c.options.getGuiScale().setValue(oldScale); c.onResolutionChanged(); }); }
            context.runOnClient(c -> module.togglePhysical(GLFW.GLFW_KEY_F8));
            require(context.computeOnClient(c -> module.cells().stream().anyMatch(cell -> cell.binding() == null && cell.code() == GLFW.GLFW_KEY_F8)),
                    "An unused physical key was not added to the HUD");
            context.getInput().holdKey(GLFW.GLFW_KEY_F8);
            context.waitTicks(3);
            require(context.computeOnClient(c -> module.physicalDown(GLFW.GLFW_KEY_F8)), "Native F8 input did not light its physical HUD cell");
            context.takeScreenshot("maro-keyboard-physical-held");
            context.getInput().releaseKey(GLFW.GLFW_KEY_F8);
            context.waitTicks(2);
            require(!context.computeOnClient(c -> module.physicalDown(GLFW.GLFW_KEY_F8)), "Physical key remained lit after release");
            context.runOnClient(c -> {
                ((ButtonSetting) module.getSettings().stream().filter(v -> v.getName().equals("Position")).findFirst().orElseThrow()).press();
                require(c.currentScreen instanceof HudPlacementScreen, "Position should open the HUD editor");
                ((HudPlacementScreen) c.currentScreen).dragTo(120, 80);
                module.hudResize(.4f);
                module.toggleKey(c.options.sneakKey);
                float left = module.hudLeft(), top = module.hudTop(), scale = module.hudScale();
                require(ConfigManager.save("keyboard_native_smoke"), "Saving keyboard settings failed");
                module.getSettings().forEach(Setting::reset);
                require(ConfigManager.load("keyboard_native_smoke"), "Reloading keyboard settings failed");
                require(module.isShown(c.options.sneakKey) && module.isPhysicalShown(GLFW.GLFW_KEY_F8) && Math.abs(module.hudLeft() - left) <= 1
                        && Math.abs(module.hudTop() - top) <= 1 && Math.abs(module.hudScale() - scale) < .01,
                        "Selected keys, position and size did not persist");
                ConfigManager.delete("keyboard_native_smoke");
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-keyboard-strokes-placement");
            context.runOnClient(c -> {
                module.clearKeys();
                require(module.cells().isEmpty(), "Clearing should remove every key");
                c.setScreen(null);
            });
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);
                ClientSettings.menuStyle.set(layout); ClientSettings.screenGui.set(floating);
                ClientSettings.spatialMenu.set(spatial);
                module.setEnabled(false); module.resetLayout();
                module.getSettings().forEach(Setting::reset);
            });
            context.waitTicks(2);
            System.out.println("[keyboard-strokes-proof] held keys, selection, placement, size and saved settings passed");
        }
    }

    private static void arrangeLayout(ClientGameTestContext context, KeyboardStrokes module) {
        KeyboardArrangeScreen editor=context.computeOnClient(c->{var s=new KeyboardArrangeScreen(null,module);c.setScreen(s);return s;});
        context.waitTicks(5);
        var before=context.computeOnClient(c->key(module,GLFW.GLFW_KEY_A));
        context.runOnClient(c->{
            var at=editor.keyPoint(c.options.forwardKey.getId());float scale=editor.canvasScale();
            editor.mouseClicked(new Click(at[0],at[1],new MouseInput(0,0)),false);
            var end=new Click(at[0]+scale*55,at[1]+scale*65,new MouseInput(0,0));
            editor.mouseDragged(end,scale*55,scale*65);editor.mouseReleased(end);
            var moved=key(module,GLFW.GLFW_KEY_W);
            require(module.customLayout()&&moved.y()>=60,"Dragging did not move the individual key");
            require(Math.abs(key(module,GLFW.GLFW_KEY_A).x()-before.x())<.01f&&Math.abs(key(module,GLFW.GLFW_KEY_A).y()-before.y())<.01f,"Dragging one key moved its neighbor");
            editor.keyPressed(new net.minecraft.client.input.KeyInput(GLFW.GLFW_KEY_RIGHT,0,GLFW.GLFW_MOD_SHIFT));
            require(Math.abs(key(module,GLFW.GLFW_KEY_W).x()-moved.x()-1)<.01f,"Fine arrow nudge failed");
            require(ConfigManager.save("keyboard_arrange_proof"),"Saving arranged keyboard failed");
            float x=key(module,GLFW.GLFW_KEY_W).x(),y=key(module,GLFW.GLFW_KEY_W).y();
            module.resetLayout();require(ConfigManager.load("keyboard_arrange_proof"),"Reloading arranged keyboard failed");
            require(Math.abs(key(module,GLFW.GLFW_KEY_W).x()-x)<.01f&&Math.abs(key(module,GLFW.GLFW_KEY_W).y()-y)<.01f,"Individual positions did not persist");
            var forward=c.options.forwardKey;var old=InputUtil.fromTranslationKey(forward.getBoundKeyTranslationKey());
            try{forward.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_UP));KeyBinding.updateKeysByCode();require(Math.abs(key(module,GLFW.GLFW_KEY_UP).x()-x)<.01f,"Rebinding lost custom placement");}
            finally{forward.setBoundKey(old);KeyBinding.updateKeysByCode();}
            ConfigManager.delete("keyboard_arrange_proof");
        });
        context.waitTicks(5);context.takeScreenshot("maro-keyboard-arrange");
        context.runOnClient(c->{module.resetLayout();require(!module.customLayout(),"Reset retained custom positions");require(key(module,GLFW.GLFW_KEY_W).y()<key(module,GLFW.GLFW_KEY_A).y(),"Reset did not restore physical layout");c.setScreen(null);});
        System.out.println("[keyboard-arrange-proof] PASS: native individual drag, neighbors stable, fine nudge, saved positions, rebind and physical reset");
    }

    private static KeyboardStrokes.Cell key(KeyboardStrokes module, int code) {
        return module.cells().stream().filter(cell -> cell.code() == code).findFirst().orElseThrow();
    }

    private static void bounds(KeyboardStrokes module) {
        var cells = module.cells();
        for (int i = 0; i < cells.size(); i++) {
            var a = cells.get(i);
            require(a.x() >= -.01f && a.y() >= -.01f && a.x() + a.w() <= module.layoutWidth() + .01f
                    && a.y() + a.h() <= module.layoutHeight() + .01f, "A key escaped the HUD bounds: " + a);
            for (int j = i + 1; j < cells.size(); j++) {
                var b = cells.get(j);
                require(a.x() + a.w() <= b.x() + .01f || b.x() + b.w() <= a.x() + .01f
                        || a.y() + a.h() <= b.y() + .01f || b.y() + b.h() <= a.y() + .01f,
                        "Physical keycaps overlap: " + a + " / " + b);
            }
        }
    }

    private static void physicalLayout(ClientGameTestContext context, KeyboardStrokes module) {
        context.runOnClient(c -> {
            module.resetKeys();
            module.togglePhysical(GLFW.GLFW_KEY_F);
            module.togglePhysical(GLFW.GLFW_KEY_R);
            module.hudResize(.5f);
            var f = key(module, GLFW.GLFW_KEY_F);
            var r = key(module, GLFW.GLFW_KEY_R);
            var d = key(module, GLFW.GLFW_KEY_D);
            var w = key(module, GLFW.GLFW_KEY_W);
            require(f.x() > d.x() && Math.abs(f.y() - d.y()) < .01f, "F should sit to the right of D, not below Space");
            require(r.x() > w.x() && Math.abs(r.y() - w.y()) < .01f && r.y() < f.y(), "R should stay on W's row above F");
            require(key(module, GLFW.GLFW_KEY_SPACE).y() > f.y(), "Extra letter keys were appended below Space");
            bounds(module);
        });
        context.waitTicks(5);
        context.takeScreenshot("maro-keyboard-positions-fr");
        context.runOnClient(c -> c.setScreen(new KeyboardStrokesScreen(null, module)));
        context.waitTicks(4);
        context.takeScreenshot("maro-keyboard-positions-preview");
        context.runOnClient(c -> {
            c.setScreen(null);
            var forward = c.options.forwardKey;
            var original = InputUtil.fromTranslationKey(forward.getBoundKeyTranslationKey());
            try {
                forward.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_UP));
                KeyBinding.updateKeysByCode();
                var up = key(module, GLFW.GLFW_KEY_UP);
                require(up.binding() == forward && up.x() > key(module, GLFW.GLFW_KEY_F).x(), "Rebinding did not move the selected action to its new physical position");
            } finally {
                forward.setBoundKey(original); KeyBinding.updateKeysByCode();
            }
            require(key(module, GLFW.GLFW_KEY_W).binding() == forward, "Restoring a keybind left its HUD position stale");
            module.clearKeys();
            for (var cap : KeyboardLayout.keys()) module.togglePhysical(cap.code());
            require(module.cells().size() == 107, "The full selected keyboard lost keys");
            require(key(module, GLFW.GLFW_KEY_KP_ADD).h() > KeyboardStrokes.KEY * 2
                    && key(module, GLFW.GLFW_KEY_KP_ENTER).h() > KeyboardStrokes.KEY * 2, "Tall numpad keys were flattened");
            bounds(module);
            require(module.hudWidth() <= c.getWindow().getScaledWidth() - 7
                    && module.hudHeight() <= c.getWindow().getScaledHeight() - 7, "The full keyboard should fit the viewport");
        });
        context.waitTicks(4);
        context.takeScreenshot("maro-keyboard-positions-full");
        context.runOnClient(c -> {
            module.clearKeys();
            module.togglePhysical(dev.maro.util.KeyUtil.mouse(0));
            module.togglePhysical(dev.maro.util.KeyUtil.mouse(1));
            require(module.cells().size() == 2 && module.layoutHeight() == KeyboardStrokes.KEY, "Mouse-only selection retained empty keyboard rows");
            bounds(module);
            module.clearKeys();
            require(module.layoutWidth() == 0 && module.layoutHeight() == 0, "Empty selection retained its previous bounds");
            module.resetKeys();
            ((NumberSetting)module.getSettings().stream().filter(s -> s.getName().equals("Scale")).findFirst().orElseThrow()).reset();
        });
        System.out.println("[keyboard-strokes-proof] physical F/R positions, rebind invalidation, all 107 key bounds, tall numpad keys and viewport fit passed");
    }
}
