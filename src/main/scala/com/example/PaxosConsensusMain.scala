package com.example

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration.DurationInt

object Conf {
  val clusterSize = 9
  val majQuo = math.ceil(clusterSize / 2.0).toInt + 1
}

object ReplicaExample {

  import com.example.Replica.{ClusterState, Coordinator, TMessage}

  object Client {
    def apply(seed: ActorRef[TMessage], voteValue: String): Behavior[Any] =
      Behaviors.setup { context =>
        context.self ! Coordinator.InitVote(voteValue, context.self)
        Behaviors.receiveMessage {
          case Coordinator.ClusterOutOfOperability() =>
            import context.executionContext
            context.system.scheduler.scheduleOnce(2 seconds, () => seed ! Coordinator.InitVote(voteValue, context.self))
            Behaviors.same

          case Coordinator.ElectionFinished(value) =>
            context.log.info("Consensus achieved: {}", value)
            Behaviors.stopped

          case Coordinator.InitVote(_, _) =>
            seed ! Coordinator.InitVote(voteValue, context.self)
            Behaviors.same
        }
      }
  }

  object ClusterManager {
    def apply(): Behavior[Nothing] = {
      Behaviors
        .setup[Receptionist.Listing] { context =>
          val cluster = (0 to Conf.clusterSize).map(r => context.spawn(Replica(), "Replica" + r))

          context.system.receptionist ! Receptionist.Subscribe(Replica.ReplicaServiceKey, context.self)
          context.spawn(Client(cluster.head, "Trump!"), "Client0")
          context.spawn(Client(cluster.head, "Biden!"), "Client1")
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

object SingleDecreePaxos {

  import ReplicaExample._
  import akka.actor.typed.ActorSystem

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](ClusterManager(), "Paxos-case-study")
  }
}
