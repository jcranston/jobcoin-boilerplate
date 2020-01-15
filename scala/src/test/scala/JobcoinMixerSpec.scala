package com.gemini.jobcoin

import org.scalatest._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

class MixerTests extends FlatSpec with Matchers {
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "Main method" should "print the help text when no args are given" in {
    val inputText = "\nquit\n"
    val inputStream = new ByteArrayInputStream(inputText.getBytes(StandardCharsets.UTF_8))
    val outCapture = new ByteArrayOutputStream
    Console.withIn(inputStream) {
      Console.withOut(outCapture) {
        JobcoinMixer.main(Array[String]())
      }
    }

    val expectedOutput = s"""${JobcoinMixer.prompt}
    |You must specify empty addresses to mix into!
    |${JobcoinMixer.helpText}
    |${JobcoinMixer.prompt}
    |Quitting...
    |""".stripMargin

    outCapture.toString should be (expectedOutput)
  }
}
