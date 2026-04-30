package com.javelin.core;

import java.io.InputStream;
import java.util.Properties;
import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() throws Exception {
        String version = loadVersion();
        return new String[]{
            "",
            "+========================================================+",
            "|                 JAVELIN CLI v" + version + pad(version) + "|",
            "+========================================================+",
            "",
            "javelin-cli " + version
        };
    }

    public static String loadVersion() {
        Properties props = new Properties();
        try (InputStream is = VersionProvider.class.getResourceAsStream("/version.properties")) {
            if (is != null) props.load(is);
        } catch (Exception ignored) {}
        return props.getProperty("version", "unknown");
    }

    private static String pad(String version) {
        int contentLen = "                 JAVELIN CLI v".length() + version.length();
        int innerWidth = 56;
        int needed = innerWidth - contentLen;
        return " ".repeat(Math.max(1, needed));
    }
}
