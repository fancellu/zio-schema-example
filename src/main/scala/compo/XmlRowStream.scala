package compo

import java.io.{BufferedInputStream, FileInputStream, InputStream}
import java.nio.file.Path
import javax.xml.stream.{XMLInputFactory, XMLStreamConstants, XMLStreamReader}

import zio._
import zio.schema.Schema
import zio.schema.codec.xml.{WriterConfig, Xml, XmlCodec, XmlName, XmlWriter}
import zio.stream.ZStream

/** Streams the direct children of the root element (the `<COMPO>` rows) one at a time.
  *
  * A StAX pull parser walks the file, so only the current row is held in memory. Each row is rebuilt as a small
  * zio-schema-xml `Xml.Element`, written back to a string and decoded by the schema-derived `XmlCodec`, so the
  * mapping from XML to case class still comes from `Schema[A]`.
  */
object XmlRowStream {

  def rows[A](path: Path)(implicit schema: Schema[A]): ZStream[Any, Throwable, A] = {
    val codec = XmlCodec.schemaBasedBinaryCodec[A]
    elements(path).mapZIO { element =>
      val fragment = XmlWriter.write(element, WriterConfig.default)
      ZIO
        .fromEither(codec.decode(Chunk.fromArray(fragment.getBytes("UTF-8"))))
        .mapError(e => new RuntimeException(s"Cannot decode row: $e\n$fragment"))
    }
  }

  /** The raw child elements of the root, as zio-schema-xml trees. */
  def elements(path: Path): ZStream[Any, Throwable, Xml.Element] =
    ZStream.fromIteratorScoped(
      for {
        in     <- ZIO.fromAutoCloseable(ZIO.attemptBlocking(new BufferedInputStream(new FileInputStream(path.toFile))))
        reader <- ZIO.acquireRelease(ZIO.attemptBlocking(newReader(in)))(r => ZIO.succeed(r.close()))
      } yield new ChildIterator(reader)
    )

  private def newReader(in: InputStream): XMLStreamReader = {
    val factory = XMLInputFactory.newInstance()
    // No DTDs or external entities: safe for untrusted input.
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    // Deliver each text node as one event, not split at buffer boundaries.
    factory.setProperty(XMLInputFactory.IS_COALESCING, true)
    factory.createXMLStreamReader(in) // honours the encoding declaration and skips the BOM
  }

  /** Yields every element at depth 2 (child of the root). */
  private final class ChildIterator(reader: XMLStreamReader) extends Iterator[Xml.Element] {
    private var depth                        = 0
    private var pending: Option[Xml.Element] = None
    private var finished                     = false

    override def hasNext: Boolean = {
      if (pending.isEmpty && !finished) advance()
      pending.isDefined
    }

    override def next(): Xml.Element = {
      if (!hasNext) throw new NoSuchElementException
      val e = pending.get
      pending = None
      e
    }

    private def advance(): Unit =
      while (pending.isEmpty && !finished) {
        if (!reader.hasNext) finished = true
        else
          reader.next() match {
            case XMLStreamConstants.START_ELEMENT =>
              depth += 1
              if (depth == 2) {
                pending = Some(readElement())
                depth -= 1 // readElement consumed the matching END_ELEMENT
              }
            case XMLStreamConstants.END_ELEMENT => depth -= 1
            case _                              => ()
          }
      }

    /** Called with the reader on a START_ELEMENT; returns after its END_ELEMENT. */
    private def readElement(): Xml.Element = {
      val name  = XmlName(reader.getLocalName)
      val attrs = Chunk.fromIterable((0 until reader.getAttributeCount).map { i =>
        XmlName(reader.getAttributeLocalName(i)) -> reader.getAttributeValue(i)
      })
      val children = ChunkBuilder.make[Xml]()
      var done     = false
      while (!done) {
        reader.next() match {
          case XMLStreamConstants.START_ELEMENT                          => children += readElement()
          case XMLStreamConstants.CHARACTERS | XMLStreamConstants.CDATA =>
            val text = reader.getText
            if (text.trim.nonEmpty) children += Xml.Text(text)
          case XMLStreamConstants.END_ELEMENT                            => done = true
          case _                                                         => ()
        }
      }
      Xml.Element(name, attrs, children.result())
    }
  }
}
