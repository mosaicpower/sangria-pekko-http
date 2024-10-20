package sangria.http.pekko.circe

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe._
import io.circe.generic.semiauto._
import org.apache.pekko.http.scaladsl.marshalling._
import org.apache.pekko.http.scaladsl.model.StatusCodes._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling._
import sangria.execution.Executor
import sangria.http.pekko.{GraphQLHttpRequest, SangriaPekkoHttp, Variables}
import sangria.parser.SyntaxError
import sangria.schema.Schema

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait CirceHttpSupport extends SangriaPekkoHttp[Json] with FailFastCirceSupport {
  import SangriaPekkoHttp._

  implicit val locationEncoder: Encoder[Location] = deriveEncoder[Location]
  implicit val graphQLErrorEncoder: Encoder[GraphQLError] = deriveEncoder[GraphQLError]
  implicit val graphQLErrorResponseEncoder: Encoder[GraphQLErrorResponse] =
    deriveEncoder[GraphQLErrorResponse]

  implicit val graphQLRequestDecoder: Decoder[GraphQLHttpRequest[Json]] =
    deriveDecoder[GraphQLHttpRequest[Json]]

  implicit object JsonVariables extends Variables[Json] {
    override def empty: Json = Json.obj()
  }

  override implicit def errorMarshaller: ToEntityMarshaller[GraphQLErrorResponse] = marshaller
  override implicit def requestUnmarshaller: FromEntityUnmarshaller[GraphQLHttpRequest[Json]] =
    unmarshaller

  // TODO: This seems... awkward?
  import PredefinedFromStringUnmarshallers.{
    _fromStringUnmarshallerFromByteStringUnmarshaller ⇒ stringFromByteStringUm
  }
  override implicit def variablesUnmarshaller: FromStringUnmarshaller[Json] =
    stringFromByteStringUm(fromByteStringUnmarshaller[Json])

  // 🎉 Tada!
  def graphql(maybePath: String = "graphql")(schema: Schema[Any, Any])(implicit
      ec: ExecutionContext): Route = {
    import sangria.marshalling.circe._

    path(maybePath) {
      prepareGraphQLRequest {
        case Success(req) =>
          val resp = Executor
            .execute(
              schema = schema,
              queryAst = req.query,
              variables = req.variables,
              operationName = req.operationName
              // TODO: Accept Middleware, Context, and all other execute options?
            )
            .map(OK -> _)
          complete(resp)
        case Failure(e) =>
          e match {
            case err: SyntaxError =>
              val errResp = GraphQLErrorResponse(
                formatError(err) :: Nil
              )
              complete(UnprocessableEntity, errResp)
            case err: MalformedRequest =>
              val errResp = GraphQLErrorResponse(
                formatError(err) :: Nil
              )
              complete(UnprocessableEntity, errResp)
          }

      }
    }
  }
}
