package com.example.jmeterai.model;

public class SummaryMetrics {
  public long total;
  public long success;
  public long fail;
  public double errorRate;
  public double avg;
  public double p95;
  public double p99;
  public double min;
  public double max;
  public java.util.Map<String, LabelStat> byLabel = new java.util.LinkedHashMap<>();
}
