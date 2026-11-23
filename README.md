# zio-schema-example

A small Scala 2.13 project that parses `compo_2025_11_03.xml` (a ~66MB food composition table, 257,816 `<COMPO>` rows) with [zio-schema-xml](https://github.com/zio/zio-schema/tree/main/zio-schema-xml).

## Requirements

- JDK 17+ (developed on JDK 25)
- sbt (version pinned in `project/build.properties`)
- Heap: about 6GB for the in-memory `Main` (`build.sbt` forks the run with `-Xmx6g`). `StreamingMain` holds one row at a time and runs in 64MB (see [Streaming version](#streaming-version)).
- [A suitable XML file](https://entrepot.recherche.data.gouv.fr/file.xhtml?persistentId=doi:10.57745/O73GDX&version=1.0)

## Run

```sh
sbt run                       # parses compo_2025_11_03.xml in the project root
sbt "run path/to/other.xml"   # parses another file
```

It prints the row count, decode time, distinct foods and constituents, how many rows have a numeric `teneur`, a count per `code_confiance`, the first five rows, and the heap size and usage.

## Layout

| File | Purpose |
| --- | --- |
| `build.sbt` | Scala 2.13.18, ZIO 2.1.27, zio-schema / -derivation / -xml 1.9.2 |
| `src/main/scala/compo/Model.scala` | `Compo` case class, derived `Schema`, and the root `Schema[List[Compo]]` |
| `src/main/scala/compo/CompoApp.scala` | Shared `Stats` fold and `CompoApp` trait (args, timing, report) |
| `src/main/scala/compo/Main.scala` | In-memory loader: decodes the whole file with `XmlCodec` |
| `src/main/scala/compo/StreamingMain.scala` | Streaming loader built on `XmlRowStream` |
| `src/main/scala/compo/XmlRowStream.scala` | StAX pull parser plus per-row `XmlCodec` decode, as a `ZStream` |

## Input format

```xml
<TABLE>
   <COMPO>
      <alim_code> 1000 </alim_code>
      <const_code> 400 </const_code>
      <teneur> 59,7 </teneur>
      <min>         58,7 </min>
      <max>         60,4 </max>
      <code_confiance> D </code_confiance>
      <source_code> 1373 </source_code>
   </COMPO>
   ...
</TABLE>
```

## Design notes

- **Root as a list.** The codec decodes a sequence from the children of the root element, whatever their names. So `<TABLE>` is decoded directly as `List[Compo]` (`Table.schema`). Wrapping it in a record with a `COMPO: List[Compo]` field fails with "Missing required field".
- **String fields.** The data uses French decimals (`59,7`), `-` placeholders and attribute-only elements such as `<min missing=" " />`. A numeric field type would fail to decode, so every field is a `String`. `Compo#teneurValue`, `minValue` and `maxValue` convert to `Option[Double]`, and a value that is not a plain number gives `None`.
- **Padding.** Values are padded with spaces (`" D "`) and the codec keeps them. `Compo#trimmed` strips them after decoding.
- **BOM.** The file starts with a UTF-8 BOM, which `Main` strips before decoding.
- **Empty values.** Elements with only a `missing` attribute decode as an empty string. That includes 83,246 rows with no `code_confiance`.

## Streaming version

`Main` loads the whole file into memory: bytes, then a `String`, then a full `Xml` tree, then the `List`. Hence the 6GB heap. The codec's own `streamDecoder` doesn't help, because it decodes each byte chunk as a complete document.

`StreamingMain` produces the same report with constant memory:

```sh
sbt "runMain compo.StreamingMain"
sbt streamSmall                      # same with a 64MB heap, proves memory stays flat
```

`XmlRowStream.rows[A](path): ZStream[Any, Throwable, A]` uses the JDK's StAX pull parser to walk the file. For each child of the root (`<COMPO>`) it builds a small zio-schema-xml `Xml.Element`, writes it with `XmlWriter` and decodes it with `XmlCodec`. The mapping to the case class still comes from `Schema[A]`. Only one row is in memory at a time. The full file takes about 2.8s, against about 1.3s for the in-memory decode.

## Using the codec elsewhere

```scala
import zio.schema.codec.xml.XmlCodec

val codec = XmlCodec.schemaBasedBinaryCodec[Table.Rows](Table.schema)
codec.decode(bytes)   // Either[DecodeError, List[Compo]]
```

Reader limits (`maxDepth`, `maxTextLength`, `preserveWhitespace`) can be changed through `XmlCodec.Configuration(readerConfig = ReaderConfig(...))`.
