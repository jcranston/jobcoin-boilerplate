package com.gemini.jobcoin

import akka.actor.Actor
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits._
import scala.collection._
import scala.util.Success
import scala.util.Failure
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import scala.collection.concurrent.TrieMap

class HouseAddress extends Actor {
  import HouseAddress.houseAddressName

  // create Jobcoin client
  implicit val materializer = ActorMaterializer()
  private val config = ConfigFactory.load()
  private val jobcoinClient = new JobcoinClient(config)

  val transferPeriodToAddresses = 15 // in seconds

  private var addressesToAmounts: concurrent.TrieMap[String, Double] =
    new TrieMap[String, Double]

  private val ex = new ScheduledThreadPoolExecutor(1)

  // every N seconds, looks at the pooled Jobcoin amounts
  // and sends to the recipient Addresses
  private val jobcoinTransferTask = new Runnable {
    override def run() = {
      addressesToAmounts
        .foreach {
          case (recipientAddress: String, amount: Double) => {
            println(s"[$houseAddressName] sending [$amount] Jobcoin to recipient address [$recipientAddress]")
            jobcoinClient
              .sendJobcoins(
                fromAddress = houseAddressName,
                toAddress = recipientAddress,
                amount = amount
              )
              .onComplete {
                case Success(_) => {
                  println(s"[$houseAddressName] removing information for [$recipientAddress]")
                  addressesToAmounts.remove(recipientAddress)
                }
                case Failure(e: Throwable) => {
                  throw new RuntimeException(s"failed to send Jobcoin to $recipientAddress", e)
                }
              }
            // simulate 1 second pause to make this seem more "realistic"
            // there could be a race condition between the 50ms sleep here and the 15s period
            // but for simulation purposes this should be appropriate
            // to make this scalable we would need to resolve this periodicity problem
            Thread.sleep(50)
          }
        }
    }
  }
  ex.scheduleAtFixedRate(jobcoinTransferTask, 1, 30, TimeUnit.SECONDS)

  override def receive: Receive = {
    case houseAddressTransfer: HouseAddressTransfer => {
      val receiverAddress: String = houseAddressTransfer.receiverAddressName
      println(s"[$houseAddressName] received an incoming transfer from [${houseAddressTransfer.depositAddressName}]")

      addressesToAmounts.get(receiverAddress) match {
        case Some(amount) =>
          val updatedAmount = amount + houseAddressTransfer.amount
          println(s"[$houseAddressName] increasing Jobcoin amount for [$receiverAddress] from [${amount}] to [$updatedAmount]")
          addressesToAmounts.update(receiverAddress, updatedAmount)
        case _ =>
          val amount = houseAddressTransfer.amount
          println(s"[$houseAddressName] creating Jobcoin amount for [$receiverAddress] with value [$amount]")
          addressesToAmounts.update(receiverAddress, amount)
      }
    }
    case _ => {
      System.out.println(s"[$houseAddressName] cannot recognize message")
    }
  }
}

object HouseAddress {
  val houseAddressName = "HouseAddress"
}

case class HouseAddressTransfer(
  depositAddressName: String,
  receiverAddressName: String,
  amount: Double
)