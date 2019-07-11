package com.oneapm.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public final class Messages {
    private static ResourceBundle messages;
    static {
        messages = ResourceBundle.getBundle("com.sun.btrace.resources.messages", Locale.getDefault());
    }

    private Messages() {}

    public static String get(String key) {
        return messages.getString(key);
    }

    public static String format(String key, Object...args) {
        return MessageFormat.format(get(key), args);
    }
}