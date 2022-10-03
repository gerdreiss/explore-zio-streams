package rockthejvm

import zio.*
import zio.json.*
import zio.stream.*
import java.io.InputStream
import java.io.IOException

object ZIOStreams extends ZIOAppDefault:

  // ZStream
  val numbers: ZStream[Any, Nothing, Int]    = ZStream.fromIterable(1 to 10)
  val strings: ZStream[Any, Nothing, String] = numbers.map(_.toString).map(_ * 3)

  // Sink = destination of the elements in the stream
  val sum: ZSink[Any, Nothing, Int, Nothing, Int]          = ZSink.sum[Int]
  val take5i: ZSink[Any, Nothing, Int, Int, Chunk[Int]]    = ZSink.take(5)
  val take5s: ZSink[Any, Nothing, Int, Int, Chunk[String]] = take5i.map(_.map(_.toString))

  // leftovers
  val take5leftovers: ZSink[Any, Nothing, Int, Int, (Chunk[String], Chunk[Int])] =
    take5s.collectLeftover //                        ^^ output      ^^ leftovers

  val take5ignore: ZSink[Any, Nothing, Int, Nothing, Chunk[String]] =
    take5s.ignoreLeftover

  // contramap
  val take5contra: ZSink[Any, Nothing, String, Int, Chunk[Int]] =
    take5i.contramap(_.toInt)

  // ZStream[String]          -> ZSink[Int].contramap(...)
  // ZStream[String].map(...) -> ZSink

  val summed: ZIO[Any, Nothing, Int] = numbers.run(sum)

  // ZPipeline
  val businessLogic: ZPipeline[Any, Nothing, String, Int] = ZPipeline.map(_.toInt)

  val zio2: ZIO[Any, Nothing, Int] = strings.via(businessLogic).run(sum)

  // many pipelines
  val filterLogic: ZPipeline[Any, Nothing, Int, Int] = ZPipeline.filter(_ % 4 == 0)

  val zio3: ZIO[Any, Nothing, Int] = strings.via(businessLogic).via(filterLogic).run(sum)

  val appLogic: ZPipeline[Any, Nothing, String, Int] = businessLogic >>> filterLogic

  val zio4: ZIO[Any, Nothing, Int] = strings.via(appLogic).run(sum)

  /** 
   * Failures/Errors
   */

  val failStream: ZStream[Any, String, Int] =
    ZStream.fromIterable(1 to 10) ++ ZStream.fail("boom") ++ ZStream.fromIterable(11 to 100)

  class FakeInputStream[T <: Throwable](failAt: Int, failWith: => T) extends InputStream:
    private val data: Array[Byte] = util.Random.nextString(8192).getBytes
    private var index             = 0

    override def read(): Int =
      if index == failAt then throw failWith
      else
        data
          .lift(index)
          .fold(-1) { byte =>
            index += 1
            byte
          }

  val failingStream: ZStream[Any, IOException, String] =
    ZStream
      .fromInputStream(new FakeInputStream(9, new IOException("boom")), chunkSize = 1)
      .map(_.toChar.toString)

  val sink: ZSink[Any, Nothing, String, Nothing, String] =
    ZSink.collectAll[String].map(_.mkString("-"))

  val defectStream: ZStream[Any, Nothing, Int] =
    ZStream.fromIterable(1 to 10) ++ ZStream.dieMessage("boom") ++ ZStream.fromIterable(11 to 100)

  // recovery
  val recoveryStream: ZStream[Any, Throwable, String] =
    ZStream("A", "B", "C", "D", "E", "F", "G", "H", "I")

  val recoveredStream = failingStream <> recoveryStream >>> sink

  // catch
  val caughtStream = failingStream.catchAll(_ => recoveryStream) >>> sink

  val caughtSomeStream = failingStream.catchSome { case _: IOException => recoveryStream } >>> sink

  val errorContained = failingStream.either >>> ZSink.collectAll

  override def run = errorContained.debug.unit
