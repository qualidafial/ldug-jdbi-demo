package com.example.ldug.todo;

import java.net.URI;
import java.util.Objects;

import org.glassfish.jersey.linking.Binding;
import org.glassfish.jersey.linking.InjectLink;

public class Todo {

  private Integer id;
  private String title;
  private Boolean completed;

  @InjectLink(
      resource = TodoResource.class,
      method = "getById",
      style = InjectLink.Style.ABSOLUTE,
      bindings = @Binding(name = "id", value = "${instance.id}"),
      rel = "self"
  )
  private URI url;

  public Todo() {
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Boolean getCompleted() {
    return completed;
  }

  public void setCompleted(Boolean completed) {
    this.completed = completed;
  }

  public URI getUrl() {
    return url;
  }

  public void setUrl(URI url) {
    this.url = url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Todo todo = (Todo) o;
    return Objects.equals(id, todo.id) &&
        Objects.equals(title, todo.title) &&
        Objects.equals(completed, todo.completed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, completed);
  }

  @Override
  public String toString() {
    return "Todo{" +
        "id=" + id +
        ", title='" + title + '\'' +
        ", completed=" + completed +
        ", url=" + url +
        '}';
  }
}