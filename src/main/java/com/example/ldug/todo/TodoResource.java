package com.example.ldug.todo;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.google.common.collect.Maps;
import io.dropwizard.jersey.PATCH;

@Path("/todo")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class TodoResource {

  private Map<Integer, Todo> todos = Maps.newHashMap();
  private int counter = 0;

  @Inject
  public TodoResource() {
  }

  @GET
  public Collection<Todo> list() {
    return todos.values();
  }

  @POST
  public Todo create(Todo todo) {
    todo.setId(++counter);
    todo.setCompleted(false);
    todos.put(counter, todo);
    return todo;
  }

  @GET
  @Path("{id}")
  public Todo getById(@PathParam("id") int id) {
    return todos.get(id);
  }

  @PATCH
  @Path("{id}")
  public Todo update(@PathParam("id") int id, Todo patch) {
    Todo todo = todos.get(id);

    Optional.ofNullable(patch.getCompleted()).ifPresent(todo::setCompleted);
    Optional.ofNullable(patch.getTitle()).ifPresent(todo::setTitle);

    todos.put(id, todo);

    return todo;
  }

  @DELETE
  @Path("{id}")
  public void deleteById(@PathParam("id") int id) {
    todos.remove(id);
  }

  @DELETE
  public void deleteAll() {
    todos.clear();
  }
}