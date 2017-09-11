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

As you follow the tutorial, restart the app to see each take take effect.

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

Add a `DataSourceFactory` property to `TodoConfig`:

```java
private final DataSourceFactory database = new DataSourceFactory();

public DataSourceFactory getDatabase() {
  return database;
}
```

Add database configuration to the end config.yml:

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

Spin up `DataSource` on app startup:

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

Add flyway-core dependency:

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

Run database migrations on app startup:

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

Bootstrap Jdbi on application startup, and bind it as an injectable dependency:

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

Write a SQL Object interface for the todos table:

```java
@RegisterBeanMapper(Todo.class)
public interface TodoDao {
  @SqlQuery("SELECT * FROM todos ORDER BY id")
  List<Todo> list();

  @SqlQuery("SELECT * FROM todos WHERE id = ?")
  Todo getById(int id);

  @SqlUpdate("INSERT INTO todos (title) VALUES (:title)")
  @GetGeneratedKeys
  Todo insert(@BindBean Todo todo);

  @SqlUpdate("UPDATE todos SET " +
      "title = coalesce(:title, title), " +
      "completed = coalesce(:completed, completed) " +
      "WHERE id = :id")
  @GetGeneratedKeys
  Todo update(@BindBean Todo todo);

  @SqlUpdate("DELETE FROM todos WHERE id = ?")
  void deleteById(int id);

  @SqlUpdate("DELETE FROM todos")
  void deleteAll();
}
```

Modify `TodoResource` to switch from in-memory to Postgres data store:

```java
@Path("/todo")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class TodoResource {

  private final TodoDao dao;

  @Inject
  public TodoResource(Jdbi jdbi) {
    dao = jdbi.onDemand(TodoDao.class);
  }

  @GET
  public Collection<Todo> get() {
    return dao.list();
  }

  @GET
  @Path("{id}")
  public Todo getById(@PathParam("id") int id) {
    return dao.getById(id);
  }

  @POST
  public Todo addTodos(Todo todo) {
    return dao.insert(todo);
  }

  @DELETE
  public void delete() {
    dao.deleteAll();
  }

  @DELETE
  @Path("{id}")
  public void deleteById(@PathParam("id") int id) {
    dao.deleteById(id);
  }

  @PATCH
  @Path("{id}")
  public Todo edit(@PathParam("id") int id, Todo patch) {
    patch.setId(id);
    return dao.update(patch);
  }
}
```
