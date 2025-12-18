package com.example.jmeterai.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class TestCase {
  public String name;
  public String method;
  public String path;
  public String body;
  public Map<String,String> headers = new LinkedHashMap<>();
  public Map<String,String> queryParams = new LinkedHashMap<>();
  public Map<String,String> pathParams = new LinkedHashMap<>();
  public String goal;
  public List<Assertion> assertions = new ArrayList<>();
  public List<String> tags;
}
