package ziverge

import zio.*
import zio.stream.*
import java.io.IOException

object ZIOStreams extends ZIOAppDefault:

  val randomNumbers = ZStream.repeatZIO(Random.nextIntBetween(3, 100))

  val filterer = ZPipeline.filter[Int](_ % 3 == 0)
  val doubler  = ZPipeline.map[Int, Int](_ * 2)

  def collector(n: Int) = ZSink.collectAllN[Int](n)

  val runStream = randomNumbers >>> filterer >>> doubler >>> collector(10)

  val runStreams = randomNumbers
    .broadcast(2, 16)
    .flatMap { streams =>
      val subscriber1 = streams(0) >>> doubler >>> filterer >>> collector(10)
      val subscriber2 = streams(1) >>> doubler >>> filterer >>> collector(5)
      subscriber1 <&> subscriber2
    }

  val runDynamicStreams =
    for
      shared <- randomNumbers.broadcastDynamic(16)
      c1     <- shared >>> doubler >>> filterer >>> collector(10)
      c2     <- shared >>> doubler >>> filterer >>> collector(5)
    yield (c1, c2)

  override def run = runDynamicStreams.debug("Got: ")
