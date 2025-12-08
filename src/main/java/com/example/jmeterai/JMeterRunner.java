package com.example.jmeterai;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class JMeterRunner {
  public void run(Path jmx, Path jtl, Path reportDir) throws Exception {
    Logger log = Logger.get(JMeterRunner.class);
    if (Files.exists(reportDir)) deleteDir(reportDir.toFile());
    if (Files.exists(jtl)) Files.delete(jtl);
    String jmeterCmd = resolveJMeterCommand();
    log.info("JMeter 可执行: " + jmeterCmd);
    String[] runCmd = new String[]{jmeterCmd, "-n", "-t", jmx.toAbsolutePath().toString(), "-l", jtl.toAbsolutePath().toString()};
    int code = exec(runCmd);
    if (code != 0) throw new RuntimeException("JMeter执行失败: " + code);
    long lines = Files.exists(jtl) ? Files.lines(jtl).count() : 0;
    if (lines > 1) {
      String[] reportCmd = new String[]{jmeterCmd, "-g", jtl.toAbsolutePath().toString(), "-e", "-o", reportDir.toAbsolutePath().toString()};
      int rc = exec(reportCmd);
      if (rc == 0) {
        log.info("报告已生成: " + reportDir.toAbsolutePath());
      } else {
        log.warn("报告生成失败: code=" + rc + ", 目录: " + reportDir.toAbsolutePath());
      }
    } else {
      log.warn("JTL为空或无样本，跳过报告生成。");
    }
  }

  private int exec(String[] cmd) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    Process p = pb.start();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      String line;
      Logger log = Logger.get(JMeterRunner.class);
      while ((line = r.readLine()) != null) log.info(line);
    }
    return p.waitFor();
  }

  private String resolveJMeterCommand() {
    String os = System.getProperty("os.name", "").toLowerCase();
    boolean isWindows = os.contains("win");
    String envHome = System.getenv("JMETER_HOME");
    if (envHome != null && !envHome.isEmpty()) {
      return isWindows ? envHome + "\\bin\\jmeter.bat" : envHome + "/bin/jmeter";
    }
    // Try known portable path
    String portable = isWindows ? "C:\\1-PortableSoft\\apache-jmeter-5.6.3\\bin\\jmeter.bat" : "/opt/apache-jmeter-5.6.3/bin/jmeter";
    if (new File(portable).exists()) return portable;
    // Fallback to PATH
    return "jmeter";
  }

  private void deleteDir(File f) {
    File[] files = f.listFiles();
    if (files != null) {
      for (File c : files) {
        if (c.isDirectory()) deleteDir(c); else c.delete();
      }
    }
    f.delete();
  }
}
