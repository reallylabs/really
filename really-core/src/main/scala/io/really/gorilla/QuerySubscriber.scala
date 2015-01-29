package io.really.gorilla

import akka.actor.{ ActorLogging, Actor }
import io.really.model.Model
import io.really.ReallyGlobals

class QuerySubscriber(globals: ReallyGlobals, subscriptionId: String,
    model: Model,
    querySubscription: QuerySubscription) extends Actor with ActorLogging {
  //todo on start register on model modifications

  def receive = {
    case PersistentCreatedEvent(event) =>
    //validate by query filter
    //apply onGet for the object
    //filter fields
    //push on pushChannel
  }
}