package com.example

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.example.Replica.Ballot

import java.time.Instant
import scala.collection.mutable
import scala.language.{postfixOps, reflectiveCalls}
import scala.util.Random

object Replica {

  import BallotOrdering._
  final case class Ballot(ballotNumber: Long, guid: String)

  final case class Voice(ballot: Ballot, value: String)

  sealed trait TMessage

  object ClusterState {

    final case class ConfigurationChanged(replicaSet: Set[ActorRef[TMessage]]) extends TMessage

  }

  object Coordinator {

    final case class InitVote(value: String, sender: ActorRef[TMessage]) extends TMessage

    final case class ClusterOutOfOperability() extends TMessage

    final case class ElectionFinished(value: String) extends TMessage

  }

  object Proposer {

    final case class Prepare(ballot: Ballot, sender: ActorRef[TMessage]) extends TMessage

    final case class Propose(ballot: Ballot, value: String, sender: ActorRef[TMessage]) extends TMessage

  }

  object Acceptor {

    final case class Promise(voice: Option[Voice], sender: ActorRef[TMessage]) extends TMessage

    final case class Accepted(voice: Voice, sender: ActorRef[TMessage]) extends TMessage

  }

  class ProposerState(var ballot: Option[Ballot], var initialValue: Option[String], val voices: mutable.ListBuffer[Option[Voice]], val acceptedVales: mutable.ListBuffer[Voice])

  class AcceptorState(var biggestBallot: Option[Ballot], var acceptedValue: Option[String])

  val ReplicaServiceKey: ServiceKey[TMessage] = ServiceKey[TMessage]("replica")

  def apply(): Behavior[Any] = {
    Behaviors.setup[Any] { context =>
      val guid = java.util.UUID.randomUUID.toString
      val majQuo = 2
      var replicaSet = Set.empty[ActorRef[TMessage]]
      val proposerState = new ProposerState(None, None, mutable.ListBuffer.empty[Option[Voice]], mutable.ListBuffer.empty[Voice])
      val acceptorState = new AcceptorState(None, None)

      context.system.receptionist ! Receptionist.Register(ReplicaServiceKey, context.self)

      Behaviors.receive { (context, message) =>
        message match {

          case ClusterState.ConfigurationChanged(replicas) =>
            replicaSet = replicas

          case Coordinator.InitVote(value, sender) =>
            if (replicaSet.size < 3)
              sender ! Coordinator.ClusterOutOfOperability()
            else {
              proposerState.initialValue = Some(value)
              val ballot = Ballot(Instant.now.getEpochSecond, guid)
              proposerState.ballot = Some(ballot)
              Random
                .shuffle(replicaSet)
                .take(majQuo)
                .foreach(_ ! Proposer.Prepare(ballot, context.self))
            }

          case Proposer.Prepare(ballot, sender) =>
            acceptorState.biggestBallot match {
              case None =>
                acceptorState.biggestBallot = Some(ballot)
                sender ! Acceptor.Promise(None, context.self)
              case Some(bigBallot) =>
                if (ballot > bigBallot) {
                  val voice = for {
                    v <- acceptorState.acceptedValue
                  } yield Voice(bigBallot, v)

                  sender ! Acceptor.Promise(voice, context.self)
                  acceptorState.biggestBallot = Some(ballot)
                }
            }

          case Acceptor.Promise(voice, sender) =>
            proposerState.voices.addOne(voice)
            if (proposerState.voices.size == majQuo) {
              implicit val userOrdering: Ordering[Ballot] = Ordering.by(_.ballotNumber)
              val biggest: Voice = proposerState.voices.maxBy(r => r.get.ballot) match {
                case None => Voice(proposerState.ballot.get, proposerState.initialValue.get)
                case Some(voice) => Voice(proposerState.ballot.get, voice.value)
              }

              Random.shuffle(replicaSet).take(majQuo).foreach {
                _ ! Proposer.Propose(biggest.ballot, biggest.value, context.self)
              }
            }

          case Proposer.Propose(ballot, value, sender) =>

            if (ballot > ballot) {
              acceptorState.biggestBallot = Some(ballot)
              acceptorState.acceptedValue = Some(value)
              sender ! Acceptor.Accepted(Voice(ballot, value), context.self)
            }

          case Acceptor.Accepted(voice, sender) =>
            proposerState.acceptedVales.addOne(voice)
        }
        Behaviors.same
      }
    }.narrow
  }


}

class BallotOrdering(val bal: Ballot) {
  def >(other: Ballot): Boolean = bal.ballotNumber > other.ballotNumber
}

object BallotOrdering {
  implicit def fromInt(n: Ballot): BallotOrdering = new BallotOrdering(n)
}