package cider_ci;

import clojure.lang.ExceptionInfo;
import clojure.lang.IPersistentMap;

public class WebstackException extends ExceptionInfo {
  public WebstackException(String s, IPersistentMap data) {
    super(s, data);
  }
}
