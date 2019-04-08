/*
 * Copyright 2019 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.salus.monitor_management.entities;

import java.io.Serializable;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.hibernate.validator.constraints.NotBlank;

// Using the old validation exceptions for podam support
// Will move to the newer ones once they're supported.
//import javax.validation.constraints.NotBlank;

@Entity
@IdClass(BoundMonitor.PrimaryKey.class)
@Table(name = "bound_monitors")
@Data
public class BoundMonitor implements Serializable {

  @Data
  public static class PrimaryKey implements Serializable {

    UUID monitorId;
    String tenantId;
    String resourceId;
    String zone;
  }

  @Id
  @NotNull
  @Type(type = "uuid-char")
  @Column(length = 100)
  UUID monitorId;

  @Id
  @NotBlank
  @Column(length = 100)
  String tenantId;

  @Id
  @NotNull
  @Column(length = 100)
  String resourceId;

  @Lob
  String renderedContent;

  @Id
  @Column(length = 100)
  String zone;

  @Column(length = 100)
  String envoyId;
}