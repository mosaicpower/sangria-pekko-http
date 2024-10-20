package sangria.http.pekko

import org.apache.pekko.http.javadsl.server.RequestEntityExpectedRejection
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.MediaTypes._
import org.apache.pekko.http.scaladsl.model.StatusCodes.{
  BadRequest,
  InternalServerError,
  UnprocessableEntity
}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromStringUnmarshaller}
import sangria.ast.Document
import sangria.http.pekko.GraphQLRequestUnmarshaller._
import sangria.http.pekko.Util.explicitlyAccepts
import sangria.parser.{QueryParser, SyntaxError}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait SangriaPekkoHttp[Input] {
  import SangriaPekkoHttp._

  type GQLRequestHandler = PartialFunction[Try[GraphQLRequest[Input]], StandardRoute]
  implicit def errorMarshaller: ToEntityMarshaller[GraphQLErrorResponse]
  implicit def requestUnmarshaller: FromEntityUnmarshaller[GraphQLHttpRequest[Input]]
  implicit def variablesUnmarshaller: FromStringUnmarshaller[Input]

  private val MISSING_QUERY_MSG =
    s"""Could not extract `query` from request.
       |Please confirm you have included a valid GraphQL query either as a QueryString parameter, or in the body of your request.""".stripMargin

  private val HELPFUL_UNPROCESSABLE_ERR = MalformedRequest(s"""
       |Check that you have provided well-formed JSON in the request.
       |`variables` must also be valid JSON if you have provided this
       |parameter to your request.""".stripMargin)

  def malformedRequestHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case r: MalformedQueryParamRejection =>
          val err = formatError(r.cause.getOrElse(MalformedRequest(r.errorMsg)))
          complete(
            UnprocessableEntity,
            GraphQLErrorResponse(
              errors = err :: formatError(HELPFUL_UNPROCESSABLE_ERR) :: Nil
            ))
        case r: MalformedRequestContentRejection =>
          val err = formatError(r.cause)
          complete(
            UnprocessableEntity,
            GraphQLErrorResponse(
              errors = err :: formatError(HELPFUL_UNPROCESSABLE_ERR) :: Nil
            ))
        case _: RequestEntityExpectedRejection =>
          val err = formatError(MalformedRequest(MISSING_QUERY_MSG))
          complete(
            BadRequest,
            GraphQLErrorResponse(
              errors = err :: Nil
            ))
      }
      .result()

  def graphQLExceptionHandler: ExceptionHandler =
    ExceptionHandler { case _ =>
      complete(
        InternalServerError,
        GraphQLErrorResponse(
          GraphQLError(
            "Internal Server Error"
          ) :: Nil
        ))
    }

  val graphQLPlayground: Route = get {
    explicitlyAccepts(`text/html`) {
      getFromResource("assets/playground.html")
    }
  }

  def formatError(error: Throwable): GraphQLError = error match {
    case syntaxError: SyntaxError =>
      GraphQLError(
        syntaxError.getMessage,
        Some(
          Location(
            syntaxError.originalError.position.line,
            syntaxError.originalError.position.column) :: Nil)
      )
    case NonFatal(e) =>
      GraphQLError(e.getMessage)
    case e =>
      throw e
  }

  def prepareQuery(maybeQuery: Option[String]): Try[Document] = maybeQuery match {
    case Some(q) => QueryParser.parse(q)
    case None => Left(MalformedRequest(MISSING_QUERY_MSG)).toTry
  }

  private def extractParams: Directive[(Option[String], Option[String], Option[Input])] =
    parameters(Symbol("query").?, Symbol("operationName").?, Symbol("variables").as[Input].?)

  private def prepareGraphQLPost(inner: GQLRequestHandler)(implicit v: Variables[Input]): Route =
    extractParams { case (queryParam, operationNameParam, variablesParam) =>
      // Content-Type: application/json
      entity(as[GraphQLHttpRequest[Input]]) { body =>
        val maybeOperationName = operationNameParam.orElse(body.operationName)
        val maybeQuery = queryParam.orElse(body.query)

        // Variables may be provided in the QueryString, or possibly in the body as a String:
        // If we were unable to parse the variables from the body as a string,
        // we read them as JSON, and finally if no variables have been located
        // in the QueryString, Body (as a String) or Body (as JSON), we provide
        // an empty JSON object as the final result
        val maybeVariables = variablesParam.orElse(body.variables)

        prepareQuery(maybeQuery) match {
          case Success(document) =>
            val result = GraphQLRequest(
              query = document,
              variables = maybeVariables,
              operationName = maybeOperationName
            )
            inner(Success(result))
          case Failure(error) => inner(Failure(error))
        }
      } ~
        // Content-Type: application/graphql
        entity(as[Document]) { document =>
          val result = GraphQLRequest(
            query = document,
            variables = variablesParam,
            operationName = operationNameParam)
          inner(Success(result))
        }
    }

  private def prepareGraphQLGet(inner: GQLRequestHandler)(implicit v: Variables[Input]): Route =
    extractParams { (maybeQuery, maybeOperationName, maybeVariables) =>
      prepareQuery(maybeQuery) match {
        case Success(document) =>
          val result = GraphQLRequest(
            query = document,
            variables = maybeVariables,
            maybeOperationName
          )
          inner(Success(result))
        case Failure(error) => inner(Failure(error))
      }
    }

  def prepareGraphQLRequest(inner: GQLRequestHandler)(implicit v: Variables[Input]): Route =
    handleExceptions(graphQLExceptionHandler) {
      handleRejections(malformedRequestHandler) {
        get {
          prepareGraphQLGet(inner)
        } ~ post {
          prepareGraphQLPost(inner)
        }
      }
    }

  def graphQLRoute(inner: GQLRequestHandler)(implicit v: Variables[Input]): Route =
    path("graphql") {
      graphQLPlayground ~ prepareGraphQLRequest(inner)
    }
}

object SangriaPekkoHttp {
  final case class MalformedRequest(
      private val message: String = "Your request could not be processed",
      private val cause: Throwable = None.orNull
  ) extends Exception(message, cause)

  case class Location(line: Int, column: Int)
  case class GraphQLError(message: String, locations: Option[List[Location]] = None)
  case class GraphQLErrorResponse(errors: List[GraphQLError])
}
