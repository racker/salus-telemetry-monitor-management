package com.rackspace.salus.monitor_management.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DeletionNotAllowed extends RuntimeException {
  public DeletionNotAllowed(String message) { super(message);}
}
