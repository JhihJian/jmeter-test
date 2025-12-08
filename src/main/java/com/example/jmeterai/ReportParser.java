package com.example.jmeterai;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReportParser {
  public SummaryMetrics parseJtl(Path jtl) throws IOException {
    Logger log = Logger.get(ReportParser.class);
    List<Double> elapses = new ArrayList<>();
    long total = 0;
    long success = 0;
    int idxElapsed = -1, idxSuccess = -1, idxLabel = -1, idxCode = -1;
    try (BufferedReader r = Files.newBufferedReader(jtl)) {
      String header = r.readLine();
      if (header != null) {
        String[] hs = header.split(",");
        for (int i = 0; i < hs.length; i++) {
          String h = hs[i].trim();
          if (h.equalsIgnoreCase("elapsed")) idxElapsed = i;
          if (h.equalsIgnoreCase("success")) idxSuccess = i;
          if (h.equalsIgnoreCase("label")) idxLabel = i;
          if (h.equalsIgnoreCase("responseCode")) idxCode = i;
        }
        if (!header.contains(",")) {
          header = null;
        }
      }
      String line;
      while ((line = r.readLine()) != null) {
        String[] parts = line.split(",");
        if (parts.length == 0) continue;
        double e = tryParse(parts, idxElapsed, 1);
        boolean ok = tryParseBool(parts, idxSuccess, 7);
        String label = tryParseStr(parts, idxLabel, 2);
        String code = tryParseStr(parts, idxCode, 3);
        if (e >= 0) elapses.add(e);
        if (ok) success++;
        total++;
        String key = label == null ? "" : label;
        LabelStat ls = mByLabel.computeIfAbsent(key, k -> new LabelStat());
        ls.label = key;
        ls.total++;
        if (ok) ls.success++; else ls.fail++;
        String c = code == null ? "" : code;
        ls.codes.put(c, ls.codes.getOrDefault(c, 0L) + 1);
      }
    }
    SummaryMetrics m = new SummaryMetrics();
    m.total = total;
    m.success = success;
    m.fail = total - success;
    m.errorRate = m.fail * 1.0 / Math.max(1, m.total);
    if (elapses.isEmpty()) {
      m.avg = 0; m.p95 = 0; m.p99 = 0; m.min = 0; m.max = 0;
    } else {
      double sum = 0; for (double e : elapses) sum += e;
      Collections.sort(elapses);
      m.p95 = percentile(elapses, 0.95);
      m.p99 = percentile(elapses, 0.99);
      m.min = elapses.get(0);
      m.max = elapses.get(elapses.size() - 1);
      m.avg = sum / elapses.size();
    }
    m.byLabel = mByLabel;
    if (parseErrors > 0 && total > 0) {
      double rate = parseErrors * 1.0 / total;
      log.warn("JTL 字段解析异常条数: " + parseErrors + ", 占比: " + String.format("%.2f%%", rate*100));
    }
    return m;
  }

  private final java.util.Map<String, LabelStat> mByLabel = new java.util.LinkedHashMap<>();
  private long parseErrors = 0;

  private double tryParse(String[] parts, int idx, int fallbackIdx) {
    try {
      if (idx >= 0 && idx < parts.length) return Double.parseDouble(parts[idx]);
      return Double.parseDouble(parts[fallbackIdx]);
    } catch (Exception e) { parseErrors++; return -1; }
  }
  private boolean tryParseBool(String[] parts, int idx, int fallbackIdx) {
    try {
      if (idx >= 0 && idx < parts.length) return Boolean.parseBoolean(parts[idx]);
      return Boolean.parseBoolean(parts[fallbackIdx]);
    } catch (Exception e) { parseErrors++; return false; }
  }
  private String tryParseStr(String[] parts, int idx, int fallbackIdx) {
    try {
      if (idx >= 0 && idx < parts.length) return parts[idx];
      return parts[fallbackIdx];
    } catch (Exception e) { parseErrors++; return null; }
  }

  private double percentile(List<Double> sorted, double p) {
    int n = sorted.size();
    if (n == 0) return 0;
    double idx = p * (n - 1);
    int i = (int) Math.floor(idx);
    int j = (int) Math.ceil(idx);
    if (i == j) return sorted.get(i);
    double w = idx - i;
    return sorted.get(i) * (1 - w) + sorted.get(j) * w;
  }
}
