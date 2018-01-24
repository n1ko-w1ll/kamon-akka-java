package org.example;

import java.util.concurrent.TimeUnit;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import kamon.Kamon;
import kamon.trace.Span;
import scala.concurrent.duration.FiniteDuration;

final class GreetingsHandler extends AbstractLoggingActor {

  static Props props() {
    return Props.create(GreetingsHandler.class, GreetingsHandler::new);
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder
        .create()
        .match(String.class, msg -> {
          log().info("Received message '{}'", msg);

          final Span span = Kamon.buildSpan("sayHello")
              .asChildOf(Kamon.currentSpan())
              .start();

          getContext().getSystem().scheduler().scheduleOnce(
              FiniteDuration.create(2, TimeUnit.SECONDS),
              getSelf(),
              msg,
              getContext().dispatcher(),
              getSender());
          getContext().become(waiting(span));
        })
        .matchAny(m -> {
          log().info("Received unknown message: {}", m);
          stop(null);
        })
        .build();
  }

  private Receive waiting(final Span span) {
    return ReceiveBuilder
        .create()
        .match(String.class, msg -> msg.equals("world"), msg -> {
          getSender().tell("akka", ActorRef.noSender());
          stop(span);
        })
        .match(String.class, msg -> {
          getSender().tell("world", ActorRef.noSender());
          stop(span);
        })
        .matchAny(m -> {
          span.addError("true");
          log().info("Received unknown message: {}", m);
          stop(span);
        })
        .build();
  }

  private void stop(final Span span) {
    if (span != null) {
      span.finish();
    }
    log().debug("Stopping actor");
    getContext().stop(getSelf());
  }
}
