package com.example.jmeterai.model;

import java.util.ArrayList;
import java.util.List;

public class ApiUnderstandingResult {
  public boolean isJson;
  public List<String> tags = new ArrayList<>();
  public List<String> schemas = new ArrayList<>();
  public boolean authNeeded;
  public String tokenEndpoint;
  public List<String> commonParams = new ArrayList<>();
  public String summaryText;
}
