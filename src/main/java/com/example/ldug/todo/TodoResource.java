package com.example.ldug.todo;

import com.google.common.collect.Maps;
import io.dropwizard.jersey.PATCH;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Map;

@Path("/todo")
@Produces(MediaType.APPLICATION_JSON)
public class TodoResource {

    private Map<Integer, Todo> todos = Maps.newHashMap();
    private int counter = 0;

    {
        Todo changeOil = new Todo();
        changeOil.setTitle("Change Oil");
        addTodos(changeOil);

        Todo buyMilk = new Todo();
        buyMilk.setTitle("Buy Milk");
        addTodos(buyMilk);
    }

    @GET
    public Collection<Todo> get() {
        return todos.values();
    }

    @GET
    @Path("{id}")
    public Todo getById(@PathParam("id") int id) {
        return todos.get(id);
    }

    @POST
    public Todo addTodos(Todo todo) {
        todo.setId(++counter);
        todo.setCompleted(false);
        todos.put(counter, todo);
        return todo;
    }

    @DELETE
    public void delete() {
        todos.clear();
    }

    @DELETE
    @Path("{id}")
    public void deleteById(@PathParam("id") int id) {
        todos.remove(id);
    }

    @PATCH
    @Path("{id}")
    public Todo edit(@PathParam("id") int id, Todo patch) {
        Todo todo = todos.get(id);
        Todo patchedTodo = todo.patchFrom(patch);
        todos.put(id, patchedTodo);
        return patchedTodo;
    }
}