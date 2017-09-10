package com.example.ldug.todo;

import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.linking.DeclarativeLinkingFeature;

public class TodoApplication extends Application<TodoConfig> {

  public static void main(String[] args) throws Exception {
    new TodoApplication().run("server", "src/main/resources/config.yml");
  }

  @Override
  public void initialize(Bootstrap<TodoConfig> bootstrap) {
    bootstrap.addBundle(new AssetsBundle("/assets", "/", "index.html"));
  }

  @Override
  public void run(TodoConfig configuration, Environment environment) throws Exception {
    environment.jersey().setUrlPattern("/api/*");
    environment.jersey().register(new TodoResource());

    environment.jersey().register(DeclarativeLinkingFeature.class);
    addCorsHeader(environment);
  }

  private void addCorsHeader(Environment environment) {
    FilterRegistration.Dynamic filter = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    filter.setInitParameter("allowedOrigins", "*");
    filter.setInitParameter("allowedMethods", "GET,PUT,POST,DELETE,OPTIONS,HEAD,PATCH");
  }
}