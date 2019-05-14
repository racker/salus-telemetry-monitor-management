package com.rackspace.salus.monitor_management.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ZoneDeletionNotAllowed extends RuntimeException {
  public ZoneDeletionNotAllowed(String message) { super(message);}
}
