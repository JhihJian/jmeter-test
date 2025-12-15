package com.example.jmeterai.util;

public class ModelUtils {
  public static String extractXml(String s) {
    String t = stripCodeFences(s).trim();
    int start = t.indexOf("<?xml");
    if (start >= 0) t = t.substring(start);
    return t;
  }
  public static String stripCodeFences(String s) {
    String t = s;
    int fenceStart = t.indexOf("```\n");
    int fenceXml = t.indexOf("```xml");
    if (fenceXml >= 0) fenceStart = fenceXml;
    if (fenceStart >= 0) {
      int end = t.indexOf("```", fenceStart + 3);
      if (end > fenceStart) {
        return t.substring(fenceStart, end).replaceAll("```xml", "").replaceAll("```", "").trim();
      }
    }
    return t;
  }
}
