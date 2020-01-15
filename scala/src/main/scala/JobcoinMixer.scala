package com.gemini.jobcoin

import scala.io.StdIn
import com.typesafe.config.ConfigFactory
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure, Success, Try}

object JobcoinMixer {
  // Create an actor system
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  // Load Config
  val config = ConfigFactory.load()

  // create DepositAddress actors
  val numDepositAddresses: Int = config.getInt("jobcoin.numDepositAddresses")
  val depositAddressActors: immutable.Seq[ActorRef] = {
    (1 to numDepositAddresses).map { addressNumber: Int =>
      actorSystem.actorOf(Props[DepositAddress], name = s"DepositAddress$addressNumber")
    }
  }

  // create HouseAddress actor
  val houseAddressActor: ActorRef = {
    JobcoinMixer.actorSystem.actorOf(Props[HouseAddress], name = s"HouseAddress")
  }

  object CompletedException extends Exception

  def main(args: Array[String]): Unit = {
    val client = new JobcoinClient(config)

    try {
      while (true) {
        println(prompt)
        val line = StdIn.readLine()

        if (line == "quit") throw CompletedException
        
        val addresses = line.split(",")
        if (line == "") {
          println(s"You must specify empty addresses to mix into!\n$helpText")
        } else {
          processTransaction(addresses, client)
        }
      }
    } catch {
      case CompletedException => println("Quitting...")
    } finally {
      actorSystem.terminate()
    }
  }

  /**
   * Takes the array of strings from the command line and computes a TransactionRequest
   * after hydrating the addresses from the API. Upon successful creation of a TransactionRequest,
   * sends the corresponding transaction to the appropriate DepositAddress.
   */
  private def processTransaction(
    line: Seq[String],
    jobcoinClient: JobcoinClient
  ): Unit = {
    val transactionRequestTry: Try[TransactionRequest] = TransactionRequest.parse(line, jobcoinClient)
    transactionRequestTry match {
      case Success(transactionRequest) =>
        if (transactionRequest.fromAddress.balance < transactionRequest.requestedSend) {
          println(s"Sender address ${transactionRequest.fromAddress.name} does not have enough Jobcoin to send")
        } else {
          transferToDepositAddress(transactionRequest, jobcoinClient)
        }
      case _ =>
        println(s"Unable to parse the transaction given input: $line\n\n$helpText")
    }
  }

  private def transferToDepositAddress(
    transactionRequest: TransactionRequest,
    jobcoinClient: JobcoinClient
  ): Unit = {
    val fromAddressName = transactionRequest.fromAddress.name
    val amount = transactionRequest.requestedSend
    val depositAddressNumber = transactionRequest.getDepositAddressNumber()
    val depositAddressName = s"DepositAddress${depositAddressNumber + 1}"
    println(s"$fromAddressName can now send Jobcoin to deposit address [$depositAddressName]")
    println(s"[$fromAddressName] now sending mixed Jobcoin amount of [$amount] to [$depositAddressName]")

    jobcoinClient
      .sendJobcoins(
        fromAddress = fromAddressName,
        toAddress = depositAddressName,
        amount = amount
      )
      .onComplete {
        case Success(_) => {
          println(s"[$fromAddressName] successfully transferred [$amount] Jobcoin to [$depositAddressName]")
          val depositAddressTransfer = DepositAddressTransfer(
            depositAddressName = depositAddressName,
            recipientAddressesAndAmounts = transactionRequest.toAddresses
          )

          val depositAddressActor = depositAddressActors(depositAddressNumber)
          depositAddressActor ! depositAddressTransfer
        }

        case Failure(e) => {
          throw new RuntimeException(s"[$fromAddressName] failed to transfer [$amount] Jobcoin to [$depositAddressName]")
        }
      }
  }

  val prompt: String = "Enter comma-separated list of form: <SenderAddress>, <SenderAmount>, <RecipientAddress1>" +
    ", <RecipientAmount1>, <RecipientAddress2>, <RecipientAmount2>"
  val helpText: String =
    """
      |Jobcoin Mixer
      |
      |Takes in a sender address and amount, and recipient addresses and corresponding amounts. Will mix the recipients'
      |Jobcoin totals together and send to a Deposit Address, and then get sent to a pooled House Address which will
      |later send Jobcoin to the recipient addresses.
      |
    """.stripMargin
}
