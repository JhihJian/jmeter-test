package com.example.jmeterai;

import java.util.LinkedHashMap;
import java.util.Map;

public class TestCase {
  public String name;
  public String method;
  public String path;
  public String body;
  public Map<String,String> headers = new LinkedHashMap<>();
  public Map<String,String> queryParams = new LinkedHashMap<>();
  public Map<String,String> pathParams = new LinkedHashMap<>();
}
