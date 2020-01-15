package com.gemini.jobcoin

import akka.actor.{Actor, ActorRef, Props}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure, Success}

class DepositAddress extends Actor {
  implicit val materializer = ActorMaterializer()
  val config = ConfigFactory.load()
  val jobcoinClient = new JobcoinClient(config)

  // create HouseAddress actor
  val houseAddressActor: ActorRef = {
    JobcoinMixer.actorSystem.actorOf(Props[HouseAddress], name = s"HouseAddress")
  }

  override def receive: Receive = {
    case transactionRequest: TransactionRequest => {
      val depositAddressNumber = transactionRequest.getDepositAddressNumber()
      val depositAddressName = s"DepositAddress${depositAddressNumber}"
      jobcoinClient
        .sendJobcoins(
          fromAddress = transactionRequest.fromAddress.name,
          toAddress = s"DepositAddress${depositAddressNumber + 1}",
          amount = transactionRequest.requestedSend
        )
        .onComplete {
          // when the transfer succeeds, move funds to the house address
          case Success(_) =>
            transactionRequest
              .toAddresses
              .map { addressAndAmount: AddressAndAmount =>
                val houseAddressTransfer = HouseAddressTransfer(
                  depositAddressName = depositAddressName,
                  receiverAddressName = addressAndAmount.address.name,
                  amount = addressAndAmount.amountToSend
                )
                houseAddressActor ! houseAddressTransfer
              }
          case _ =>
            throw new RuntimeException("unable to transfer Jobcoin to the deposit address")
        }
    }
    case _ => {
      System.out.println(s"deposit address cannot recognize message")
    }
  }
}

object DepositAddress {
  private val config = ConfigFactory.load()
  private val numDepositAddresses: Int = config.getInt("jobcoin.numDepositAddresses")

  def computeDepositAddress(addresses: Seq[String]): Int = {
    val sorted = addresses.sortWith(_ < _)
    val hashCode = sorted.hashCode() // todo does this respect unique ordering?
    hashCode % (numDepositAddresses)
  }
}

case class DepositAddressInstruction(

)
