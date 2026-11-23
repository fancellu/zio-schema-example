package compo

import zio.schema.{DeriveSchema, Schema}

/** Mirrors one <COMPO> element. Numeric fields use French decimals ("59,7") or "-" placeholders, so they are kept as
  * raw strings here and converted by [[Compo#teneurValue]] and friends.
  *
  * Elements such as `<min missing=" " />` carry only an attribute; they decode as an empty string.
  */
final case class Compo(
  alim_code: String,
  const_code: String,
  teneur: String,
  min: String,
  max: String,
  code_confiance: String,
  source_code: String
) {
  /** The XML pads every value with spaces (`<code_confiance> D </code_confiance>`). */
  def trimmed: Compo = copy(
    alim_code = alim_code.trim,
    const_code = const_code.trim,
    teneur = teneur.trim,
    min = min.trim,
    max = max.trim,
    code_confiance = code_confiance.trim,
    source_code = source_code.trim
  )

  def teneurValue: Option[Double] = Compo.parseNumber(teneur)
  def minValue: Option[Double]    = Compo.parseNumber(min)
  def maxValue: Option[Double]    = Compo.parseNumber(max)
}

object Compo {
  implicit val schema: Schema[Compo] = DeriveSchema.gen[Compo]

  /** Parses "59,7" as 59.7. Returns None for anything that is not a plain number, e.g. "-", "< 0,5", "traces". */
  def parseNumber(raw: String): Option[Double] =
    raw.trim.replace(',', '.').toDoubleOption
}

/** The root `<TABLE>` element is decoded as a sequence schema: the codec treats the children of the root element as the
  * items of the list, whatever their element name (`<COMPO>` here).
  */
object Table {
  type Rows = List[Compo]
  implicit val schema: Schema[Rows] = Schema.list[Compo]
}
