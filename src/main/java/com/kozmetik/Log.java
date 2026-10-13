package com.kozmetik;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {
    private static final Logger L = LoggerFactory.getLogger("kozmetik");
    private Log() {}
    public static void info(String s) { L.info(s); }
    public static void warn(String s) { L.warn(s); }
    public static void error(String s, Throwable t) { L.error(s, t); }
}
