# Jdbi Tutorial

This project uses code from two other open source projects:

* The server side code comes from
  [todo-backend-dropwizard](https://github.com/danielsiwiec/todo-backend-dropwizard).
* The UI was taken from the Vanilla JS example of the
  [TodoMVC](https://github.com/tastejs/todomvc/tree/master/examples/vanillajs)
  project.

Clone this Github project to your computer, then open it in your IDE of choice.

Run the `TodoApplication` class in your IDE, then open your browser to
http://localhost:8088/ to test out the app.

Initially, the app saves todos in Java memory on the server, so todos will be lost
whenever you restart the app.

As you follow the tutorial, restart the app to see each change take effect.

Let's begin!

## Set up Postgres Database

Install Postgres (Linux):

```bash
$ sudo apt install postgresql
```

Install Postgres (Mac):

```bash
$ brew install postgresql
```

Create ldug postgres user and database:

```bash
$ sudo -i -u postgres
$ createuser --interactive

Enter name of role to add: ldug
Shall the new role be a superuser? (y/n) y

$ psql

postgres=# \password ldug
Enter new password: password
Enter it again: password

postgres=# \q

$ createdb -O ldug ldug
```

## Configure Dropwizard to connect to Postgres

Add dropwizard-db and postgresql dependencies to `pom.xml`:

```xml
<properties>
  <postgres.version>42.1.4</postgres.version>
</properties>

<dependencies>
  <dependency>
    <groupId>io.dropwizard</groupId>
    <artifactId>dropwizard-db</artifactId>
  </dependency>

  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>${postgres.version}</version>
  </dependency>
</dependencies>
```

Note: Fold the new properties and dependencies above into the respective
sections already in the POM.

Add a `DataSourceFactory` property to `TodoConfig`:

```java
private final DataSourceFactory database = new DataSourceFactory();

public DataSourceFactory getDatabase() {
  return database;
}
```

Add database configuration to the end of `config.yml`:

```yaml
database:
  driverClass: org.postgresql.Driver
  user: ldug
  password: password
  url: jdbc:postgresql://localhost:5432/ldug
  properties:
    charSet: UTF-8
  maxWaitForConnection: 1s
  validationQuery: "select 1"
  minSize: 2
  maxSize: 8
  checkConnectionWhileIdle: false
  evictionInterval: 10s
  minIdleTime: 1 minute
```

Spin up `DataSource` on app startup, in `TodoApplication.run()`:

```java
log.info("creating data source");
ManagedDataSource dataSource = configuration.getDatabase().build(environment.metrics(), "db");
environment.lifecycle().manage(dataSource);

log.info("registering database healthcheck");
environment.healthChecks().register("db", new HealthCheck() {
  @Override
  protected HealthCheck.Result check() throws Exception {
    try (Connection conn = dataSource.getConnection()) {
      conn.prepareStatement(configuration.getDatabase().getValidationQuery()).execute();
      return Result.healthy();
    }
  }
});
```

## Initialize database using Flyway migrations

Add flyway-core dependency to `pom.xml`:

```xml
<properties>
  <flyway.version>4.2.0</flyway.version>
</properties>

<dependencies>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>${flyway.version}</version>
  </dependency>
</dependencies>
```

Write an SQL database migration file to set up the `todos` table:

`src/main/resource/db/migration/V1__Initial_Setup.sql`
```sql
create table todos (
  id serial primary key,
  title text not null,
  completed boolean not null default false
);
```

Run database migrations on app startup, in `TodoApplication.run()`:

```java
log.info("running database migrations");
Flyway flyway = new Flyway();
flyway.setDataSource(dataSource);
flyway.migrate();
```

If you run the app after this step, you should then be able to connect to the database
and verify that the todos table has been created.

## Set up Jdbi

Add Jdbi dependencies to `pom.xml`:

```xml
<properties>
  <jdbi.version>3.0.0-beta2</jdbi.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.jdbi</groupId>
      <artifactId>jdbi3-bom</artifactId>
      <version>${jdbi.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.jdbi</groupId>
    <artifactId>jdbi3</artifactId>
  </dependency>
  <dependency>
    <groupId>org.jdbi</groupId>
    <artifactId>jdbi3-sqlobject</artifactId>
  </dependency>
</dependencies>
```

Bootstrap Jdbi on application startup, and bind it as an injectable dependency
in `TodoApplication.run():

```java
log.info("bootstrapping jdbi");
environment.jersey().register(new AbstractBinder() {
  @Override
  protected void configure() {
    Jdbi jdbi = Jdbi.create(dataSource)
        .installPlugin(new SqlObjectPlugin());

    bind(jdbi).to(Jdbi.class);
  }
});
```

## Modify `TodoResource` to use Jdbi instead of Map

Start a SQL Object interface for the todos table. We'll start with a method
to query all todos in the table.

```java
public interface TodoDao {
  @SqlQuery("SELECT * FROM todos ORDER BY id")
  @RegisterBeanMapper(Todo.class)
  List<Todo> list();
}
```

We use the `@SqlQuery` annotation since this is a query, not an update.

Out of the box, Jdbi supports mapping to most standard Java data types.
However Jdbi doesn't know anything about `Todo`, so we have to give it a hint
on how to map database rows into `Todo`s.

The `@RegisterBeanMapper` annotation tells Jdbi that `Todo` is a Java Bean.
The bean mapper will match up column names (including snake_case columns)
with java property names.

Now let's update `TodoResource` to use our new SQL Object:

```java
public class TodoResource {
  private final TodoDao dao;

  @Inject
  public TodoResource(Jdbi jdbi) {
    dao = jdbi.onDemand(TodoDao.class);
  }

  @GET
  public Collection<Todo> list() {
    return dao.list();
  }
}
```

Since we set up dependency injection bindings earlier for the `Jdbi` type, we can
inject it into our resource class.

Next, we ask Jdbi for an "on-demand" SQL Object. On-demand means that the instance
does not hold an open connection. Instead, it opens a temporary connection for each
method call, and closes the connection as the method call returns.

Final, we modify the `list()` method to delegate to `dao.list()`.

Let's restart the app and see what happens.

When we open up Chrome Dev Tools, we can see that when the page is loaded, the UI
is requesting the todo list from the server, and gets back an empty array.

Also notice, that nothing happens if we try to add a todo, because the other methods
in the resource class are still storing todos in the `Map` field.

Let's move to the next method, `create()`. First we add a method to our DAO: 

```java
@SqlUpdate("INSERT INTO todos (title) VALUES (:title)")
@GetGeneratedKeys
@RegisterBeanMapper(Todo.class)
Todo insert(@BindBean Todo todo);
```

We use the `@SqlUpdate` annotation since this is a database modification.

The `@GetGeneratedKeys` annotation needs a little explanation. Because `todos.id` is
a `serial` column, every new row has the id automatically numbered. ID is a generated
key. Normally you would use `@GetGeneratedKeys` to get back an autonumbered key that
was generated by the database.

However, Postgres will return the entire database row as generated keys for you.
So by using the `@GetGeneratedKeys` annotation, we can do an insert and skip doing
a separate query to the database to get the full row.

The `@BindBean` annotation tells Jdbi that `todo` is a Java Bean, and that its bean
properties should be bound as named parameters. Since `Todo` has a `title` property,
the title from the `todo` object gets bound to the insert statement.

Finally, we register a bean mapper for the `Todo` class so Jdbi knows how to map
result rows into `Todo` objects.

If you find yourself adding the same register annotation to multiple methods, you can
just move the annotation up to the interface level, and it will apply to all methods
on the interface:

```java
@RegisterBeanMapper(Todo.class)
public interface TodoDao {
  @SqlQuery("SELECT * FROM todos ORDER BY id")
  @RegisterBeanMapper(Todo.class)
  List<Todo> list();

  @SqlUpdate("INSERT INTO todos (title) VALUES (:title)")
  @GetGeneratedKeys
  Todo insert(@BindBean Todo todo);
}
```

Now, update `TodoResource` to use our new DAO method:

```java
@POST
public Todo create(Todo todo) {
  return dao.insert(todo);
}
```

Restart the app, and now when we add new todos, they show up!
If we refresh the browser or restart the app, the todos we added
are still there.

Moving on to the `getById()` method in the DAO:

```java
@SqlQuery("SELECT * FROM todos WHERE id = ?")
Todo getById(int id);
```

Here we've used a `?` marker, which binds parameters positionally.

If our method had more than one parameter, then the first argument would be bound
to the first `?`, the second argument to the second `?`, and so on.

Alternatively, we could use named parameters, and add a `@Bind` annotation to the argument:

```java
@SqlQuery("SELECT * FROM todos WHERE id = :id")
Todo getById(@Bind("id") int id);
```

Then we update the resource class to use the `getById` DAO method:

```java
@GET
@Path("{id}")
public Todo getById(@PathParam("id") int id) {
  return dao.getById(id);
}
```

So funny thing.. the UI doesn't actually talk to this resource. However, if we restart
the server, we can navigate directly to a todo resource in the browser and see that
it works.

Next the `update` DAO method:

```java
@SqlUpdate("UPDATE todos SET " +
    "title = coalesce(:title, title), " +
    "completed = coalesce(:completed, completed) " +
    "WHERE id = :id")
@GetGeneratedKeys
Todo update(@BindBean Todo todo);
```

You'll notice we're using the `coalesce` postgres function in this statement. This
function returns the first of its arguments that is not null.

We do this because the UI may send incomplete payloads like `{"title": "milk the cows"}`
or `{"completed": true}`. Thus, `coalesce(:title, title)` will set `title` to `:title`
if the new value is not null, otherwise it will set it to ..itself. Likewise for the
`completed` column.

Now let's update the resource:

```java
@PATCH
@Path("{id}")
public Todo update(@PathParam("id") int id, Todo patch) {
  patch.setId(id);
  return dao.update(patch);
}
```

You'll notice that no matter what ID is sent in the request payload, we override it with
the ID from the URL path.

Now we can restart the app, and see that double-clicking a todo and saving a change
works like we expect.

On to the `deleteById()` DAO method:

```java
@SqlUpdate("DELETE FROM todos WHERE id = ?")
void deleteById(int id);
```

Nothing surprising here. On to the resource:

```java
@DELETE
@Path("{id}")
public void deleteById(@PathParam("id") int id) {
  dao.deleteById(id);
}
```

Restart the app, and if we check a todo as completed, we can click "Clear completed" and
see that the todos are removed.

Lastly, the `deleteAll()` method:

```java
@SqlUpdate("DELETE FROM todos")
void deleteAll();
```

Update the resource:

```java
@DELETE
public void deleteAll() {
  dao.deleteAll();
}
```

We can also get rid of the `Map` and counter fields now that they're unused. 

And we've finished! Let's restart and test!

Actually.. the UI doesn't use this resource either, but we can use Chrome Dev
Tools to test it out:

```javascript
xhr = new XMLHttpRequest();
xhr.open('DELETE', '/api/todo');
xhr.send();
```

Refresh the browser, and we see that all the todos have been removed.

So there you have it: a basic walkthrough of Jdbi.

For a more thorough guide, see https://jdbi.github.io.