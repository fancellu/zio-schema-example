package compo

import java.nio.file.{Path, Paths}

import zio._

/** Summary of the COMPO rows, built incrementally so it works for both a `List` and a `ZStream`. */
final case class Stats(
  rows: Long,
  numeric: Long,
  foods: Set[String],
  consts: Set[String],
  byConf: Map[String, Long],
  first: Vector[Compo]
) {
  def add(c: Compo): Stats = Stats(
    rows + 1,
    numeric + (if (c.teneurValue.isDefined) 1 else 0),
    foods + c.alim_code,
    consts + c.const_code,
    byConf.updated(c.code_confiance, byConf.getOrElse(c.code_confiance, 0L) + 1),
    if (first.size < Stats.FirstRows) first :+ c else first
  )
}

object Stats {
  val FirstRows    = 5
  val empty: Stats = Stats(0, 0, Set.empty, Set.empty, Map.empty, Vector.empty)
}

/** Shared entry point: reads the path argument, times [[load]] and prints the report. */
trait CompoApp extends ZIOAppDefault {

  /** Word used in the log lines, e.g. "Decoded" / "Streamed". */
  def verb: String

  /** Reads the file and folds every (trimmed) row into [[Stats]]. */
  def load(path: Path): Task[Stats]

  val DefaultFile = "compo_2025_11_03.xml"

  def run: ZIO[ZIOAppArgs, Any, Unit] =
    for {
      args  <- getArgs
      path   = Paths.get(args.headOption.getOrElse(DefaultFile))
      _     <- Console.printLine(s"Reading $path ...")
      start <- Clock.nanoTime
      stats <- load(path)
      end   <- Clock.nanoTime
      _     <- Console.printLine(f"$verb ${stats.rows}%,d COMPO rows in ${(end - start) / 1000000}%,d ms")
      _     <- report(stats)
    } yield ()

  private def report(s: Stats): Task[Unit] = {
    val rt     = java.lang.Runtime.getRuntime
    val byConf = s.byConf.toList.sortBy(-_._2).map { case (k, v) => s"'$k'=$v" }.mkString(", ")
    for {
      _ <- Console.printLine(f"Distinct foods (alim_code): ${s.foods.size}%,d, constituents (const_code): ${s.consts.size}%,d")
      _ <- Console.printLine(f"Rows with numeric teneur: ${s.numeric}%,d, without: ${s.rows - s.numeric}%,d")
      _ <- Console.printLine(s"Rows by code_confiance: $byConf")
      _ <- Console.printLine("First rows:")
      _ <- ZIO.foreachDiscard(s.first)(r => Console.printLine("  " + r))
      _ <- Console.printLine(
             f"Max heap: ${rt.maxMemory / 1048576}%,d MB, used now: ${(rt.totalMemory - rt.freeMemory) / 1048576}%,d MB"
           )
    } yield ()
  }
}
