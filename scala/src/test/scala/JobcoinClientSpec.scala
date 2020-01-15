package com.gemini.jobcoin

import org.scalatest._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.gemini.jobcoin.JobcoinClient.{Address, Transaction}
import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

class JobcoinClientSpec extends FlatSpec with Matchers {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()
  val client = new JobcoinClient(config)

  "Jobcoin API Client" should "correctly retrieve address" in {
    // TODO test with missing fromAddress
    val addressTry: Try[Address] = Await.result(client.getAddress("Alice"), 5.seconds)
    addressTry match {
      case Failure(_) =>
        fail()
      case Success(address) => {
        address.name should be(expectedAliceAddress.name)
        address.balance should be(expectedAliceAddress.balance)
        address.transactions should contain(expectedAliceAddress.transactions(0))
        address.transactions should contain(expectedAliceAddress.transactions(1))
      }
    }
  }

  "Jobcoin API Client" should "return a failed Try for a non-existent account" in {

    // TODO test with missing fromAddress
    val addressTry: Try[Address] = Await.result(client.getAddress("SenderDoesNotExist"), 5.seconds)
    addressTry match {
      case Failure(_) =>
        // pass
      case Success(address) => {
        fail()
      }
    }
  }

  "Jobcoin API Client" should "correctly send jobcoins" in {
    // address1 starts with 50.0 and sends 10.0 to address2 who also starts with 50.0
    Await.result(
      client.sendJobcoins(
        fromAddress = "SenderAddressForScalaTest1",
        toAddress = "SenderAddressForScalaTest2",
        amount = 10.0
      ),
      5.seconds
    )

    // assert that sender 1 has 40.0 and sender2 has 50.0
    var address1Try: Try[Address] =
      Await.result(
        client.getAddress("SenderAddressForScalaTest1"),
        5.seconds
      )
    var address2Try: Try[Address] =
      Await.result(
        client.getAddress("SenderAddressForScalaTest2"),
        5.seconds
      )
    (address1Try, address2Try) match {
      case (Success(address1), Success(address2)) =>
        address1.balance should be(40.0)
        address2.balance should be(60.0)
      case _ => fail() // shouldn't get here
    }

    // address2 sends 10.0 back to address1 so that the test can be consistent on each run
    Await.result(
      client.sendJobcoins(
        fromAddress = "SenderAddressForScalaTest2",
        toAddress = "SenderAddressForScalaTest1",
        amount = 10.0
      ),
      5.seconds
    )

    // assert that sender 1 has 50.0 and sender2 has 50.0
    address1Try =
      Await.result(
        client.getAddress("SenderAddressForScalaTest1"),
        5.seconds
      )
    address2Try =
      Await.result(
        client.getAddress("SenderAddressForScalaTest2"),
        5.seconds
      )
    (address1Try, address2Try) match {
      case (Success(address1), Success(address2)) =>
        address1.balance should be(50.0)
        address2.balance should be(50.0)
      case _ => fail() // shouldn't get here
    }
  }

  private val expectedAliceAddress = Address(
    name = "Alice",
    balance = 37.5,
    transactions = Seq(
      Transaction(
        timestamp = new DateTime("2020-01-07T19:51:51.956Z").withZone(DateTimeZone.UTC),
        toAddress = "Alice",
        fromAddress = None,
        amount = 50.0
      ),
      Transaction(
        timestamp = new DateTime("2020-01-07T19:51:51.992Z").withZone(DateTimeZone.UTC),
        toAddress = "Bob",
        fromAddress = Some("Alice"),
        amount = 12.5
      )
    )
  )
}
