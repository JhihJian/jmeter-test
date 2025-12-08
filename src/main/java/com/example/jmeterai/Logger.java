package com.example.jmeterai;

public final class Logger {
  public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

  private static final Level CURRENT = resolveLevel();
  private final String name;

  private Logger(Class<?> cls) { this.name = cls.getSimpleName(); }
  public static Logger get(Class<?> cls) { return new Logger(cls); }

  private static Level resolveLevel() {
    try {
      String v = System.getenv().getOrDefault("LOG_LEVEL", "INFO");
      return Level.valueOf(v.toUpperCase());
    } catch (Exception e) {
      return Level.INFO;
    }
  }

  private void log(Level lvl, String msg, Throwable t) {
    if (lvl.ordinal() < CURRENT.ordinal()) return;
    String ts = java.time.LocalDateTime.now().toString();
    String th = Thread.currentThread().getName();
    String line = String.format("%s [%s] %-5s %s - %s", ts, th, lvl, name, msg);
    if (lvl == Level.ERROR) System.err.println(line); else System.out.println(line);
    if (t != null) {
      if (lvl == Level.ERROR) t.printStackTrace(System.err); else t.printStackTrace(System.out);
    }
  }

  public void trace(String m) { log(Level.TRACE, m, null); }
  public void debug(String m) { log(Level.DEBUG, m, null); }
  public void info(String m)  { log(Level.INFO,  m, null); }
  public void warn(String m)  { log(Level.WARN,  m, null); }
  public void error(String m) { log(Level.ERROR, m, null); }
  public void error(String m, Throwable t) { log(Level.ERROR, m, t); }
}

