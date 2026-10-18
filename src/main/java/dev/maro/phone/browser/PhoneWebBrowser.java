package dev.maro.phone.browser;

import net.minecraft.client.input.KeyInput;
import java.nio.file.Path;

/** Isolated browser boundary; installed Edge/Chrome handles media and website authentication. */
public interface PhoneWebBrowser extends AutoCloseable {
    static boolean available() { return ChromePhoneBrowser.executable()!=null; }
    static PhoneWebBrowser create(String url, int width, int height, Path profile) {
        return available() ? new ChromePhoneBrowser(url,width,height,profile) : null;
    }
    static PhoneWebBrowser forDownloads(String url,int width,int height,Path profile,Path directory) {
        return available()?new ChromePhoneBrowser(url,width,height,profile,directory):null;
    }
    record Download(String id,String filename,long received,long total,String state,Path file,String message) { }
    default Download download(){return null;}
    default void cancelDownload(){}
    static void shutdown() { ChromePhoneBrowser.shutdown(); }
    record Frame(long serial,int width,int height,byte[] rgba,float viewX,float viewY,float viewWidth,float viewHeight,boolean focusedMedia) {
        public Frame(long serial,int width,int height,byte[] rgba,float viewX,float viewY,float viewWidth,float viewHeight){this(serial,width,height,rgba,viewX,viewY,viewWidth,viewHeight,false);}
    }
    default void videoFocus(boolean enabled) { }
    Frame frame();
    String status();
    void resize(int width, int height);
    void navigate(String url);
    void reload();
    void focus(boolean focused);
    void move(int x, int y);
    void press(int x, int y);
    void release(int x, int y);
    void scroll(int x, int y, double amount);
    default void videoStep(int direction) { scroll(100,200,direction > 0 ? -6 : 6); }
    void key(KeyInput input, boolean pressed);
    void character(int codepoint);
    void paste(String text);
    boolean loading();
    @Override void close();
}
