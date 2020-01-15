package com.gemini.jobcoin

import play.api.libs.ws._
import play.api.libs.ws.ahc._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import com.typesafe.config.Config
import akka.stream.Materializer

import scala.async.Async._
import scala.concurrent.Future
import DefaultBodyReadables._

import scala.concurrent.ExecutionContext.Implicits._
import JobcoinClient.{Address, AddressWithoutName}
import org.joda.time.DateTime
import play.libs.ws.StandaloneWSResponse

import scala.util.{Failure, Success, Try}

class JobcoinClient(config: Config)(implicit materializer: Materializer) {
  private val wsClient = StandaloneAhcWSClient()
  private val apiAddressesUrl = config.getString("jobcoin.apiAddressesUrl")
  private val apiTransactionsUrl = config.getString("jobcoin.apiTransactionsUrl")
  
  // Docs:
  // https://github.com/playframework/play-ws
  // https://www.playframework.com/documentation/2.6.x/ScalaJsonCombinators
  def sendJobcoins(fromAddress: String, toAddress: String, amount: Double): Future[Unit] = {
    val data = Json.obj(
      "fromAddress" -> fromAddress,
      "toAddress" -> toAddress,
      "amount" -> amount.toString()
    )

    wsClient
      .url(apiTransactionsUrl)
      .post(data)
      .flatMap { response: StandaloneWSRequest#Response =>
        response.status match {
          case 200 => Future.unit
          case _ => Future.failed[Unit](new RuntimeException("cannot send Jobcoins"))
        }
      }
  }

  def getAddress(addressName: String): Future[Try[Address]] = {
    wsClient
      .url(apiAddressesUrl + "/" + addressName)
      .get
      .map { response: StandaloneWSRequest#Response =>
        if (response.status != 200) {
          throw new RuntimeException(s"error getting address for $addressName: ${response.status}")
        } else {
          val address = response.body[JsValue]
            .validate[AddressWithoutName]
            .map(Address(addressName, _))
            .get
          if (address.balance.equals(0.0) && address.transactions.length == 0) {
            throw new RuntimeException(s"address $addressName does not exist")
          } else {
            Success(address)
          }
        }
      }
      .recover {
        case e: Throwable => {
          Failure(e)
        }
      }
  }
}

object JobcoinClient {
  case class AddressWithoutName(balance: Double, transactions: Seq[Transaction])

  object AddressWithoutName {
    implicit val addressReads: Reads[AddressWithoutName] = (
      (JsPath \ "balance").read[String].map(_.toDouble) and
      (JsPath \ "transactions").read[Seq[Transaction]]
    )(AddressWithoutName.apply _)
  }

  case class Address(name: String, balance: Double, transactions: Seq[Transaction])

  object Address {
    def apply(name: String, addressWithoutName: AddressWithoutName): Address = {
      Address(
        name = name,
        balance = addressWithoutName.balance,
        transactions = addressWithoutName.transactions
      )
    }
  }

  case class Transaction(timestamp: DateTime, toAddress: String, fromAddress: Option[String], amount: Double)

  object Transaction {
    implicit val transactionReads: Reads[Transaction] = (
      (JsPath \ "timestamp").read[String].map(parseDateTime(_)) and
      (JsPath \ "toAddress").read[String] and
      (JsPath \ "fromAddress").readNullable[String] and
      (JsPath \ "amount").read[String].map(_.toDouble)
    ).apply(Transaction.apply _)
    private def parseDateTime(s: String): DateTime = {
      Try(DateTime.parse(s)).toOption match {
        case Some(d) => d
        case _ => new DateTime
      }
    }
  }
}
