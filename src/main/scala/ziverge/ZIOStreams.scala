package ziverge

import zio.*
import zio.stream.*
import java.io.IOException

object ZIOStreams extends ZIOAppDefault:

  val runStream =
    ZStream.repeatZIO(Random.nextIntBetween(3, 100)).take(100) >>>
      ZPipeline.filter[Int](_ % 3 == 0) >>>
      ZSink.collectAll

  override def run = runStream.debug("Got: ")
