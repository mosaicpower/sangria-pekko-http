package sangria.http.pekko

case class GraphQLHttpRequest[T](
    query: Option[String],
    variables: Option[T],
    operationName: Option[String])
