package org.example;

import static akka.pattern.PatternsCS.ask;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.util.Timeout;
import kamon.Kamon;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class GreetingsApplication extends AllDirectives {

  private static final Timeout REQUEST_TIMEOUT = Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS));

  public static void main(String[] args) throws Exception {
    final Config config = ConfigFactory.load();
    final ActorSystem system = ActorSystem.create("greeter", config);
    final Http http = Http.get(system);
    final ActorMaterializer materializer = ActorMaterializer.create(system);

    final GreetingsApplication app = new GreetingsApplication(system, http, materializer, config);

    final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = app.createRoute().flow(system, materializer);
    final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow, ConnectHttp.toHost("localhost", 8080), materializer);

    binding.exceptionally(failure -> {
      System.err.println("Something very bad happened! " + failure.getMessage());
      system.terminate();
      return null;
    });
  }

  private final ActorRef greetings;

  private GreetingsApplication(final ActorSystem system, final Http http, final ActorMaterializer materializer, final Config config) {
    this.greetings = system.actorOf(GreetingsFacade.props(), "greetings");
  }

  private Route createRoute() {
    return path("greeting", () ->
        logResult("Result", () ->
            logRequest("Request", () -> {

              // rename the span to avoid cardinality explosion (because request parameter are part of the operation name otherwise)
              Kamon.currentSpan().setOperationName("greeting");

              return get(() -> completeOKWithFuture(
                  ask(greetings, "hello", REQUEST_TIMEOUT)
                      .thenCompose(result -> ask(greetings, result, REQUEST_TIMEOUT)), Jackson.marshaller()));
            })
        )
    );
  }
}
