package com.example.jmeterai;

import java.util.LinkedHashMap;
import java.util.Map;

public class LabelStat {
  public String label;
  public long total;
  public long success;
  public long fail;
  public Map<String,Long> codes = new LinkedHashMap<>();
}
