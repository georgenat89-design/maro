package dev.maro.anubis.util;
public final class NameFilter {
    private NameFilter() {}
    public static String apply(String text){return text==null?"":text;}
}
