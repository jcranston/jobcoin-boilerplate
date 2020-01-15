package com.gemini.jobcoin

import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.gemini.jobcoin.JobcoinClient.{Address, Transaction}
import com.typesafe.config.ConfigFactory
import org.joda.time.{DateTime, DateTimeZone}

import scala.util.{Failure, Success}

class TransactionRequestSpec extends FlatSpec with Matchers {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  private val config = ConfigFactory.load()
  private val jobcoinClient = new JobcoinClient(config)

  "Transaction request parser" should
    "correctly parse for valid addresses" in {
      val inputLine = Seq("Sender1", "50.0", "Recipient1", "50.0", "Recipient2", "50.0")
      TransactionRequest.parse(inputLine, jobcoinClient) match {
        case Failure(_) => fail() // should be well-formed
        case Success(transactionRequest) => {
          transactionRequest.fromAddress should be(senderAddress)
          transactionRequest.requestedSend should be(50.0)
          transactionRequest.toAddresses should contain(AddressAndAmount(recipient1Address, 50.0))
          transactionRequest.toAddresses should contain(AddressAndAmount(recipient2Address, 50.0))
        }
      }
  }


  "Transaction request parser" should
    "return an error for badly formed line" in {
      val inputLine = Seq("SenderDoesNotExist", "30.0", "Recipient1", "15.0", "Recipient2", "7.5")
      TransactionRequest.parse(inputLine, jobcoinClient) match {
        case Failure(_) => // pass
        case _ => fail()
      }
      val retVal = TransactionRequest.parse(inputLine, jobcoinClient)
      println(retVal)
    }


  private val senderAddress = Address(
    name = "Sender1",
    balance = 50.0,
    transactions = Seq[Transaction](
      Transaction(
        timestamp = new DateTime("2020-01-15T00:30:03.379Z").withZone(DateTimeZone.UTC),
        toAddress = "Sender1",
        fromAddress = None,
        amount = 50.0
      )
    )
  )

  private val recipient1Address = Address(
    name = "Recipient1",
    balance = 50.0,
    transactions = Seq[Transaction](
      Transaction(
        timestamp = new DateTime("2020-01-15T00:30:07.231Z").withZone(DateTimeZone.UTC),
        toAddress = "Recipient1",
        fromAddress = None,
        amount = 50.0
      )
    )
  )

  private val recipient2Address = Address(
    name = "Recipient2",
    balance = 50.0,
    transactions = Seq[Transaction](
      Transaction(
        timestamp = new DateTime("2020-01-15T00:30:11.067Z").withZone(DateTimeZone.UTC),
        toAddress = "Recipient2",
        fromAddress = None,
        amount = 50.0
      )
    )
  )
}
