package com.gemini.jobcoin

import java.util.UUID

import scala.io.StdIn
import com.typesafe.config.ConfigFactory
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Success, Try}

object JobcoinMixer {
  // Create an actor system
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  object CompletedException extends Exception

  def main(args: Array[String]): Unit = {


    // Load Config
    val config = ConfigFactory.load()

    // Test HTTP client
     val client = new JobcoinClient(config)

    // create deposit addresses
    val numDepositAddresses: Int = config.getInt("jobcoin.numDepositAddresses")
    val depositAddressActors: immutable.Seq[ActorRef] = {
      (1 to numDepositAddresses).map { addressNumber: Int =>
        actorSystem.actorOf(Props[DepositAddress], name = s"DepositAddress$addressNumber")
      }
    }

    try {
      while (true) {
        println(prompt)
        val line = StdIn.readLine()

        if (line == "quit") throw CompletedException
        
        val addresses = line.split(",")
        if (line == "") {
          println(s"You must specify empty addresses to mix into!\n$helpText")
        } else {
          processTransaction(addresses, depositAddressActors, client)
          val depositAddress = UUID.randomUUID()
          println(s"You may now send Jobcoins to address $depositAddress. They will be mixed and sent to your destination addresses.")
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
    depositAddressActors: Seq[ActorRef],
    jobcoinClient: JobcoinClient
  ): Unit = {
    val transactionRequestTry: Try[TransactionRequest] = TransactionRequest.parse(line, jobcoinClient)
    transactionRequestTry match {
      case Success(transactionRequest) =>
        if (transactionRequest.fromAddress.balance < transactionRequest.requestedSend) {
          println(s"Sender address ${transactionRequest.fromAddress.name} does not have enough Jobcoin to send")
        } else {
          val depositAddressNum: Int = transactionRequest.getDepositAddressNumber()
          val depositAddressActor = depositAddressActors(depositAddressNum)
          depositAddressActor ! transactionRequestTry
        }
      case _ =>
        println(s"Unable to parse the transaction given input: $line\n\n$helpText") // todo update the help message
    }
  }

  val prompt: String = "Please enter a comma-separated list of new, unused Jobcoin addresses where your mixed Jobcoins will be sent."
  val helpText: String =
    """
      |Jobcoin Mixer
      |
      |Takes in at least one return address as parameters (where to send coins after mixing). Returns a deposit address to send coins to.
      |
      |Usage:
      |    run return_addresses...
    """.stripMargin
}
