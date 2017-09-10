package com.example.ldug.todo;

import java.net.URI;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.glassfish.jersey.linking.Binding;
import org.glassfish.jersey.linking.InjectLink;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class Todo {

  private String title;
  private int id;
  private Boolean completed;
  private Integer order;

  @InjectLink(
      resource = TodoResource.class,
      method = "getById",
      style = InjectLink.Style.ABSOLUTE,
      bindings = @Binding(name = "id", value = "${instance.id}"),
      rel = "self"
  )
  private URI url;

  public void setId(int id) {
    this.id = id;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Todo patchFrom(Todo patch) {

    if (patch.completed != null) {
      completed = patch.completed;
    }

    if (patch.title != null) {
      title = patch.title;
    }

    if (patch.order != null) {
      order = patch.order;
    }

    return this;
  }

  public void setCompleted(boolean completed) {
    this.completed = completed;
  }
}