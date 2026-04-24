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
            "+===============================================================+",
            "|                      Javelin Core v" + version + padRight(version) + "|",
            "+===============================================================+",
            "",
            "javelin-core " + version
        };
    }

    public static String loadVersion() {
        Properties props = new Properties();
        try (InputStream is = VersionProvider.class.getResourceAsStream("/version.properties")) {
            if (is != null) props.load(is);
        } catch (Exception ignored) {}
        return props.getProperty("version", "unknown");
    }

    // Pad to keep the banner box width consistent (banner inner width = 63 chars)
    private static String padRight(String version) {
        // "                      Javelin Core v" = 36 chars, "|" at end, total inner = 63
        // printed = 36 + version.length(), need total = 62 before closing "|"
        int printed = 36 + version.length();
        int needed = 62 - printed;
        return " ".repeat(Math.max(0, needed));
    }
}
