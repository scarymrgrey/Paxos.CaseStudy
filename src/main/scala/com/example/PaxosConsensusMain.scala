package com.example

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.example.Replica.{ClusterState, Coordinator}

import scala.concurrent.duration.DurationInt

object ReplicaExample {
  import com.example.Replica.{ClusterState, Coordinator, TMessage}

  object Client {
    def apply(seed: ActorRef[TMessage]): Behavior[Any] =
      Behaviors.setup { context =>
        context.self ! "start"
        Behaviors.receiveMessage {
          case _ =>
            seed ! Coordinator.InitVote("Trump!", context.self)
            Behaviors.same
          case Coordinator.ClusterOutOfOperability() =>
            import context.executionContext
            context.system.scheduler.scheduleOnce(2 seconds, () => seed ! Coordinator.InitVote("Trump!", context.self))
            Behaviors.same
        }
      }
  }

  object ClusterManager {
    def apply(): Behavior[Nothing] = {
      Behaviors
        .setup[Receptionist.Listing] { context =>
          val seed = context.spawn(Replica(), "Replica-1")
          context.spawn(Replica(), "Replica-2")
          context.spawn(Replica(), "Replica-3")

          context.system.receptionist ! Receptionist.Subscribe(Replica.ReplicaServiceKey, context.self)
          context.spawn(Client(seed), "Client")
          Behaviors.receiveMessagePartial[Receptionist.Listing] {
            case Replica.ReplicaServiceKey.Listing(listings) =>
              listings.foreach(_ ! ClusterState.ConfigurationChanged(listings))
              Behaviors.same
          }
        }
        .narrow
    }
  }
}

object AtomicRegister {
  import ReplicaExample._
  import akka.actor.typed.ActorSystem

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](ClusterManager(), "Paxos-case-study")
  }
}
