package sangria.http.pekko

import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.util.ByteString
import sangria.ast.Document
import sangria.parser.QueryParser
import sangria.renderer.{QueryRenderer, QueryRendererConfig}

import java.nio.charset.StandardCharsets

object GraphQLRequestUnmarshaller {
  val `application/graphql` =
    MediaType.applicationWithFixedCharset("graphql", HttpCharsets.`UTF-8`, "graphql")

  def unmarshallerContentTypes: Seq[ContentTypeRange] =
    mediaTypes.map(ContentTypeRange.apply)

  def mediaTypes: Seq[MediaType.WithFixedCharset] =
    List(`application/graphql`)

  implicit final def documentMarshaller(implicit
      config: QueryRendererConfig = QueryRenderer.Compact): ToEntityMarshaller[Document] =
    Marshaller.oneOf(mediaTypes: _*) { mediaType =>
      Marshaller.withFixedContentType(ContentType(mediaType)) { json =>
        HttpEntity(mediaType, QueryRenderer.render(json, config))
      }
    }

  implicit final val documentUnmarshaller: FromEntityUnmarshaller[Document] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(unmarshallerContentTypes: _*)
      .map {
        case ByteString.empty => throw Unmarshaller.NoContentException
        case data =>
          QueryParser.parse(data.decodeString(StandardCharsets.UTF_8)).fold(e => throw e, identity)
      }
}
