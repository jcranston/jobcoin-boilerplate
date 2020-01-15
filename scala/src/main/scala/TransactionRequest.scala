package com.gemini.jobcoin

import com.gemini.jobcoin.JobcoinClient.Address

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

case class TransactionRequest(
  fromAddress: Address,
  requestedSend: Double,
  toAddresses: Seq[AddressAndAmount]
) {

  /**
   * Returns a deposit address in the (inclusive) range [0,...,numDepositAddresses - 1]
   */
  def getDepositAddressNumber(): Int = {
    import com.typesafe.config.ConfigFactory
    val config = ConfigFactory.load()
    val numDepositAddresses: Int = config.getInt("jobcoin.numDepositAddresses")

    // sort the address names lexicographically, concatenate them
    // and then compute hash code % num deposit addresses to get the
    // name of the deposit address to send to
    val hashCode = toAddresses
      .map(_.address.name)
      .sortWith(_ < _)
      .mkString("")
      .hashCode

    hashCode % numDepositAddresses
  }
}

object TransactionRequest {

  /**
   * Expects an input Seq[String] of the form:
   * senderAddress, recipient1Address, recipient1Amount, recipient2Address, recipient2Amount, ...
   *
   * In the interest of time, I was not able to code all edge cases of bad formed input.
   * This throw RuntimeExceptions if the input is badly formed.
   *
   * The first element in sequence will be the sender address.
   * The second element in the sequence will be the amount the sender will send.
   * The next two elements (third and fourth), will be the first recipient's
   * address and the amount to send to the first recipient.
   * If there is more than one recipient, the next two elements (fifth and sixth)
   * will be the second recipient's address and the amount to send to the second
   * recipient, etc.
   */
  def parse(inputLine: Seq[String], jobcoinClient: JobcoinClient): Try[TransactionRequest] = {
    // sender should be in first position
    val senderAddressFuture: Future[Try[Address]] = jobcoinClient.getAddress(inputLine(0))
    val senderAmount: Double = inputLine(1).toDouble

    val recipientAddressesFuture: Future[List[Try[Address]]] =
      Future.sequence(
        inputLine
          .drop(2) // ignore the sender address and sender amount
          .grouped(2).map(_.head).toList // extract just the addresses, ignore the amounts
          .map { recipientAddress =>
            jobcoinClient.getAddress(recipientAddress)
          }
      )
    val recipientAmounts: Seq[Double] =
      inputLine
        .drop(3) // amounts for each recipient begin at 4th index
        .grouped(2).map(_.head).toList
        .map(_.toDouble)

    val futureResult = for {
      senderAddressTry: Try[Address] <- senderAddressFuture
      recipientAddressesTry: Seq[Try[Address]] <- recipientAddressesFuture
    } yield {
      senderAddressTry match {
        case Failure(e) => {
          Failure[TransactionRequest](e)
        }
        case Success(senderAddress) => {
          val tryOfAddresses: Try[Seq[Address]] =
            Try(
              recipientAddressesTry.collect {
                case Success(address) => address
                case Failure(e) => throw new Throwable("unable to parse address", e)
              }
            )
          tryOfAddresses match {
            case Failure(e) => Failure[TransactionRequest](e)
            // all futures resolved properly if we reach this point
            case Success(addresses) => {
              val addressesAndAmounts: Seq[AddressAndAmount] =
                addresses
                  .zip(recipientAmounts)
                  .map {
                    case (address: Address, amount: Double) => AddressAndAmount(address, amount)
                  }
              Success(
                TransactionRequest(
                  fromAddress = senderAddress,
                  requestedSend = senderAmount,
                  toAddresses = addressesAndAmounts
                )
              )
            }
          }
        }
      }
    }

    Await.result(futureResult, 5.seconds)
  }
}

case class AddressAndAmount(
  address: Address,
  amountToSend: Double
)