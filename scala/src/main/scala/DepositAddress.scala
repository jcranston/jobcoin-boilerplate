package com.gemini.jobcoin

import akka.actor.Actor
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure, Success}

class DepositAddress extends Actor {
  implicit val materializer = ActorMaterializer()
  val config = ConfigFactory.load()
  val jobcoinClient = new JobcoinClient(config)

  override def receive: Receive = {
    case depositAddressTransfer: DepositAddressTransfer => {
      val depositAddressName = depositAddressTransfer.depositAddressName
      println(s"[$depositAddressName] received a new DepositAddressTransfer")

      depositAddressTransfer
        .recipientAddressesAndAmounts
        .map { addressAndAmount: AddressAndAmount =>
          sendToHouseAddress(addressAndAmount, depositAddressName)
        }
    }
    case _ => {
      System.out.println(s"deposit address cannot recognize message")
    }
  }

  private def sendToHouseAddress(
    recipientAddressAndAmount: AddressAndAmount,
    depositAddressName: String
  ): Unit = {
    val receiverAddressName = recipientAddressAndAmount.address.name
    val amount = recipientAddressAndAmount.amountToSend
    val houseAddressTransfer = HouseAddressTransfer(
      depositAddressName = depositAddressName,
      receiverAddressName = receiverAddressName,
      amount = amount
    )

    Thread.sleep(2000) // simulate 2 second delay
    val houseAddressName = HouseAddress.houseAddressName

    println(s"[${depositAddressName}] transfering [$amount] to pooled [$houseAddressName]")
    jobcoinClient
      .sendJobcoins(
        fromAddress = depositAddressName,
        toAddress = houseAddressName,
        amount = amount
      )
      .onComplete {
        case Success(_) => {
          println(s"[$depositAddressName] successfully transferred [$amount] to pooled [$houseAddressName]")
          JobcoinMixer.houseAddressActor ! houseAddressTransfer
        }
        case Failure(e) => {
          throw new RuntimeException(s"[$depositAddressName] failed to transfer [$amount] to pooled [$houseAddressName]", e)
        }
      }
  }
}

case class DepositAddressTransfer(
  depositAddressName: String,
  recipientAddressesAndAmounts: Seq[AddressAndAmount]
)
