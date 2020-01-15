package com.gemini.jobcoin

import java.lang.Throwable
import java.util.concurrent.atomic.AtomicLong

import akka.actor.Actor
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits._
import scala.collection._
import java.util.concurrent.{ConcurrentHashMap, ScheduledThreadPoolExecutor, TimeUnit}

import scala.collection.concurrent.TrieMap
import scala.collection.convert.decorateAsScala._
import scala.util.{Failure, Success}

class HouseAddress extends Actor {
  // create Jobcoin client
  implicit val materializer = ActorMaterializer()
  private val config = ConfigFactory.load()
  private val jobcoinClient = new JobcoinClient(config)

  val houseAddressName = "HouseAddress"
  val transferPeriodToAddresses = 30 // 30 seconds

  private val addressesToAmounts: concurrent.TrieMap[String, Double] =
    new TrieMap[String, Double]

  private val ex = new ScheduledThreadPoolExecutor(1)
  private val jobcoinTransferTask = new Runnable {
    override def run() = {
      addressesToAmounts
        .foreach {
          case (recipientAddress: String, amount: Double) => {
            jobcoinClient
              .sendJobcoins(
                fromAddress = houseAddressName,
                toAddress = recipientAddress,
                amount = amount
              )
              .recover {
                case e: Throwable =>
                  throw new RuntimeException(s"failed to send Jobcoin to $recipientAddress", e)
              }
            // simulate 2 second pause to make this seem more "realistic"
            Thread.sleep(2000)
          }
        }
    }
  }
  private val f = ex.scheduleAtFixedRate(jobcoinTransferTask, 1, 30, TimeUnit.SECONDS)

  override def receive: Receive = {
    case houseAddressTransfer: HouseAddressTransfer => {
      val receiverAddress: String = houseAddressTransfer.receiverAddressName
      addressesToAmounts.get(receiverAddress) match {

        case Some(amount) =>
          addressesToAmounts.update(receiverAddress, amount + houseAddressTransfer.amount)
        case _ =>
          addressesToAmounts.update()
      }
    }
    case _ => {
      System.out.println(s"house address cannot recognize message")
    }
  }
}

case class HouseAddressTransfer(
  depositAddressName: String,
  receiverAddressName: String,
  amount: Double
)