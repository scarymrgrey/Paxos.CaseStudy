////#full-example
//package com.example
//
//import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
//import com.example.Client.Greet
//import com.example.Client.Greeted
//import org.scalatest.wordspec.AnyWordSpecLike
//
////#definition
//class AkkaQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
////#definition
//
//  "A Greeter" must {
//    //#test
//    "reply to greeted" in {
//      val replyProbe = createTestProbe[Greeted]()
//      val underTest = spawn(Client())
//      underTest ! Greet("Santa", replyProbe.ref)
//      replyProbe.expectMessage(Greeted("Santa", underTest.ref))
//    }
//    //#test
//  }
//
//}
////#full-example
