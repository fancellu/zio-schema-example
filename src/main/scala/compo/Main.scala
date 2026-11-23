package compo

import java.nio.file.{Files, Path}

import zio._
import zio.schema.codec.BinaryCodec
import zio.schema.codec.xml.XmlCodec

/** Decodes the whole document in memory with the schema-derived `XmlCodec`. */
object Main extends CompoApp {

  private val codec: BinaryCodec[Table.Rows] = XmlCodec.schemaBasedBinaryCodec[Table.Rows](Table.schema)

  private val Utf8Bom = Chunk[Byte](0xef.toByte, 0xbb.toByte, 0xbf.toByte)

  val verb = "Decoded"

  def load(path: Path): Task[Stats] =
    for {
      bytes <- ZIO.attemptBlocking(Chunk.fromArray(Files.readAllBytes(path)))
      clean  = if (bytes.take(3) == Utf8Bom) bytes.drop(3) else bytes
      rows  <- ZIO.fromEither(codec.decode(clean)).mapError(e => new RuntimeException(e.toString))
    } yield rows.foldLeft(Stats.empty)((s, c) => s.add(c.trimmed))
}
