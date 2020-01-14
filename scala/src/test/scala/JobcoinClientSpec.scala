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

class JobcoinClientSpec extends FlatSpec with Matchers {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "Jobcoin API Client" should "correctly retrieve address" in {
    val config = ConfigFactory.load()
    val client = new JobcoinClient(config)
    val response: Address = Await.result(client.testGetAddress("Alice"), 5.seconds)
    response.balance should be(expectedAliceAddress.balance)
    response.transactions should contain(expectedAliceAddress.transactions(0))
    response.transactions should contain(expectedAliceAddress.transactions(1))
  }

  private val expectedAliceAddress = Address(
    balance = 37.5,
    transactions = Seq(
      Transaction(
        timestamp = new DateTime("2020-01-07T19:51:51.956Z").withZone(DateTimeZone.UTC),
        toAddress = "Alice",
        amount = 50.0
      ),
      Transaction(
        timestamp = new DateTime("2020-01-07T19:51:51.992Z").withZone(DateTimeZone.UTC),
        toAddress = "Bob",
        amount = 12.5
      )
    )
  )
}
