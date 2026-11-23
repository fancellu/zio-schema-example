package compo

import java.nio.file.Path

import zio._
import zio.stream.ZSink

/** Same report as [[Main]], but the file is streamed row by row, so memory use stays flat whatever the file size. */
object StreamingMain extends CompoApp {

  val verb = "Streamed"

  def load(path: Path): Task[Stats] =
    XmlRowStream.rows[Compo](path).map(_.trimmed).run(ZSink.foldLeft(Stats.empty)(_ add _))
}
