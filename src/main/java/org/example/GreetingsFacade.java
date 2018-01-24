package org.example;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

public final class GreetingsFacade extends AbstractLoggingActor {

  public static Props props() {
    return Props.create(GreetingsFacade.class, GreetingsFacade::new);
  }

  @Override
  public Receive createReceive() {
    return ReceiveBuilder
        .create()
        .match(String.class, msg -> getContext().actorOf(GreetingsHandler.props()).forward(msg, getContext()))
        .matchAny(m -> log().info("Received unknown message: {}", m))
        .build();
  }
}
