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
import JobcoinClient.{Address, PlaceholderResponse}
import org.joda.time.DateTime

import scala.util.Try

class JobcoinClient(config: Config)(implicit materializer: Materializer) {
  private val wsClient = StandaloneAhcWSClient()
  private val apiAddressesUrl = config.getString("jobcoin.apiAddressesUrl")
  
  // Docs:
  // https://github.com/playframework/play-ws
  // https://www.playframework.com/documentation/2.6.x/ScalaJsonCombinators
  def testGet(): Future[PlaceholderResponse] = {
//    val response = await {
//      wsClient
//        .url("https://jsonplaceholder.typicode.com/posts/1")
//        .get()
//    }
//
//    response
//      .body[JsValue]
//      .validate[PlaceholderResponse]
//      .get

    wsClient
      .url("https://jsonplaceholder.typicode.com/posts/1")
      .get()
      .map { response =>
        response.body[JsValue]
          .validate[PlaceholderResponse]
          .get
      }
  }

  def testGetAddress(address: String): Future[Address] = {
    wsClient
      .url(apiAddressesUrl + "/" + address)
      .get
      .map { response =>
        System.out.println(response)
        response.body[JsValue]
          .validate[Address]
          .get
      }
  }
}

object JobcoinClient {
  case class PlaceholderResponse(userId: Int, id: Int, title: String, body: String)
  object PlaceholderResponse {
    implicit val jsonReads: Reads[PlaceholderResponse] = Json.reads[PlaceholderResponse]
  }
  case class Address(balance: Double, transactions: Seq[Transaction])
  object Address {
    implicit val addressReads: Reads[Address] = (
      (JsPath \ "balance").read[String].map(_.toDouble) and
      (JsPath \ "transactions").read[Seq[Transaction]]
    )(Address.apply _)
  }
  case class Transaction(timestamp: DateTime, toAddress: String, amount: Double)
  object Transaction {
    implicit val transactionReads: Reads[Transaction] = (
      (JsPath \ "timestamp").read[String].map(parseDateTime(_)) and
      (JsPath \ "toAddress").read[String] and
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
