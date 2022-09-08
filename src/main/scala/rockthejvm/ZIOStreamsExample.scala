package rockthejvm

import zio.*
import zio.json.*
import zio.stream.*

import java.nio.charset.CharacterCodingException
import scala.util.matching.Regex

object ZIOStreamsExample extends ZIOAppDefault:

  val post1: String              = "hello-world.md"
  val post1_content: Array[Byte] =
    """---
       |title: Hello World
       |tags: []
       |---
       |=====
       |
       |## Generic Heading
       |
       |Even pretend blog posts need a #generic intro.
       |""".stripMargin.getBytes

  val post2: String              = "scala-3-extensions.md"
  val post2_content: Array[Byte] =
    """---
       |title: "Scala 3 for You and Me"
       |tags: []
       |---
       |=====
       |
       |## Scala 3 Extensions
       |
       |Scala 3 has a number of new features that are not yet available in Scala 2.
       |
       |### ZIO
       |
       |ZIO is a new typeclass for effectful programming in #Scala.
       |
       |### ZStream
       |
       |ZStream is a new typeclass for effectful streaming in #Scala.
       |
       |### ZIO JSON
       |
       |ZIO JSON is a new typeclass for effectful #JSON parsing in #Scala.
       |""".stripMargin.getBytes

  val post3: String              = "zio-streams.md"
  val post3_content: Array[Byte] =
    """---
       |title: "ZIO Streams: An Introduction"
       |tags: []
       |---
       |=====
       |
       |## ZIO Streams
       |
       |ZIO Streams is a new typeclass for effectful streaming in #Scala and #ZIO #ZStreams.
       |
       |### ZStream
       |
       |ZStream is a new typeclass for effectful streaming in #Scala.
       |
       |### ZIO JSON
       |
       |ZIO JSON is a new typeclass for effectful #JSON parsing in #Scala.
       |""".stripMargin.getBytes

  val fileMap: Map[String, Array[Byte]] =
    Map(
      post1 -> post1_content,
      post2 -> post2_content,
      post3 -> post3_content,
    )

  val hashFilter: String => Boolean =
    s => s.startsWith("#") && s.count(_ == '#') == 1 && s.length > 2

  val punctuationRegex: Regex = """\p{Punct}""".r

  val parseHashPipeline: ZPipeline[Any, Nothing, String, String] =
    ZPipeline.filter(hashFilter)

  val removePunctuationPipeline: ZPipeline[Any, Nothing, String, String] =
    ZPipeline.map(s => punctuationRegex.replaceAllIn(s, ""))

  val lowercasePipeline: ZPipeline[Any, Nothing, String, String] =
    ZPipeline.map(_.toLowerCase)

  val collectTagsPipeline: ZPipeline[Any, CharacterCodingException, Byte, String] =
    ZPipeline.utf8Decode >>>
      ZPipeline.splitLines >>>
      ZPipeline.splitOn(" ") >>>
      parseHashPipeline >>>
      removePunctuationPipeline >>>
      lowercasePipeline

  val addTagsPipeline: Set[String] => ZPipeline[Any, Nothing, String, String] =
    tags => ZPipeline.map(_.replace("tags []", s"tags [${tags.mkString(", ")}]"))

  val addLinksPipeline: ZPipeline[Any, Nothing, String, String] =
    ZPipeline.map {
      _.split(" ")
        .map { word =>
          if hashFilter(word) then
            s"[${word}](/tags/${punctuationRegex.replaceAllIn(word.toLowerCase(), "")})"
          else word
        }
        .mkString(" ")
    }

  val addNewlinePipeline: ZPipeline[Any, Nothing, String, String] =
    ZPipeline.map(_ + "\n")

  val regeneratePostPipeline: Set[String] => ZPipeline[Any, CharacterCodingException, Byte, Byte] =
    tags =>
      ZPipeline.utfDecode >>>
        ZPipeline.splitLines >>>
        addTagsPipeline(tags) >>>
        addLinksPipeline >>>
        addNewlinePipeline >>>
        ZPipeline.utf8Encode

  def writeFileSink(dir: String, filename: String): ZSink[Any, Throwable, Byte, Byte, Long] =
    ZSink.fromFileName(dir + "/" + filename)

  val collectTagsSink: ZSink[Any, Nothing, String, Nothing, Set[String]] =
    ZSink.collectAllToSet

  def autoTag(filename: String, contents: Array[Byte]): Task[(String, Set[String])] =
    for
      tags <- ZStream
                .fromIterable(contents)
                .via(collectTagsPipeline)
                .run(collectTagsSink)
      _    <- Console.printLine(s"generating file: $filename")
      _    <- ZStream
                .fromIterable(contents)
                .via(regeneratePostPipeline(tags))
                .run(writeFileSink("src/main/resources/data/zio-streams", filename))
    yield (filename, tags)

  val autoTagAll: Task[Map[String, Set[String]]] =
    ZIO.foreach(fileMap) { case (filename, contents) =>
      autoTag(filename, contents)
    }

  // Map[filename, all tags in that file] => Map[tag, all files with that tag]
  def createTagIndexFile(tagMap: Map[String, Set[String]]) =
    val searchMap = tagMap.values.toSet.flatten
      .map(tag => tag -> tagMap.filter(_._2.contains(tag)).keys.toSet)
      .toMap

    ZStream
      .fromIterable(searchMap.toJsonPretty.getBytes)
      .run(ZSink.fromFileName("src/main/resources/data/zio-streams/search.json"))

  val parseProgram =
    for
      tagMap <- autoTagAll
      _      <- Console.printLine(s"generating tag index")
      _      <- createTagIndexFile(tagMap)
    yield ()

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = parseProgram
